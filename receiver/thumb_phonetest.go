//go:build phonetest

package main

import (
	"bytes"
	"image"
	"image/jpeg"
	"image/png"
	"net/http"
	"os/exec"
	"strings"
)

// /debug/thumb: a small JPEG of the desktop screen (or the phone screen with ?main=1), for testing.
func init() {
	extraRoutes = func(mux *http.ServeMux) {
		mux.HandleFunc("/debug/thumb", func(w http.ResponseWriter, r *http.Request) {
			id := ""
			out, _ := exec.Command("sh", "-c", "dumpsys SurfaceFlinger --display-id").Output()
			for _, line := range strings.Split(string(out), "\n") {
				if (r.URL.Query().Get("main") == "1") == strings.Contains(line, "HWC display") || strings.Contains(line, "scrcpy") && r.URL.Query().Get("main") != "1" {
					if f := strings.Fields(line); len(f) > 1 {
						id = f[1]
					}
				}
			}
			pngData, err := exec.Command("screencap", "-d", id, "-p").Output()
			if err != nil {
				http.Error(w, err.Error(), 500)
				return
			}
			src, err := png.Decode(bytes.NewReader(pngData))
			if err != nil {
				http.Error(w, err.Error(), 500)
				return
			}
			b := src.Bounds()
			tw := 960
			th := b.Dy() * tw / b.Dx()
			dst := image.NewRGBA(image.Rect(0, 0, tw, th))
			for y := 0; y < th; y++ {
				for x := 0; x < tw; x++ {
					dst.Set(x, y, src.At(b.Min.X+x*b.Dx()/tw, b.Min.Y+y*b.Dy()/th))
				}
			}
			w.Header().Set("Content-Type", "image/jpeg")
			jpeg.Encode(w, dst, &jpeg.Options{Quality: 60})
		})
	}
}
