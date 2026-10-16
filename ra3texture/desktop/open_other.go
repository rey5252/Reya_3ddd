//go:build !windows

package main

import (
	"os"
	"os/exec"
	"runtime"
)

// Для Linux/macOS (і для перевірки збірки): відкриває браузер за замовчуванням.
func openWindow(url string) {
	name := "xdg-open"
	if runtime.GOOS == "darwin" {
		name = "open"
	}
	if exec.Command(name, url).Start() != nil {
		println("Відкрийте в браузері: " + url)
	}
}

func fatal(msg string) {
	println(msg)
	os.Exit(1)
}
