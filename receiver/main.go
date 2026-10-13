// ShakeLight Receiver: shows a phone running ShakeLight on this computer's screen
// (and on any projector connected to it), directly over the local Wi-Fi network.
package main

import (
	"bufio"
	"embed"
	"encoding/json"
	"flag"
	"fmt"
	"io"
	"log"
	"net"
	"net/http"
	"os"
	"sort"
	"strings"
	"time"
)

const version = "2.0.0"

//go:embed web
var webFiles embed.FS

func main() {
	noBrowser := flag.Bool("no-browser", false, "don't open the viewer window automatically")
	flag.Parse()
	log.SetFlags(log.Ltime)

	name := computerName()
	viewerURL := fmt.Sprintf("http://127.0.0.1:%d/", webPort)

	phoneListener, err := net.Listen("tcp4", fmt.Sprintf(":%d", streamPort))
	if err != nil {
		// Most likely the receiver is already running: just show its window again.
		fmt.Println("ShakeLight Receiver is already running (or port", streamPort, "is busy).")
		if !*noBrowser {
			openViewer(viewerURL)
		}
		time.Sleep(3 * time.Second)
		return
	}

	h := newHub()
	go acceptPhones(phoneListener, h)
	go announce(name)

	webListener, err := net.Listen("tcp4", fmt.Sprintf("127.0.0.1:%d", webPort))
	if err != nil {
		log.Fatalf("Can't open the viewer port %d: %v", webPort, err)
	}
	dm := newDesktopManager(h)
	go func() {
		log.Fatal(http.Serve(webListener, routes(h, dm, name)))
	}()

	printBanner(name)
	if !*noBrowser {
		openViewer(viewerURL)
	}
	select {}
}

func printBanner(name string) {
	fmt.Println("==============================================")
	fmt.Println("  ShakeLight Receiver", version)
	fmt.Println("==============================================")
	fmt.Println("Computer name:", name)
	fmt.Println("IP address(es):", strings.Join(localIPv4s(), ", "))
	fmt.Println()
	fmt.Println("On your phone: ShakeLight > Project to computer > tap", name)
	fmt.Println("Phone and computer must be on the same Wi-Fi network.")
	fmt.Println("If Windows asks about the firewall, click \"Allow\" (private networks).")
	fmt.Println()
	fmt.Println("Keep this window open while projecting. Close it to stop.")
	fmt.Println()
}

func acceptPhones(ln net.Listener, h *hub) {
	for {
		conn, err := ln.Accept()
		if err != nil {
			log.Println("accept:", err)
			time.Sleep(time.Second)
			continue
		}
		go handlePhone(conn, h)
	}
}

func handlePhone(conn net.Conn, h *hub) {
	defer conn.Close()
	if tcp, ok := conn.(*net.TCPConn); ok {
		tcp.SetNoDelay(true)
	}

	r := bufio.NewReaderSize(conn, 1<<20)
	conn.SetReadDeadline(time.Now().Add(5 * time.Second))
	head := make([]byte, len(magic))
	if _, err := io.ReadFull(r, head); err != nil {
		return
	}
	if string(head) == oldMagic {
		log.Println("A phone with an old ShakeLight version tried to connect: please update the app on the phone.")
		return
	}
	if string(head) != magic {
		return // not a ShakeLight phone
	}
	conn.SetReadDeadline(time.Time{})

	if h.desktopSession() != nil {
		log.Println("A phone tried to mirror while Computer mode is running; ignored.")
		return
	}
	h.setPhone(conn)
	log.Println("Phone connected from", conn.RemoteAddr())
	welcome, _ := json.Marshal(map[string]string{"version": version})
	conn.Write(encodeControl(ctrlWelcome, welcome))

	header := make([]byte, headerSize)
	for {
		if _, err := io.ReadFull(r, header); err != nil {
			break
		}
		n := uint32(header[1])<<24 | uint32(header[2])<<16 | uint32(header[3])<<8 | uint32(header[4])
		if n > maxPacketLen {
			log.Println("Packet too large, disconnecting phone")
			break
		}
		pkt := make([]byte, headerSize+int(n))
		copy(pkt, header)
		if _, err := io.ReadFull(r, pkt[headerSize:]); err != nil {
			break
		}
		if header[0] == pktHello {
			var hello struct{ Name string }
			if json.Unmarshal(pkt[headerSize:], &hello) == nil && hello.Name != "" {
				log.Println("Projecting:", hello.Name)
			}
		}
		h.publish(pkt)
	}

	if h.clearPhone(conn) {
		h.publish(encodePacket(pktBye, 0, nil))
		log.Println("Phone disconnected")
	}
}

func routes(h *hub, dm *desktopManager, name string) http.Handler {
	mux := http.NewServeMux()

	page := func(file, contentType string) http.HandlerFunc {
		return func(w http.ResponseWriter, r *http.Request) {
			body, err := webFiles.ReadFile("web/" + file)
			if err != nil {
				http.NotFound(w, r)
				return
			}
			w.Header().Set("Content-Type", contentType)
			w.Header().Set("Cache-Control", "no-store")
			w.Write(body)
		}
	}
	mux.HandleFunc("/", func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/" {
			http.NotFound(w, r)
			return
		}
		page("index.html", "text/html; charset=utf-8")(w, r)
	})
	mux.HandleFunc("/desktop", page("desktop.html", "text/html; charset=utf-8"))
	mux.HandleFunc("/stream.js", page("stream.js", "text/javascript; charset=utf-8"))
	dm.routes(mux)

	mux.HandleFunc("/info", func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(map[string]any{
			"name":      name,
			"addresses": localIPv4s(),
			"port":      streamPort,
			"version":   version,
		})
	})

	// The viewer page sends mouse/keyboard input here, as a JSON array of commands, in order.
	mux.HandleFunc("/control", func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodPost {
			http.Error(w, "POST only", http.StatusMethodNotAllowed)
			return
		}
		var cmds []json.RawMessage
		if err := json.NewDecoder(io.LimitReader(r.Body, maxCommandLen)).Decode(&cmds); err != nil {
			http.Error(w, "bad request", http.StatusBadRequest)
			return
		}
		if d := h.desktopSession(); d != nil {
			for _, cmd := range cmds {
				if err := d.handle(cmd); err != nil {
					http.Error(w, err.Error(), http.StatusConflict)
					return
				}
			}
			w.WriteHeader(http.StatusNoContent)
			return
		}
		for _, cmd := range cmds {
			if !h.sendToPhone(encodeControl(ctrlCommand, cmd)) {
				http.Error(w, "no phone connected", http.StatusConflict)
				return
			}
		}
		w.WriteHeader(http.StatusNoContent)
	})

	mux.HandleFunc("/keyframe", func(w http.ResponseWriter, r *http.Request) {
		h.requestKeyframe()
		w.WriteHeader(http.StatusNoContent)
	})

	mux.HandleFunc("/stream", func(w http.ResponseWriter, r *http.Request) {
		flusher, ok := w.(http.Flusher)
		if !ok {
			http.Error(w, "streaming unsupported", http.StatusInternalServerError)
			return
		}
		w.Header().Set("Content-Type", "application/octet-stream")
		w.Header().Set("Cache-Control", "no-store")
		w.WriteHeader(http.StatusOK)
		flusher.Flush()

		sub := h.subscribe()
		defer h.unsubscribe(sub)
		for {
			select {
			case <-r.Context().Done():
				return
			case pkt := <-sub.ch:
				if _, err := w.Write(pkt); err != nil {
					return
				}
				// Send everything already queued in one go, then flush.
				for drained := false; !drained; {
					select {
					case more := <-sub.ch:
						if _, err := w.Write(more); err != nil {
							return
						}
					default:
						drained = true
					}
				}
				flusher.Flush()
			}
		}
	})
	return mux
}

// announce broadcasts "a receiver is here" every second so phones find it without typing an IP.
func announce(name string) {
	msg, _ := json.Marshal(map[string]any{"app": "shakelight-receiver", "name": name, "port": streamPort, "version": version})
	conn, err := net.ListenUDP("udp4", nil)
	if err != nil {
		log.Println("Automatic discovery is off:", err)
		return
	}
	defer conn.Close()
	for {
		targets := append(broadcastAddrs(), net.IPv4bcast)
		for _, ip := range targets {
			conn.WriteToUDP(msg, &net.UDPAddr{IP: ip, Port: discoveryPort})
		}
		time.Sleep(time.Second)
	}
}

func computerName() string {
	name, err := os.Hostname()
	if err != nil || name == "" {
		return "Computer"
	}
	return strings.TrimSuffix(name, ".local")
}

// usableIPv4Nets lists the IPv4 networks of active, non-loopback interfaces.
func usableIPv4Nets() []*net.IPNet {
	var out []*net.IPNet
	ifaces, _ := net.Interfaces()
	for _, iface := range ifaces {
		if iface.Flags&net.FlagUp == 0 || iface.Flags&net.FlagLoopback != 0 {
			continue
		}
		addrs, _ := iface.Addrs()
		for _, a := range addrs {
			if ipNet, ok := a.(*net.IPNet); ok && ipNet.IP.To4() != nil && !ipNet.IP.IsLinkLocalUnicast() {
				out = append(out, ipNet)
			}
		}
	}
	return out
}

func localIPv4s() []string {
	var ips []string
	for _, n := range usableIPv4Nets() {
		ips = append(ips, n.IP.To4().String())
	}
	sort.Strings(ips)
	if len(ips) == 0 {
		return []string{"(no network)"}
	}
	return ips
}

func broadcastAddrs() []net.IP {
	var out []net.IP
	for _, n := range usableIPv4Nets() {
		ip, mask := n.IP.To4(), n.Mask
		if len(mask) == net.IPv6len {
			mask = mask[12:]
		}
		if len(mask) != net.IPv4len {
			continue
		}
		b := make(net.IP, net.IPv4len)
		for i := range b {
			b[i] = ip[i] | ^mask[i]
		}
		out = append(out, b)
	}
	return out
}

func logf(format string, args ...any) { log.Printf(format, args...) }
