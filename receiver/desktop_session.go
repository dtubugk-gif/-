package main

import (
	"bufio"
	"crypto/rand"
	_ "embed"
	"encoding/binary"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"math"
	"net"
	"os"
	"os/exec"
	"path/filepath"
	"regexp"
	"strconv"
	"strings"
	"sync"
	"sync/atomic"
	"time"
)

// The phone-side half of Computer mode: scrcpy's server (Apache-2.0, by Genymobile), built from the
// official 5.0.1 sources. It creates the separate desktop screen on the phone and streams it.
//
//go:embed server/scrcpy-server.jar
var scrcpyServer []byte

const (
	scrcpyVersion = "5.0.1"
	serverPath    = "/data/local/tmp/shakelight-desktop.jar"

	codecH264   = 0x68323634
	codecRaw    = 0x00726177
	flagConfig  = uint64(1) << 62
	flagKey     = uint64(1) << 61
	ptsMask     = flagKey - 1
	pointerMous = ^uint64(0) // -1: a real mouse when hovering or using non-primary buttons
)

// Settings that make the phone run its desktop (Samsung DeX) on the new screen; restored afterwards.
var desktopSettings = []struct{ namespace, key, value string }{
	{"system", "dex_on_external_display", "1"},
}

type desktopSession struct {
	req   startRequest
	hub   *hub
	onEnd func(*desktopSession, error)

	mu      sync.Mutex
	st      string
	errMsg  string
	device  string
	cmd     *exec.Cmd
	conns   []net.Conn
	control net.Conn
	ctrlMu  sync.Mutex
	port    string
	saved   map[string]string

	width, height atomic.Int32 // current video size; input positions must use exactly this
	displayID     atomic.Int32 // the desktop screen on the phone (from the server log)
	lastReset     atomic.Int64
	awakeByApp    bool
	appsMu        sync.Mutex
	appsJSON      []byte
	stopOnce      sync.Once
	done          chan struct{}
}

func newDesktopSession(req startRequest, h *hub, onEnd func(*desktopSession, error)) *desktopSession {
	return &desktopSession{req: req, hub: h, onEnd: onEnd, st: "starting", done: make(chan struct{})}
}

func (s *desktopSession) state() sessionState {
	s.mu.Lock()
	defer s.mu.Unlock()
	return sessionState{State: s.st, Error: s.errMsg, Device: s.device}
}

func (s *desktopSession) setState(st string) {
	s.mu.Lock()
	s.st = st
	s.mu.Unlock()
}

func (s *desktopSession) stop() {
	s.stopOnce.Do(func() { close(s.done) })
	s.mu.Lock()
	conns := s.conns
	cmd := s.cmd
	s.mu.Unlock()
	for _, c := range conns {
		c.Close()
	}
	if cmd != nil && cmd.Process != nil {
		cmd.Process.Kill()
	}
}

func (s *desktopSession) stopped() bool {
	select {
	case <-s.done:
		return true
	default:
		return false
	}
}

func (s *desktopSession) run() {
	err := s.serve()
	s.stop()
	s.cleanup()
	s.hub.setDesktop(nil)
	s.hub.publish(encodePacket(pktBye, 0, nil))
	if s.stopped() && err != nil && isClosedErr(err) {
		err = nil // the user disconnected
	}
	if err != nil {
		logf("Computer mode stopped: %v", err)
	} else {
		logf("Computer mode stopped.")
	}
	s.onEnd(s, err)
}

func (s *desktopSession) serve() error {
	serial := s.req.Serial
	if serial == "" {
		return errors.New("no phone selected")
	}
	s.hub.setDesktop(s) // takes over the viewer (a mirroring phone is disconnected)

	// 1. Desktop settings (remember the user's values).
	s.saved = map[string]string{}
	for _, st := range desktopSettings {
		if old, err := adb.shell(serial, 10*time.Second, "settings get "+st.namespace+" "+st.key); err == nil {
			s.saved[st.namespace+" "+st.key] = old
		}
		adb.shell(serial, 10*time.Second, "settings put "+st.namespace+" "+st.key+" "+st.value)
	}

	// 2. Push the server (it deletes itself after starting, so push every time).
	tmp := filepath.Join(os.TempDir(), "shakelight-desktop.jar")
	if err := os.WriteFile(tmp, scrcpyServer, 0o644); err != nil {
		return err
	}
	if out, err := adb.run(60*time.Second, "-s", serial, "push", tmp, serverPath); err != nil {
		return fmt.Errorf("could not copy to the phone: %s", firstLine(out))
	}

	// 3. Tunnel and start.
	var b [4]byte
	rand.Read(b[:])
	scid := binary.BigEndian.Uint32(b[:]) & 0x7fffffff
	socket := fmt.Sprintf("localabstract:scrcpy_%08x", scid)
	out, err := adb.run(10*time.Second, "-s", serial, "forward", "tcp:0", socket)
	port := strings.TrimSpace(out)
	if _, convErr := strconv.Atoi(port); err != nil || convErr != nil {
		return fmt.Errorf("adb forward failed: %s", firstLine(out))
	}
	s.port = port

	args := []string{
		"-s", serial, "shell",
		"CLASSPATH=" + serverPath, "app_process", "/", "com.genymobile.scrcpy.Server", scrcpyVersion,
		fmt.Sprintf("scid=%08x", scid), "log_level=info", "tunnel_forward=true",
		"video=true", "audio=" + strconv.FormatBool(s.req.Audio), "control=true",
		"video_codec=h264", "video_bit_rate=" + strconv.Itoa(bitrateFor(s.req.Width, s.req.Height)), "max_fps=60",
		"audio_codec=raw", "audio_source=output",
		fmt.Sprintf("new_display=%dx%d/%d", s.req.Width, s.req.Height, s.req.DPI),
		"vd_system_decorations=true", "vd_destroy_content=false", "display_ime_policy=local",
		"clipboard_autosync=true", "stay_awake=true", "power_off_on_close=false", "cleanup=true",
	}
	cmd, stdout, err := adb.start(args...)
	if err != nil {
		return err
	}
	s.mu.Lock()
	s.cmd = cmd
	s.mu.Unlock()
	serverLog := make(chan string, 64)
	go func() {
		sc := bufio.NewScanner(stdout)
		for sc.Scan() {
			line := sc.Text()
			logf("[phone] %s", line)
			if m := displayIDRe.FindStringSubmatch(line); m != nil {
				if id, err := strconv.Atoi(m[1]); err == nil {
					s.displayID.Store(int32(id))
				}
			}
			select {
			case serverLog <- line:
			default:
			}
		}
		close(serverLog)
	}()

	// Keep the phone awake: a sleeping phone stops drawing the desktop screen too.
	s.keepAwake(serial)

	// 4. Connect video, audio, control — in this order.
	video, err := s.connectFirst(serverLog)
	if err != nil {
		return err
	}
	var audio net.Conn
	if s.req.Audio {
		if audio, err = s.dial(); err != nil {
			return err
		}
	}
	control, err := s.dial()
	if err != nil {
		return err
	}
	if tcp, ok := control.(*net.TCPConn); ok {
		tcp.SetNoDelay(true)
	}
	s.mu.Lock()
	s.control = control
	s.mu.Unlock()

	vr := bufio.NewReaderSize(video, 1<<20)
	name := make([]byte, 64)
	if _, err := io.ReadFull(vr, name); err != nil {
		return fmt.Errorf("the phone did not answer: %w", err)
	}
	device := strings.TrimRight(string(name), "\x00")
	s.mu.Lock()
	s.device = device
	s.mu.Unlock()
	hello, _ := json.Marshal(map[string]string{"name": device, "mode": "desktop"})
	s.hub.publish(encodePacket(pktHello, 0, hello))

	codec := make([]byte, 4)
	if _, err := io.ReadFull(vr, codec); err != nil {
		return err
	}
	if id := binary.BigEndian.Uint32(codec); id != codecH264 {
		return fmt.Errorf("the phone could not start its video encoder (code %#x)", id)
	}

	if audio != nil {
		go s.readAudio(audio)
	}
	go s.readControl(control)
	return s.readVideo(vr)
}

var displayIDRe = regexp.MustCompile(`New display: .*\(id=(\d+)\)`)

const shakelightPkg = "com.dtubugk.shakelight"

// keepAwake asks the ShakeLight app to hold the phone awake (it shows a notification meanwhile).
// Without the app (or an old version), the receiver wakes the phone whenever it starts to doze.
func (s *desktopSession) keepAwake(serial string) {
	out, err := adb.shell(serial, 10*time.Second, "am start-foreground-service -n "+shakelightPkg+"/.cast.DesktopAwakeService")
	if err == nil && !strings.Contains(out, "Error") && !strings.Contains(out, "not found") {
		s.awakeByApp = true
		return
	}
	logf("ShakeLight app not available to keep the phone awake (%s); waking it as needed.", firstLine(out))
	adb.shell(serial, 5*time.Second, "input keyevent KEYCODE_WAKEUP")
	go func() {
		t := time.NewTicker(4 * time.Second)
		defer t.Stop()
		for {
			select {
			case <-s.done:
				return
			case <-t.C:
				state, _ := adb.shell(serial, 5*time.Second, "dumpsys power | grep -m1 mWakefulness=")
				if !strings.Contains(state, "Awake") {
					adb.shell(serial, 5*time.Second, "input keyevent KEYCODE_WAKEUP")
				}
			}
		}
	}()
}

// apps returns the phone's apps for the Start menu (names and icons come from the ShakeLight app;
// without it, just the package names).
func (s *desktopSession) apps() ([]byte, error) {
	s.appsMu.Lock()
	defer s.appsMu.Unlock()
	if s.appsJSON != nil {
		return s.appsJSON, nil
	}
	out, err := adb.shell(s.req.Serial, 40*time.Second, "content read --uri content://"+shakelightPkg+".desktop/apps")
	if err == nil && strings.HasPrefix(out, "[") && json.Valid([]byte(out)) {
		s.appsJSON = []byte(out)
		return s.appsJSON, nil
	}
	list, err := adb.shell(s.req.Serial, 20*time.Second,
		"cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.LAUNCHER")
	if err != nil {
		return nil, fmt.Errorf("could not list the phone's apps: %s", firstLine(list))
	}
	var apps []map[string]string
	seen := map[string]bool{}
	for _, line := range strings.Split(list, "\n") {
		line = strings.TrimSpace(line)
		pkg, _, ok := strings.Cut(line, "/")
		if !ok || seen[pkg] || strings.ContainsAny(line, " =") {
			continue
		}
		seen[pkg] = true
		name := pkg[strings.LastIndex(pkg, ".")+1:]
		apps = append(apps, map[string]string{"p": pkg, "n": strings.ToUpper(name[:1]) + name[1:], "c": line, "i": ""})
	}
	s.appsJSON, _ = json.Marshal(apps)
	return s.appsJSON, nil
}

var componentRe = regexp.MustCompile(`^[A-Za-z0-9_.]+/[A-Za-z0-9_.$]+$`)

// launch opens an app in a window on the desktop screen.
func (s *desktopSession) launch(component string) error {
	if !componentRe.MatchString(component) {
		return errors.New("bad app name")
	}
	id := s.displayID.Load()
	if id <= 0 {
		return errors.New("the desktop is not ready yet")
	}
	go func() {
		out, _ := adb.shell(s.req.Serial, 15*time.Second,
			fmt.Sprintf("am start --display %d --windowingMode 5 -n %s", id, component))
		if strings.Contains(out, "brought to the front") {
			// Already open (on the phone or the desktop): bring it here as a window.
			pkg, _, _ := strings.Cut(component, "/")
			task := runningTask(s.req.Serial, pkg)
			if task != "" {
				adb.shell(s.req.Serial, 10*time.Second, fmt.Sprintf("am display move-stack %s %d 2>/dev/null || cmd activity task move %s %d 2>/dev/null", task, id, task, id))
			}
		}
	}()
	return nil
}

func runningTask(serial, pkg string) string {
	out, _ := adb.shell(serial, 10*time.Second, "am stack list 2>/dev/null")
	for _, line := range strings.Split(out, "\n") {
		if strings.Contains(line, pkg+"/") {
			if i := strings.Index(line, "taskId="); i >= 0 {
				id := line[i+7:]
				if j := strings.IndexAny(id, ": "); j >= 0 {
					id = id[:j]
				}
				return id
			}
		}
	}
	return ""
}

func bitrateFor(w, h int) int {
	// ~16 Mbit/s at 1080p60 keeps text crisp while scrolling; scale with the pixel count.
	b := int(float64(w*h) / (1920 * 1080) * 16_000_000)
	return min(max(b, 6_000_000), 30_000_000)
}

func (s *desktopSession) dial() (net.Conn, error) {
	c, err := net.DialTimeout("tcp", "127.0.0.1:"+s.port, 3*time.Second)
	if err != nil {
		return nil, err
	}
	s.mu.Lock()
	s.conns = append(s.conns, c)
	s.mu.Unlock()
	return c, nil
}

// connectFirst retries until the server is listening: adb accepts the TCP connection right away,
// but only a live server sends the first byte.
func (s *desktopSession) connectFirst(serverLog <-chan string) (net.Conn, error) {
	var lastLines []string
	for i := 0; i < 120; i++ {
		if s.stopped() {
			return nil, errors.New("stopped")
		}
		for drained := false; !drained; {
			select {
			case line, ok := <-serverLog:
				if !ok {
					return nil, fmt.Errorf("the phone stopped the desktop: %s", strings.Join(lastLines, " "))
				}
				lastLines = append(lastLines, line)
				if len(lastLines) > 3 {
					lastLines = lastLines[1:]
				}
			default:
				drained = true
			}
		}
		c, err := net.DialTimeout("tcp", "127.0.0.1:"+s.port, time.Second)
		if err == nil {
			c.SetReadDeadline(time.Now().Add(1500 * time.Millisecond))
			one := make([]byte, 1)
			if _, err = io.ReadFull(c, one); err == nil {
				c.SetReadDeadline(time.Time{})
				s.mu.Lock()
				s.conns = append(s.conns, c)
				s.mu.Unlock()
				return c, nil
			}
			c.Close()
		}
		time.Sleep(100 * time.Millisecond)
	}
	return nil, errors.New("the phone did not start the desktop in time (is Wireless debugging still on?)")
}

func (s *desktopSession) readVideo(r *bufio.Reader) error {
	header := make([]byte, 12)
	var config []byte
	running := false
	for {
		if _, err := io.ReadFull(r, header); err != nil {
			return err
		}
		if header[0]&0x80 != 0 { // session packet: new video size, a config and a key frame follow
			w := int32(binary.BigEndian.Uint32(header[4:8]))
			h := int32(binary.BigEndian.Uint32(header[8:12]))
			s.width.Store(w)
			s.height.Store(h)
			continue
		}
		ptsFlags := binary.BigEndian.Uint64(header[0:8])
		size := binary.BigEndian.Uint32(header[8:12])
		if size > maxPacketLen {
			return errors.New("bad video packet")
		}
		payload := make([]byte, size)
		if _, err := io.ReadFull(r, payload); err != nil {
			return err
		}
		pts := int64(ptsFlags & ptsMask)
		switch {
		case ptsFlags&flagConfig != 0:
			config = make([]byte, 4+len(payload))
			binary.BigEndian.PutUint16(config[0:2], uint16(s.width.Load()))
			binary.BigEndian.PutUint16(config[2:4], uint16(s.height.Load()))
			copy(config[4:], payload)
			s.hub.publish(encodePacket(pktConfig, 0, config))
		case ptsFlags&flagKey != 0:
			s.hub.publish(encodePacket(pktKey, pts, payload))
			if !running {
				running = true
				s.setState("running")
				logf("Computer mode running: %dx%d", s.width.Load(), s.height.Load())
			}
		default:
			s.hub.publish(encodePacket(pktDelta, pts, payload))
		}
	}
}

func (s *desktopSession) readAudio(c net.Conn) {
	r := bufio.NewReaderSize(c, 64<<10)
	codec := make([]byte, 4)
	if _, err := io.ReadFull(r, codec); err != nil {
		return
	}
	if id := binary.BigEndian.Uint32(codec); id != codecRaw {
		logf("Sound is not available from this phone (code %#x)", id)
		return
	}
	header := make([]byte, 12)
	for {
		if _, err := io.ReadFull(r, header); err != nil {
			return
		}
		pts := int64(binary.BigEndian.Uint64(header[0:8]) & ptsMask)
		size := binary.BigEndian.Uint32(header[8:12])
		if size > 1<<20 {
			return
		}
		payload := make([]byte, size)
		if _, err := io.ReadFull(r, payload); err != nil {
			return
		}
		s.hub.publish(encodePacket(pktAudio, pts, payload))
	}
}

// readControl handles messages from the phone: clipboard changes (copy on the phone → paste here).
func (s *desktopSession) readControl(c net.Conn) {
	r := bufio.NewReader(c)
	for {
		kind, err := r.ReadByte()
		if err != nil {
			s.stop()
			return
		}
		switch kind {
		case 0: // clipboard
			var n uint32
			if binary.Read(r, binary.BigEndian, &n) != nil || n > 1<<20 {
				s.stop()
				return
			}
			text := make([]byte, n)
			if _, err := io.ReadFull(r, text); err != nil {
				return
			}
			s.hub.publish(encodePacket(pktClip, 0, text))
		case 1: // clipboard ack
			io.CopyN(io.Discard, r, 8)
		case 2: // uhid output
			var hdr [4]byte
			if _, err := io.ReadFull(r, hdr[:]); err != nil {
				return
			}
			io.CopyN(io.Discard, r, int64(binary.BigEndian.Uint16(hdr[2:4])))
		default:
			logf("Unknown message from the phone (%d)", kind)
			s.stop()
			return
		}
	}
}

// ---------- input: browser command → scrcpy control message ----------

type inputCommand struct {
	T      string  `json:"t"`
	A      string  `json:"a"`
	X      float64 `json:"x"`
	Y      float64 `json:"y"`
	DX     float64 `json:"dx"`
	DY     float64 `json:"dy"`
	Button int     `json:"button"`
	B      int     `json:"b"`
	Code   int32   `json:"code"`
	Meta   int32   `json:"meta"`
	Repeat int32   `json:"repeat"`
	S      string  `json:"s"`
	K      string  `json:"k"`
	C      string  `json:"c"`
	On     bool    `json:"on"`
}

func (s *desktopSession) handle(raw json.RawMessage) error {
	var c inputCommand
	if err := json.Unmarshal(raw, &c); err != nil {
		return err
	}
	switch c.T {
	case "mouse":
		return s.mouse(c)
	case "wheel":
		return s.wheel(c)
	case "key":
		action := byte(0)
		if c.A == "up" {
			action = 1
		}
		return s.key(action, c.Code, c.Repeat, c.Meta)
	case "text":
		return s.text(c.S)
	case "paste":
		return s.setClipboard(c.S, true)
	case "nav":
		return s.nav(c.K)
	case "launch":
		return s.launch(c.C)
	case "phoneScreen":
		on := byte(0)
		if c.On {
			on = 1
		}
		return s.send([]byte{10, on})
	}
	return nil
}

// Browser buttons (1 left, 2 right, 4 middle, 8 back, 16 forward) use the same bits as Android's.
func androidButton(browserButton int) int32 {
	switch browserButton {
	case 0:
		return 1
	case 1:
		return 4
	case 2:
		return 2
	case 3:
		return 8
	case 4:
		return 16
	}
	return 0
}

func (s *desktopSession) position(x, y float64) ([]byte, bool) {
	w, h := s.width.Load(), s.height.Load()
	if w == 0 || h == 0 {
		return nil, false
	}
	px := int32(math.Round(math.Min(1, math.Max(0, x)) * float64(w-1)))
	py := int32(math.Round(math.Min(1, math.Max(0, y)) * float64(h-1)))
	b := make([]byte, 12)
	binary.BigEndian.PutUint32(b[0:4], uint32(px))
	binary.BigEndian.PutUint32(b[4:8], uint32(py))
	binary.BigEndian.PutUint16(b[8:10], uint16(w))
	binary.BigEndian.PutUint16(b[10:12], uint16(h))
	return b, true
}

func (s *desktopSession) mouse(c inputCommand) error {
	pos, ok := s.position(c.X, c.Y)
	if !ok {
		return nil
	}
	var action byte
	var pressure uint16 = 0xFFFF
	var actionButton int32
	buttons := int32(c.B & 31)
	switch c.A {
	case "down":
		action = 0
		actionButton = androidButton(c.Button)
	case "up":
		action = 1
		actionButton = androidButton(c.Button)
		pressure = 0
	default:
		if buttons != 0 {
			action = 2 // drag
		} else {
			action = 7 // hover: moves the pointer, shows tooltips and resize cursors
		}
	}
	msg := make([]byte, 0, 32)
	msg = append(msg, 2, action)
	msg = binary.BigEndian.AppendUint64(msg, pointerMous)
	msg = append(msg, pos...)
	msg = binary.BigEndian.AppendUint16(msg, pressure)
	msg = binary.BigEndian.AppendUint32(msg, uint32(actionButton))
	msg = binary.BigEndian.AppendUint32(msg, uint32(buttons))
	return s.send(msg)
}

func scrollFixed(v float64) uint16 {
	n := math.Max(-1, math.Min(1, v/16))
	i := int32(n * 32768)
	if i >= 0x7FFF {
		i = 0x7FFF
	}
	return uint16(int16(i))
}

func (s *desktopSession) wheel(c inputCommand) error {
	pos, ok := s.position(c.X, c.Y)
	if !ok {
		return nil
	}
	msg := make([]byte, 0, 21)
	msg = append(msg, 3)
	msg = append(msg, pos...)
	msg = binary.BigEndian.AppendUint16(msg, scrollFixed(c.DX))
	msg = binary.BigEndian.AppendUint16(msg, scrollFixed(c.DY))
	msg = binary.BigEndian.AppendUint32(msg, 0)
	return s.send(msg)
}

func (s *desktopSession) key(action byte, code, repeat, meta int32) error {
	if code <= 0 {
		return nil
	}
	msg := []byte{0, action}
	msg = binary.BigEndian.AppendUint32(msg, uint32(code))
	msg = binary.BigEndian.AppendUint32(msg, uint32(repeat))
	msg = binary.BigEndian.AppendUint32(msg, uint32(meta))
	return s.send(msg)
}

// text types plain ASCII directly; anything else (Hebrew, emoji…) goes through a clipboard paste,
// because the phone's virtual key map can only type ASCII.
func (s *desktopSession) text(t string) error {
	if t == "" {
		return nil
	}
	ascii := true
	for _, r := range t {
		if r < 0x20 || r > 0x7e {
			ascii = false
			break
		}
	}
	if !ascii {
		return s.setClipboard(t, true)
	}
	for len(t) > 0 {
		chunk := t
		if len(chunk) > 300 {
			chunk = chunk[:300]
		}
		t = t[len(chunk):]
		msg := []byte{1}
		msg = binary.BigEndian.AppendUint32(msg, uint32(len(chunk)))
		msg = append(msg, chunk...)
		if err := s.send(msg); err != nil {
			return err
		}
	}
	return nil
}

func (s *desktopSession) setClipboard(t string, paste bool) error {
	if len(t) > 200_000 {
		t = t[:200_000]
	}
	msg := []byte{9}
	msg = binary.BigEndian.AppendUint64(msg, 0)
	p := byte(0)
	if paste {
		p = 1
	}
	msg = append(msg, p)
	msg = binary.BigEndian.AppendUint32(msg, uint32(len(t)))
	msg = append(msg, t...)
	return s.send(msg)
}

func (s *desktopSession) nav(k string) error {
	press := func(code int32) error {
		if err := s.key(0, code, 0, 0); err != nil {
			return err
		}
		return s.key(1, code, 0, 0)
	}
	switch k {
	case "back":
		return press(4)
	case "home":
		return press(3)
	case "recents":
		return press(187)
	case "vol+":
		return press(24)
	case "vol-":
		return press(25)
	case "notifications":
		return s.send([]byte{5})
	case "quick":
		return s.send([]byte{6})
	}
	return nil
}

// requestKeyframe restarts the phone's encoder (at most every 2 s): new viewers start with a key frame.
func (s *desktopSession) requestKeyframe() {
	now := time.Now().UnixMilli()
	if now-s.lastReset.Load() < 2000 {
		return
	}
	s.lastReset.Store(now)
	s.send([]byte{17})
}

func (s *desktopSession) send(msg []byte) error {
	s.mu.Lock()
	c := s.control
	s.mu.Unlock()
	if c == nil {
		return errors.New("not connected")
	}
	if s.width.Load() == 0 && msg[0] != 17 {
		return nil // the desktop screen isn't up yet
	}
	s.ctrlMu.Lock()
	defer s.ctrlMu.Unlock()
	c.SetWriteDeadline(time.Now().Add(2 * time.Second))
	_, err := c.Write(msg)
	return err
}

func (s *desktopSession) cleanup() {
	serial := s.req.Serial
	if s.awakeByApp {
		adb.shell(serial, 10*time.Second, "am stop-service -n "+shakelightPkg+"/.cast.DesktopAwakeService")
	}
	if s.port != "" {
		adb.run(5*time.Second, "-s", serial, "forward", "--remove", "tcp:"+s.port)
	}
	for _, st := range desktopSettings {
		old, ok := s.saved[st.namespace+" "+st.key]
		if !ok {
			continue
		}
		if old == "" || old == "null" {
			adb.shell(serial, 10*time.Second, "settings delete "+st.namespace+" "+st.key)
		} else {
			adb.shell(serial, 10*time.Second, "settings put "+st.namespace+" "+st.key+" "+old)
		}
	}
	// The server itself turns the phone's own screen back on when it exits.
}

func isClosedErr(err error) bool {
	return errors.Is(err, net.ErrClosed) || errors.Is(err, io.EOF) || strings.Contains(err.Error(), "use of closed")
}
