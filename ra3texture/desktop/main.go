// RA3 Texture Studio для Windows: один .exe з вбудованою програмою.
// Запускає локальний сервер лише на 127.0.0.1 і відкриває програму в окремому вікні Edge/Chrome
// (режим --app, без адресного рядка). Коли вікно закрите і сторінка більше не відзначається, процес завершується.
package main

import (
	"bytes"
	"embed"
	"io"
	"io/fs"
	"net"
	"net/http"
	"os"
	"strings"
	"sync/atomic"
	"time"
)

//go:embed web
var webFS embed.FS

const (
	fixedPort = "47811"              // постійний порт, щоб другий запуск відкривав уже запущену програму
	pingPath  = "/__ra3ts_ping"      // сторінка відзначається тут кожні кілька секунд
	signature = "ra3-texture-studio" // відповідь на ping, за якою впізнаємо свій сервер
	idleLimit = 3 * time.Minute      // скільки чекати без відміток (фонові вкладки браузер пригальмовує до 1/хв)
	startWait = 90 * time.Second     // запас на перший запуск браузера
)

// Скрипт, який додаємо в index.html: відмітка «вікно ще відкрите».
const heartbeat = `<script>setInterval(function(){fetch('` + pingPath + `',{cache:'no-store'}).catch(function(){})},3000)</script>`

var lastSeen atomic.Int64

func touch() { lastSeen.Store(time.Now().UnixNano()) }

func main() {
	addr := "127.0.0.1:" + fixedPort
	ln, err := net.Listen("tcp", addr)
	if err != nil {
		// Порт зайнятий: якщо це вже запущена програма, просто відкриваємо ще одне вікно.
		if isOurServer(addr) {
			openWindow("http://" + addr + "/")
			return
		}
		ln, err = net.Listen("tcp", "127.0.0.1:0")
		if err != nil {
			fatal("Не вдалося запустити локальний сервер: " + err.Error())
		}
	}
	url := "http://" + ln.Addr().String() + "/"

	site, err := fs.Sub(webFS, "web")
	if err != nil {
		fatal("Пошкоджена збірка: " + err.Error())
	}
	index, err := fs.ReadFile(site, "index.html")
	if err != nil {
		fatal("Пошкоджена збірка: немає index.html")
	}
	index = bytes.Replace(index, []byte("</body>"), []byte(heartbeat+"</body>"), 1)

	files := http.FileServer(http.FS(site))
	mux := http.NewServeMux()
	mux.HandleFunc(pingPath, func(w http.ResponseWriter, r *http.Request) {
		touch()
		w.Header().Set("Cache-Control", "no-store")
		io.WriteString(w, signature)
	})
	mux.HandleFunc("/", func(w http.ResponseWriter, r *http.Request) {
		touch()
		w.Header().Set("Cache-Control", "no-cache")
		if r.URL.Path == "/" || r.URL.Path == "/index.html" {
			w.Header().Set("Content-Type", "text/html; charset=utf-8")
			w.Write(index)
			return
		}
		files.ServeHTTP(w, r)
	})

	touch()
	start := time.Now()
	go http.Serve(ln, mux)

	if os.Getenv("RA3TS_NO_WINDOW") == "" {
		openWindow(url)
	} else {
		println(url)
	}

	for {
		time.Sleep(2 * time.Second)
		idle := time.Since(time.Unix(0, lastSeen.Load()))
		if time.Since(start) > startWait && idle > idleLimit {
			os.Exit(0)
		}
	}
}

func isOurServer(addr string) bool {
	c := http.Client{Timeout: 2 * time.Second}
	resp, err := c.Get("http://" + addr + pingPath)
	if err != nil {
		return false
	}
	defer resp.Body.Close()
	b, _ := io.ReadAll(io.LimitReader(resp.Body, 64))
	return strings.TrimSpace(string(b)) == signature
}
