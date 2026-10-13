//go:build !phonetest

package main

import (
	"archive/zip"
	"bytes"
	"context"
	"errors"
	"fmt"
	"io"
	"net/http"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
	"strings"
	"sync"
	"time"
)

// adbTool runs Google's adb, which Computer mode uses to reach the phone over Wireless debugging.
// It prefers an adb that is already installed (so it shares the running adb server and the
// computer's existing pairing) and downloads Google's platform-tools only as a fallback.
type adbTool struct {
	mu   sync.Mutex
	path string
}

var adb = &adbTool{}

func (a *adbTool) binary() (string, error) {
	a.mu.Lock()
	defer a.mu.Unlock()
	if a.path != "" {
		return a.path, nil
	}
	if p := findInstalledAdb(); p != "" {
		a.path = p
		return p, nil
	}
	p, err := downloadPlatformTools()
	if err != nil {
		return "", err
	}
	a.path = p
	return p, nil
}

func adbExe() string {
	if runtime.GOOS == "windows" {
		return "adb.exe"
	}
	return "adb"
}

func findInstalledAdb() string {
	if p := os.Getenv("SHAKELIGHT_ADB"); p != "" {
		return p
	}
	if p, err := exec.LookPath("adb"); err == nil {
		return p
	}
	home, _ := os.UserHomeDir()
	var candidates []string
	if sdk := os.Getenv("ANDROID_HOME"); sdk != "" {
		candidates = append(candidates, filepath.Join(sdk, "platform-tools", adbExe()))
	}
	if sdk := os.Getenv("ANDROID_SDK_ROOT"); sdk != "" {
		candidates = append(candidates, filepath.Join(sdk, "platform-tools", adbExe()))
	}
	switch runtime.GOOS {
	case "windows":
		candidates = append(candidates,
			filepath.Join(os.Getenv("LOCALAPPDATA"), `Android\Sdk\platform-tools\adb.exe`),
			`C:\platform-tools\adb.exe`,
			filepath.Join(home, `platform-tools\adb.exe`),
			filepath.Join(home, `Downloads\platform-tools\adb.exe`),
		)
	case "darwin":
		candidates = append(candidates,
			filepath.Join(home, "Library/Android/sdk/platform-tools/adb"),
			"/opt/homebrew/bin/adb", "/usr/local/bin/adb")
	default:
		candidates = append(candidates, filepath.Join(home, "Android/Sdk/platform-tools/adb"), "/usr/bin/adb")
	}
	candidates = append(candidates, filepath.Join(toolsDir(), "platform-tools", adbExe()))
	for _, c := range candidates {
		if st, err := os.Stat(c); err == nil && !st.IsDir() {
			return c
		}
	}
	return ""
}

func toolsDir() string {
	base, err := os.UserCacheDir()
	if err != nil {
		base = os.TempDir()
	}
	return filepath.Join(base, "ShakeLight")
}

// downloadPlatformTools fetches Google's official platform-tools for this OS into the cache folder.
func downloadPlatformTools() (string, error) {
	var name string
	switch runtime.GOOS {
	case "windows":
		name = "windows"
	case "darwin":
		name = "darwin"
	case "linux":
		if runtime.GOARCH != "amd64" {
			return "", errors.New("install adb with your package manager (e.g. sudo apt install adb)")
		}
		name = "linux"
	default:
		return "", fmt.Errorf("no adb available for %s", runtime.GOOS)
	}
	url := "https://dl.google.com/android/repository/platform-tools-latest-" + name + ".zip"
	logf("Downloading Android platform-tools (adb) from Google…")
	client := &http.Client{Timeout: 3 * time.Minute}
	resp, err := client.Get(url)
	if err != nil {
		return "", fmt.Errorf("download adb: %w", err)
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		return "", fmt.Errorf("download adb: HTTP %d", resp.StatusCode)
	}
	data, err := io.ReadAll(resp.Body)
	if err != nil {
		return "", fmt.Errorf("download adb: %w", err)
	}
	zr, err := zip.NewReader(bytes.NewReader(data), int64(len(data)))
	if err != nil {
		return "", fmt.Errorf("unpack adb: %w", err)
	}
	dest := toolsDir()
	for _, f := range zr.File {
		// Only the files adb needs; skip anything with a suspicious path.
		if !strings.HasPrefix(f.Name, "platform-tools/") || strings.Contains(f.Name, "..") || f.FileInfo().IsDir() {
			continue
		}
		base := filepath.Base(f.Name)
		if !(base == adbExe() || strings.HasPrefix(strings.ToLower(base), "adbwin") || strings.HasPrefix(base, "lib")) {
			continue
		}
		out := filepath.Join(dest, filepath.FromSlash(f.Name))
		if err := os.MkdirAll(filepath.Dir(out), 0o755); err != nil {
			return "", err
		}
		rc, err := f.Open()
		if err != nil {
			return "", err
		}
		w, err := os.OpenFile(out, os.O_CREATE|os.O_TRUNC|os.O_WRONLY, 0o755)
		if err != nil {
			rc.Close()
			return "", err
		}
		_, err = io.Copy(w, rc)
		rc.Close()
		w.Close()
		if err != nil {
			return "", err
		}
	}
	p := filepath.Join(dest, "platform-tools", adbExe())
	if _, err := os.Stat(p); err != nil {
		return "", errors.New("adb not found in the download")
	}
	logf("adb ready.")
	return p, nil
}

// run executes one adb command and returns its combined output.
func (a *adbTool) run(timeout time.Duration, args ...string) (string, error) {
	bin, err := a.binary()
	if err != nil {
		return "", err
	}
	ctx, cancel := context.WithTimeout(context.Background(), timeout)
	defer cancel()
	cmd := exec.CommandContext(ctx, bin, args...)
	hideWindow(cmd)
	out, err := cmd.CombinedOutput()
	return strings.TrimSpace(string(out)), err
}

// start launches a long-running adb command (the phone-side server); the caller waits/kills it.
func (a *adbTool) start(args ...string) (*exec.Cmd, io.ReadCloser, error) {
	bin, err := a.binary()
	if err != nil {
		return nil, nil, err
	}
	cmd := exec.Command(bin, args...)
	hideWindow(cmd)
	out, err := cmd.StdoutPipe()
	if err != nil {
		return nil, nil, err
	}
	cmd.Stderr = cmd.Stdout
	if err := cmd.Start(); err != nil {
		return nil, nil, err
	}
	return cmd, out, nil
}

type adbDevice struct {
	Serial string `json:"serial"`
	State  string `json:"state"`
	Model  string `json:"model"`
	// Wireless is true for Wireless-debugging connections (serial "ip:port" or "adb-…._adb-tls-connect._tcp").
	Wireless bool `json:"wireless"`
}

func (a *adbTool) devices() ([]adbDevice, error) {
	out, err := a.run(10*time.Second, "devices", "-l")
	if err != nil {
		return nil, fmt.Errorf("adb devices: %v %s", err, out)
	}
	var list []adbDevice
	for _, line := range strings.Split(out, "\n") {
		fields := strings.Fields(line)
		if len(fields) < 2 || strings.HasPrefix(line, "List of devices") || strings.HasPrefix(line, "*") {
			continue
		}
		d := adbDevice{Serial: fields[0], State: fields[1]}
		for _, f := range fields[2:] {
			if strings.HasPrefix(f, "model:") {
				d.Model = strings.ReplaceAll(strings.TrimPrefix(f, "model:"), "_", " ")
			}
		}
		d.Wireless = strings.Contains(d.Serial, "._adb-tls-connect.") || strings.Contains(d.Serial, ":")
		list = append(list, d)
	}
	return list, nil
}

// pair runs "adb pair" with the code shown under Wireless debugging → Pair device with pairing code.
func (a *adbTool) pair(hostPort, code string) (string, error) {
	return a.run(30*time.Second, "pair", hostPort, code)
}

func (a *adbTool) connect(hostPort string) (string, error) {
	return a.run(15*time.Second, "connect", hostPort)
}

// mdnsConnectTargets lists phones advertising Wireless debugging on the network ("ip:port").
func (a *adbTool) mdnsConnectTargets() []string {
	out, err := a.run(8*time.Second, "mdns", "services")
	if err != nil {
		return nil
	}
	var targets []string
	for _, line := range strings.Split(out, "\n") {
		if !strings.Contains(line, "_adb-tls-connect._tcp") {
			continue
		}
		fields := strings.Fields(line)
		if len(fields) > 0 {
			last := fields[len(fields)-1]
			if strings.Count(last, ":") == 1 && strings.Count(last, ".") == 3 {
				targets = append(targets, last)
			}
		}
	}
	return targets
}

func (a *adbTool) shell(serial string, timeout time.Duration, command string) (string, error) {
	return a.run(timeout, "-s", serial, "shell", command)
}
