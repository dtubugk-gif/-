package main

import (
	"net"
	"sync"
)

// subscriber is one open viewer page.
type subscriber struct {
	ch chan []byte
	// After a dropped video packet the decoder is broken until the next key frame,
	// so skip video until one arrives.
	waitKey bool
}

// hub connects the single active phone to every open viewer page.
type hub struct {
	mu   sync.Mutex
	subs map[*subscriber]struct{}
	// Latest hello / codec config / status / app list, replayed to viewers that open later.
	latest map[byte][]byte
	phone  net.Conn
	// desktop is the running Computer-mode session; while set, it owns the viewer.
	desktop *desktopSession
}

func newHub() *hub {
	return &hub{subs: map[*subscriber]struct{}{}, latest: map[byte][]byte{}}
}

func (h *hub) subscribe() *subscriber {
	s := &subscriber{ch: make(chan []byte, 256), waitKey: true}
	h.mu.Lock()
	h.subs[s] = struct{}{}
	for _, kind := range []byte{pktHello, pktConfig, pktStatus, pktApps} {
		if pkt := h.latest[kind]; pkt != nil {
			s.ch <- pkt
		}
	}
	h.mu.Unlock()
	h.requestKeyframe()
	return s
}

func (h *hub) unsubscribe(s *subscriber) {
	h.mu.Lock()
	delete(h.subs, s)
	h.mu.Unlock()
}

func (h *hub) publish(pkt []byte) {
	kind := pkt[0]
	needKey := false

	h.mu.Lock()
	switch kind {
	case pktHello, pktConfig, pktStatus, pktApps:
		h.latest[kind] = pkt
	case pktBye:
		h.latest = map[byte][]byte{}
	}
	for s := range h.subs {
		video := kind == pktKey || kind == pktDelta
		if video && s.waitKey {
			if kind == pktDelta {
				continue
			}
			s.waitKey = false
		}
		select {
		case s.ch <- pkt:
		default:
			// Viewer can't keep up: drop, and resync on the next key frame.
			if video {
				s.waitKey = true
				needKey = true
			}
		}
	}
	h.mu.Unlock()

	if needKey {
		h.requestKeyframe()
	}
}

// setPhone makes conn the active phone, disconnecting any previous one (newest wins).
func (h *hub) setPhone(conn net.Conn) {
	h.mu.Lock()
	old := h.phone
	h.phone = conn
	h.mu.Unlock()
	if old != nil {
		old.Close()
	}
}

// clearPhone forgets conn if it is still the active phone. Returns true if it was.
func (h *hub) clearPhone(conn net.Conn) bool {
	h.mu.Lock()
	defer h.mu.Unlock()
	if h.phone != conn {
		return false
	}
	h.phone = nil
	return true
}

func (h *hub) requestKeyframe() {
	h.mu.Lock()
	d := h.desktop
	h.mu.Unlock()
	if d != nil {
		d.requestKeyframe()
		return
	}
	h.sendToPhone(encodeControl(ctrlRequestKeyframe, nil))
}

// setDesktop hands the viewer to a Computer-mode session (nil when it ends). A phone that is
// mirroring over Wi-Fi is disconnected so the two streams never mix.
func (h *hub) setDesktop(d *desktopSession) {
	h.mu.Lock()
	h.desktop = d
	old := h.phone
	if d != nil {
		h.phone = nil
		h.latest = map[byte][]byte{}
	}
	h.mu.Unlock()
	if d != nil && old != nil {
		old.Close()
	}
}

// clearDesktop detaches d if it is still the active session. Returns true if it was.
func (h *hub) clearDesktop(d *desktopSession) bool {
	h.mu.Lock()
	defer h.mu.Unlock()
	if h.desktop != d {
		return false
	}
	h.desktop = nil
	return true
}

func (h *hub) desktopSession() *desktopSession {
	h.mu.Lock()
	defer h.mu.Unlock()
	return h.desktop
}

// sendToPhone writes one control packet. A single Write keeps concurrent packets from interleaving.
func (h *hub) sendToPhone(frame []byte) bool {
	h.mu.Lock()
	phone := h.phone
	h.mu.Unlock()
	if phone == nil {
		return false
	}
	_, err := phone.Write(frame)
	return err == nil
}
