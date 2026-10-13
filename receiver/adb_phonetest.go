//go:build phonetest

// Test-only build (go build -tags phonetest) that runs ON the phone as the shell user, so Computer
// mode can be tested without a computer: every adb step is done locally instead.
package main

import (
	"fmt"
	"io"
	"net"
	"os"
	"os/exec"
	"strings"
	"sync"
	"time"
)

type adbTool struct {
	mu       sync.Mutex
	forwards map[string]net.Listener
}

var adb = &adbTool{forwards: map[string]net.Listener{}}

type adbDevice struct {
	Serial   string `json:"serial"`
	State    string `json:"state"`
	Model    string `json:"model"`
	Wireless bool   `json:"wireless"`
}

func (a *adbTool) binary() (string, error) { return "local", nil }

func (a *adbTool) devices() ([]adbDevice, error) {
	model, _ := exec.Command("getprop", "ro.product.model").Output()
	return []adbDevice{{Serial: "local", State: "device", Model: strings.TrimSpace(string(model)), Wireless: true}}, nil
}

func (a *adbTool) pair(hostPort, code string) (string, error) { return "Successfully paired", nil }
func (a *adbTool) connect(hostPort string) (string, error)    { return "connected", nil }
func (a *adbTool) mdnsConnectTargets() []string               { return nil }

func (a *adbTool) shell(serial string, timeout time.Duration, command string) (string, error) {
	out, err := exec.Command("sh", "-c", command).CombinedOutput()
	return strings.TrimSpace(string(out)), err
}

func (a *adbTool) run(timeout time.Duration, args ...string) (string, error) {
	if len(args) >= 2 && args[0] == "-s" {
		args = args[2:]
	}
	switch {
	case len(args) == 3 && args[0] == "push":
		data, err := os.ReadFile(args[1])
		if err != nil {
			return err.Error(), err
		}
		return "pushed", os.WriteFile(args[2], data, 0o644)
	case len(args) == 3 && args[0] == "forward" && args[1] == "--remove":
		a.mu.Lock()
		if ln := a.forwards[strings.TrimPrefix(args[2], "tcp:")]; ln != nil {
			ln.Close()
		}
		a.mu.Unlock()
		return "", nil
	case len(args) == 3 && args[0] == "forward":
		return a.forward(strings.TrimPrefix(args[2], "localabstract:"))
	case len(args) >= 2 && args[0] == "shell":
		return a.shell("", timeout, strings.Join(args[1:], " "))
	}
	return "", fmt.Errorf("phonetest: unsupported adb %v", args)
}

// forward emulates "adb forward tcp:0 localabstract:NAME": like adb, it accepts TCP connections even
// when nothing listens on the socket yet, and closes them right away in that case.
func (a *adbTool) forward(name string) (string, error) {
	ln, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		return err.Error(), err
	}
	port := fmt.Sprint(ln.Addr().(*net.TCPAddr).Port)
	a.mu.Lock()
	a.forwards[port] = ln
	a.mu.Unlock()
	go func() {
		for {
			c, err := ln.Accept()
			if err != nil {
				return
			}
			// Connect in accept order, like adb's single transport does: the server assigns the
			// video/audio/control roles by connection order.
			u, err := net.Dial("unix", "@"+name)
			if err != nil {
				c.Close()
				continue
			}
			go func() { io.Copy(u, c); u.Close() }()
			go func() { io.Copy(c, u); c.Close() }()
		}
	}()
	return port, nil
}

func (a *adbTool) start(args ...string) (*exec.Cmd, io.ReadCloser, error) {
	if len(args) >= 2 && args[0] == "-s" {
		args = args[2:]
	}
	if len(args) < 2 || args[0] != "shell" {
		return nil, nil, fmt.Errorf("phonetest: unsupported adb %v", args)
	}
	cmd := exec.Command("sh", "-c", strings.Join(args[1:], " "))
	out, err := cmd.StdoutPipe()
	if err != nil {
		return nil, nil, err
	}
	cmd.Stderr = cmd.Stdout
	return cmd, out, cmd.Start()
}
