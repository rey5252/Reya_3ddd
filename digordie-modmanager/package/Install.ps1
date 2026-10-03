<#
  Dig or Die Mod Manager - installer.

  Copies BepInEx and the Mod Manager into the Dig or Die folder (found through Steam automatically)
  and sets the BepInEx option Dig or Die needs (Preloader.Entrypoint Type = MonoBehaviour).

    Install.ps1                      install
    Install.ps1 -GamePath "D:\Games\Dig or Die"
    Install.ps1 -Uninstall           remove the Mod Manager (BepInEx and other mods stay)
#>
param(
    [string]$GamePath,
    [switch]$Uninstall
)

$ErrorActionPreference = 'Stop'
$AppId = 315460
$ExeName = 'DigOrDie.exe'
$Here = Split-Path -Parent $MyInvocation.MyCommand.Path
$Source = Join-Path $Here 'game-files'

function Say([string]$Text, [string]$Color = 'Gray') { Write-Host $Text -ForegroundColor $Color }

function Get-SteamRoots {
    $roots = @()
    foreach ($key in @(
            @{ Path = 'HKCU:\Software\Valve\Steam'; Name = 'SteamPath' },
            @{ Path = 'HKLM:\SOFTWARE\WOW6432Node\Valve\Steam'; Name = 'InstallPath' },
            @{ Path = 'HKLM:\SOFTWARE\Valve\Steam'; Name = 'InstallPath' })) {
        $value = (Get-ItemProperty -Path $key.Path -Name $key.Name -ErrorAction SilentlyContinue).($key.Name)
        if ($value) { $roots += $value }
    }
    $roots += "${env:ProgramFiles(x86)}\Steam", "$env:ProgramFiles\Steam"
    $roots | Where-Object { $_ -and (Test-Path $_) } | ForEach-Object { [IO.Path]::GetFullPath($_.Replace('/', '\')) } | Select-Object -Unique
}

function Get-SteamLibraries {
    $libraries = @()
    foreach ($root in Get-SteamRoots) {
        $libraries += $root
        $vdf = Join-Path $root 'steamapps\libraryfolders.vdf'
        if (Test-Path $vdf) {
            foreach ($match in [regex]::Matches((Get-Content $vdf -Raw), '"path"\s+"([^"]+)"')) {
                $libraries += $match.Groups[1].Value.Replace('\\', '\')
            }
        }
    }
    $libraries | Where-Object { Test-Path $_ } | Select-Object -Unique
}

function Find-Game {
    foreach ($library in Get-SteamLibraries) {
        $folders = @('Dig or Die')
        $manifest = Join-Path $library "steamapps\appmanifest_$AppId.acf"
        if (Test-Path $manifest) {
            $m = [regex]::Match((Get-Content $manifest -Raw), '"installdir"\s+"([^"]+)"')
            if ($m.Success) { $folders = @($m.Groups[1].Value) + $folders }
        }
        foreach ($folder in $folders) {
            $candidate = Join-Path $library "steamapps\common\$folder"
            if (Test-Path (Join-Path $candidate $ExeName)) { return $candidate }
        }
    }
    return $null
}

function Ask-GameFolder {
    Say 'Не вдалося знайти Dig or Die автоматично.' Yellow
    try {
        Add-Type -AssemblyName System.Windows.Forms
        $dialog = New-Object System.Windows.Forms.FolderBrowserDialog
        $dialog.Description = "Виберіть папку гри Dig or Die (де лежить $ExeName)"
        $dialog.ShowNewFolderButton = $false
        if ($dialog.ShowDialog() -eq [System.Windows.Forms.DialogResult]::OK) { return $dialog.SelectedPath }
    } catch { }
    Say 'Steam -> Dig or Die -> Керування -> Переглянути локальні файли, скопіюйте шлях з адресного рядка.' Gray
    return (Read-Host 'Вставте шлях до папки гри').Trim('"', ' ')
}

function Set-EntrypointType([string]$CfgPath) {
    $lines = New-Object 'System.Collections.Generic.List[string]'
    foreach ($l in @(Get-Content $CfgPath -Encoding UTF8)) { $lines.Add($l) }

    $section = $null
    $sectionIndex = -1
    $found = $false
    for ($i = 0; $i -lt $lines.Count; $i++) {
        $line = $lines[$i].Trim()
        if ($line -match '^\[(.+)\]$') {
            $section = $Matches[1]
            if ($section -eq 'Preloader.Entrypoint') { $sectionIndex = $i }
            continue
        }
        if ($section -eq 'Preloader.Entrypoint' -and $line -match '^Type\s*=') {
            $lines[$i] = 'Type = MonoBehaviour'
            $found = $true
        }
    }
    if (-not $found) {
        if ($sectionIndex -ge 0) {
            $lines.Insert($sectionIndex + 1, 'Type = MonoBehaviour')
        } else {
            $lines.Add('')
            $lines.Add('[Preloader.Entrypoint]')
            $lines.Add('Assembly = UnityEngine.dll')
            $lines.Add('Type = MonoBehaviour')
            $lines.Add('Method = .cctor')
        }
    }
    [IO.File]::WriteAllLines($CfgPath, $lines.ToArray(), (New-Object System.Text.UTF8Encoding($false)))
}

try {
    Say ''
    Say '=== Dig or Die Mod Manager ===' Cyan

    if (-not $Uninstall -and -not (Test-Path (Join-Path $Source 'BepInEx'))) {
        throw 'Поруч з Install.ps1 немає папки "game-files". Спочатку повністю розпакуйте архів (ПКМ -> Видобути все), потім запускайте Install.bat.'
    }

    if (-not $Uninstall) { $Source = (Resolve-Path -LiteralPath $Source).Path.TrimEnd('\') }

    if (-not $GamePath) { $GamePath = Find-Game }
    if (-not $GamePath) { $GamePath = Ask-GameFolder }
    if (-not $GamePath -or -not (Test-Path (Join-Path $GamePath $ExeName))) {
        throw "У папці `"$GamePath`" немає $ExeName. Перевірте шлях до гри."
    }
    $GamePath = [IO.Path]::GetFullPath($GamePath)
    Say "Папка гри: $GamePath" Green

    while (Get-Process -Name 'DigOrDie' -ErrorAction SilentlyContinue) {
        Say 'Гра зараз запущена. Закрийте Dig or Die і натисніть Enter...' Yellow
        [void](Read-Host)
    }

    if ($Uninstall) {
        foreach ($dir in @('BepInEx\plugins\DigOrDieModManager', 'BepInEx\patchers\DigOrDieModManager')) {
            $full = Join-Path $GamePath $dir
            if (Test-Path $full) { Remove-Item $full -Recurse -Force; Say "Видалено: $dir" }
        }
        Say 'Менеджер модів видалено. BepInEx та інші моди залишилися.' Green
        Say 'Щоб повністю прибрати моди: видаліть папку BepInEx, winhttp.dll, doorstop_config.ini і перевірте цілісність файлів гри в Steam.' Gray
        return
    }

    $cfgTarget = Join-Path (Join-Path (Join-Path $GamePath 'BepInEx') 'config') 'BepInEx.cfg'
    $hadCfg = Test-Path $cfgTarget

    Get-ChildItem -Path $Source -Recurse -Force -File | ForEach-Object {
        $relative = $_.FullName.Substring($Source.Length).TrimStart('\', '/')
        if ($hadCfg -and $relative.Replace('/', '\') -ieq 'BepInEx\config\BepInEx.cfg') { return }
        $target = Join-Path $GamePath $relative
        $targetDir = Split-Path -Parent $target
        if (-not (Test-Path $targetDir)) { New-Item -ItemType Directory -Path $targetDir -Force | Out-Null }
        Copy-Item -LiteralPath $_.FullName -Destination $target -Force
    }
    Say 'Скопійовано BepInEx і менеджер модів.' Green

    if ($hadCfg) {
        Set-EntrypointType $cfgTarget
        Say 'Оновлено BepInEx.cfg (Entrypoint Type = MonoBehaviour).' Green
    }

    Say ''
    Say 'Готово! Запускайте Dig or Die ЧЕРЕЗ STEAM - у головному меню з''явиться кнопка МОДИ.' Cyan
    Say 'Нові моди (.dll) кладіть у: BepInEx\plugins' Gray
    Say 'Перший запуск з BepInEx може тривати трохи довше.' Gray
}
catch {
    Say ''
    Say ("Помилка: " + $_.Exception.Message) Red
    exit 1
}
