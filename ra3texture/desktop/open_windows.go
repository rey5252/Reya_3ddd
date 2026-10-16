//go:build windows

package main

import (
	"os"
	"os/exec"
	"path/filepath"
	"syscall"
	"unsafe"
)

// Відкриває програму в окремому вікні Edge або Chrome (режим --app) з власним профілем,
// щоб налаштування й завантаження не змішувалися з основним браузером. Інакше — браузер за замовчуванням.
func openWindow(url string) {
	local := os.Getenv("LOCALAPPDATA")
	profile := filepath.Join(local, "RA3TextureStudio", "browser")
	candidates := []string{
		filepath.Join(os.Getenv("ProgramFiles(x86)"), `Microsoft\Edge\Application\msedge.exe`),
		filepath.Join(os.Getenv("ProgramFiles"), `Microsoft\Edge\Application\msedge.exe`),
		filepath.Join(local, `Microsoft\Edge\Application\msedge.exe`),
		filepath.Join(os.Getenv("ProgramFiles"), `Google\Chrome\Application\chrome.exe`),
		filepath.Join(os.Getenv("ProgramFiles(x86)"), `Google\Chrome\Application\chrome.exe`),
		filepath.Join(local, `Google\Chrome\Application\chrome.exe`),
	}
	for _, exe := range candidates {
		if st, err := os.Stat(exe); err == nil && !st.IsDir() {
			cmd := exec.Command(exe, "--app="+url, "--user-data-dir="+profile,
				"--no-first-run", "--no-default-browser-check", "--window-size=1440,900")
			if cmd.Start() == nil {
				return
			}
		}
	}
	if exec.Command("rundll32", "url.dll,FileProtocolHandler", url).Start() != nil {
		fatal("Не знайдено браузер. Відкрийте вручну: " + url)
	}
}

// Повідомлення про помилку у вікні (програма зібрана без консолі).
func fatal(msg string) {
	user32 := syscall.NewLazyDLL("user32.dll")
	box := user32.NewProc("MessageBoxW")
	text, _ := syscall.UTF16PtrFromString(msg)
	title, _ := syscall.UTF16PtrFromString("RA3 Texture Studio")
	box.Call(0, uintptr(unsafe.Pointer(text)), uintptr(unsafe.Pointer(title)), 0x10)
	os.Exit(1)
}
