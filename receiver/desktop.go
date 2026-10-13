package main

import (
	"encoding/json"
	"errors"
	"io"
	"net/http"
	"regexp"
	"strings"
	"sync"
	"time"
)

// desktopManager owns Computer mode: adb readiness, the phone list and the one running session.
type desktopManager struct {
	hub *hub

	mu       sync.Mutex
	adbReady bool
	adbError string
	devices  []adbDevice
	session  *desktopSession
	lastErr  string
}

func newDesktopManager(h *hub) *desktopManager {
	m := &desktopManager{hub: h}
	go m.watch()
	return m
}

// watch keeps the adb state and device list fresh for the setup screen.
func (m *desktopManager) watch() {
	triedMdns := time.Time{}
	for {
		if _, err := adb.binary(); err != nil {
			m.mu.Lock()
			m.adbReady, m.adbError = false, err.Error()
			m.mu.Unlock()
			time.Sleep(5 * time.Second)
			continue
		}
		list, err := adb.devices()
		m.mu.Lock()
		m.adbReady = err == nil
		if err != nil {
			m.adbError = err.Error()
		} else {
			m.adbError = ""
			m.devices = list
		}
		m.mu.Unlock()

		// A phone that was paired before reconnects by itself when it advertises Wireless debugging.
		if err == nil && len(list) == 0 && time.Since(triedMdns) > 10*time.Second {
			triedMdns = time.Now()
			for _, target := range adb.mdnsConnectTargets() {
				adb.connect(target)
			}
		}
		time.Sleep(2 * time.Second)
	}
}

type desktopStatus struct {
	AdbReady bool         `json:"adbReady"`
	AdbError string       `json:"adbError,omitempty"`
	Devices  []adbDevice  `json:"devices"`
	Session  sessionState `json:"session"`
}

type sessionState struct {
	State  string `json:"state"` // idle, starting, running, error
	Error  string `json:"error,omitempty"`
	Device string `json:"device,omitempty"`
}

func (m *desktopManager) status() desktopStatus {
	m.mu.Lock()
	defer m.mu.Unlock()
	st := desktopStatus{AdbReady: m.adbReady, AdbError: m.adbError, Devices: m.devices}
	if st.Devices == nil {
		st.Devices = []adbDevice{}
	}
	switch {
	case m.session != nil:
		st.Session = m.session.state()
	case m.lastErr != "":
		st.Session = sessionState{State: "error", Error: m.lastErr}
	default:
		st.Session = sessionState{State: "idle"}
	}
	return st
}

func (m *desktopManager) active() *desktopSession {
	m.mu.Lock()
	defer m.mu.Unlock()
	return m.session
}

type startRequest struct {
	Serial string `json:"serial"`
	Width  int    `json:"width"`
	Height int    `json:"height"`
	DPI    int    `json:"dpi"`
	Audio  bool   `json:"audio"`
}

func (m *desktopManager) start(req startRequest) error {
	if req.Width < 640 || req.Height < 480 || req.Width > 3840 || req.Height > 2160 {
		return errors.New("unsupported desktop size")
	}
	if req.DPI < 80 || req.DPI > 640 {
		req.DPI = 160
	}
	m.mu.Lock()
	if m.session != nil {
		m.mu.Unlock()
		return errors.New("the desktop is already running")
	}
	s := newDesktopSession(req, m.hub, func(s *desktopSession, err error) {
		m.mu.Lock()
		if m.session == s {
			m.session = nil
			m.lastErr = ""
			if err != nil {
				m.lastErr = err.Error()
			}
		}
		m.mu.Unlock()
	})
	m.session = s
	m.lastErr = ""
	m.mu.Unlock()
	go s.run()
	return nil
}

func (m *desktopManager) stop() {
	if s := m.active(); s != nil {
		s.stop()
	}
}

var hostPortRe = regexp.MustCompile(`^[0-9a-fA-F:.\[\]]+:\d{2,5}$`)

func (m *desktopManager) routes(mux *http.ServeMux) {
	writeJSON := func(w http.ResponseWriter, code int, v any) {
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(code)
		json.NewEncoder(w).Encode(v)
	}
	fail := func(w http.ResponseWriter, err string) {
		writeJSON(w, http.StatusBadRequest, map[string]string{"error": err})
	}
	decode := func(r *http.Request, v any) error {
		return json.NewDecoder(io.LimitReader(r.Body, 64<<10)).Decode(v)
	}

	mux.HandleFunc("/desktop/status", func(w http.ResponseWriter, r *http.Request) {
		writeJSON(w, http.StatusOK, m.status())
	})
	mux.HandleFunc("/desktop/start", func(w http.ResponseWriter, r *http.Request) {
		var req startRequest
		if r.Method != http.MethodPost || decode(r, &req) != nil {
			fail(w, "bad request")
			return
		}
		if err := m.start(req); err != nil {
			fail(w, err.Error())
			return
		}
		writeJSON(w, http.StatusOK, map[string]string{"ok": "starting"})
	})
	mux.HandleFunc("/desktop/stop", func(w http.ResponseWriter, r *http.Request) {
		m.stop()
		writeJSON(w, http.StatusOK, map[string]string{"ok": "stopped"})
	})
	mux.HandleFunc("/desktop/pair", func(w http.ResponseWriter, r *http.Request) {
		var req struct{ Address, Code string }
		if r.Method != http.MethodPost || decode(r, &req) != nil {
			fail(w, "bad request")
			return
		}
		if !hostPortRe.MatchString(req.Address) {
			fail(w, "Type the IP address and port from the pairing window, e.g. 192.168.1.20:37099")
			return
		}
		if len(req.Code) != 6 || strings.Trim(req.Code, "0123456789") != "" {
			fail(w, "The pairing code has 6 digits")
			return
		}
		out, err := adb.pair(req.Address, req.Code)
		if err != nil || !strings.Contains(strings.ToLower(out), "success") {
			fail(w, "Pairing failed: "+firstLine(out)+". Check the code and that both are on the same Wi-Fi.")
			return
		}
		// After pairing, connect through the phone's advertised Wireless debugging service.
		go func() {
			time.Sleep(time.Second)
			for _, t := range adb.mdnsConnectTargets() {
				adb.connect(t)
			}
		}()
		writeJSON(w, http.StatusOK, map[string]string{"message": "Paired! Connecting to the phone…"})
	})
	mux.HandleFunc("/desktop/connect", func(w http.ResponseWriter, r *http.Request) {
		var req struct{ Address string }
		if r.Method != http.MethodPost || decode(r, &req) != nil {
			fail(w, "bad request")
			return
		}
		if !hostPortRe.MatchString(req.Address) {
			fail(w, "Type the IP address and port shown on the Wireless debugging screen")
			return
		}
		out, err := adb.connect(req.Address)
		if err != nil || !strings.Contains(out, "connected") || strings.Contains(out, "failed") || strings.Contains(out, "cannot") {
			fail(w, "Could not connect: "+firstLine(out))
			return
		}
		writeJSON(w, http.StatusOK, map[string]string{"message": "Connected."})
	})
}

func firstLine(s string) string {
	s = strings.TrimSpace(s)
	if i := strings.IndexByte(s, '\n'); i >= 0 {
		s = s[:i]
	}
	if s == "" {
		return "no answer"
	}
	return s
}
