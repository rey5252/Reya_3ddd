#!/usr/bin/env bash
# Збирає ra3texture/dist/RA3TextureStudio.exe (Windows x64) з поточних веб-файлів програми.
# Потрібен лише Go 1.22+ (збирається на Linux, macOS або Windows/Git Bash).
set -euo pipefail
cd "$(dirname "$0")"
rm -rf web
mkdir -p web/js ../dist
cp ../index.html ../style.css web/
cp ../js/*.js web/js/
GOOS=windows GOARCH=amd64 CGO_ENABLED=0 go build -trimpath -ldflags "-H windowsgui -s -w" -o ../dist/RA3TextureStudio.exe .
echo "Готово: $(cd ../dist && pwd)/RA3TextureStudio.exe"
