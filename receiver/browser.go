package main

import (
	"errors"
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
)

var errNoBrowser = errors.New("no Chromium-based browser found")

// openViewer opens the viewer as its own full-screen window (Edge/Chrome "app" mode),
// falling back to the default browser.
func openViewer(url string) {
	if err := openAppWindow(url); err == nil {
		return
	}
	var cmd *exec.Cmd
	switch runtime.GOOS {
	case "windows":
		cmd = exec.Command("rundll32", "url.dll,FileProtocolHandler", url)
	case "darwin":
		cmd = exec.Command("open", url)
	default:
		cmd = exec.Command("xdg-open", url)
	}
	if cmd.Start() != nil {
		fmt.Println("Open this address in Chrome or Edge:", url)
	}
}

func openAppWindow(url string) error {
	browser := findChromium()
	if browser == "" {
		return errNoBrowser
	}
	// A separate profile makes it a fresh window that honours these flags even if
	// the browser is already open.
	profile := filepath.Join(os.TempDir(), "shakelight-receiver-profile")
	args := []string{
		"--app=" + url,
		"--user-data-dir=" + profile,
		"--start-fullscreen",
		"--autoplay-policy=no-user-gesture-required",
		"--no-first-run",
		"--no-default-browser-check",
	}
	return exec.Command(browser, args...).Start()
}

func findChromium() string {
	var candidates []string
	switch runtime.GOOS {
	case "windows":
		for _, base := range []string{os.Getenv("ProgramFiles(x86)"), os.Getenv("ProgramFiles"), os.Getenv("LocalAppData")} {
			if base == "" {
				continue
			}
			candidates = append(candidates,
				filepath.Join(base, `Microsoft\Edge\Application\msedge.exe`),
				filepath.Join(base, `Google\Chrome\Application\chrome.exe`),
			)
		}
	case "darwin":
		candidates = []string{
			"/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
			"/Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge",
			"/Applications/Chromium.app/Contents/MacOS/Chromium",
		}
	default:
		for _, name := range []string{"google-chrome", "google-chrome-stable", "microsoft-edge", "chromium", "chromium-browser"} {
			if path, err := exec.LookPath(name); err == nil {
				candidates = append(candidates, path)
			}
		}
	}
	for _, c := range candidates {
		if _, err := os.Stat(c); err == nil {
			return c
		}
	}
	return ""
}
