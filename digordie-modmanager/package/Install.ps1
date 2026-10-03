<#
  Installer for Reya's Dig or Die mods (Mod Manager, New Mobs). Shared by their packages.

  Copies BepInEx and the mods from game-files\ into the Dig or Die folder (found through Steam automatically),
  sets the BepInEx option Dig or Die needs (Preloader.Entrypoint Type = MonoBehaviour) and, for New Mobs,
  downloads its library DODModAPI from nuget.org.

    Install.ps1                      install
    Install.ps1 -GamePath "D:\Games\Dig or Die"
    Install.ps1 -Uninstall           remove the mods of this package (BepInEx and other mods stay)
    Install.ps1 -DisableOtherMods    turn off other mods without asking (-KeepOtherMods: leave them on)
#>
param(
    [string]$GamePath,
    [switch]$Uninstall,
    [switch]$DisableOtherMods,
    [switch]$KeepOtherMods
)

$ErrorActionPreference = 'Stop'
$AppId = 315460
$ExeName = 'DigOrDie.exe'
$Here = Split-Path -Parent $MyInvocation.MyCommand.Path
$Source = Join-Path $Here 'game-files'
$DODModAPIVersion = '1.0.3'
# Folders of our own mods: never treated as "other mods"
$OurModsPattern = '[\\/](DigOrDieModManager|DigOrDieMobs|DODModAPI)[\\/]'

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

# Other mods already in the game folder: BepInEx plugins/patchers (except this manager)
# and MelonLoader (version.dll + Mods). Leftovers of earlier experiments often live there.
function Find-OtherMods([string]$Game) {
    $found = @()
    $hasMelonLoader = Test-Path -LiteralPath (Join-Path $Game 'MelonLoader')
    $folders = @('BepInEx\plugins', 'BepInEx\patchers')
    if ($hasMelonLoader) { $folders += 'Mods', 'Plugins' }
    foreach ($sub in $folders) {
        $dir = Join-Path $Game $sub
        if (Test-Path -LiteralPath $dir) {
            $found += @(Get-ChildItem -LiteralPath $dir -Recurse -File -Force |
                Where-Object { $_.Extension -ieq '.dll' -and $_.FullName -notmatch $OurModsPattern -and $_.Name -ine 'dodmodapi.dll' })
        }
    }
    $versionDll = Join-Path $Game 'version.dll'
    if ($hasMelonLoader -and (Test-Path -LiteralPath $versionDll)) {
        $found += Get-Item -LiteralPath $versionDll
    }
    return $found
}

function Disable-OtherMods([string]$Game) {
    $others = @(Find-OtherMods $Game)
    if ($others.Count -eq 0) { return }

    Say ''
    Say 'Знайдено інші моди (наприклад, залишки тестів іншої моделі):' Yellow
    foreach ($f in $others) { Say ('  - ' + $f.FullName.Substring($Game.Length).TrimStart('\', '/')) }

    if ($DisableOtherMods) { $answer = 'т' }
    elseif ($KeepOtherMods) { $answer = 'н' }
    else { $answer = Read-Host 'Вимкнути їх? Потім їх можна знову увімкнути в менеджері МОДИ. [Т/н]' }

    if ($answer.Trim() -ne '' -and $answer.Trim() -notmatch '^(т|t|y|д|так|да|yes)') {
        Say 'Інші моди залишено без змін.' Gray
        return
    }

    $count = 0
    foreach ($f in $others) {
        $target = $f.FullName + '.disabled'
        try {
            if (Test-Path -LiteralPath $target) { Remove-Item -LiteralPath $target -Force }
            Rename-Item -LiteralPath $f.FullName -NewName ($f.Name + '.disabled') -Force
            $count++
        } catch {
            Say ('  Не вдалося вимкнути ' + $f.Name + ': ' + $_.Exception.Message) Red
        }
    }
    Say "Вимкнено модів: $count (файли перейменовано в *.dll.disabled)." Green
}

# New Mobs needs DODModAPI (https://github.com/ddmitv/dig-or-die-mods). It is not ours to bundle,
# so it is taken from its official nuget.org package.
function Ensure-DODModAPI([string]$Game) {
    $plugins = Join-Path (Join-Path $Game 'BepInEx') 'plugins'
    $existing = @(Get-ChildItem -LiteralPath $plugins -Recurse -File -Force -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -ieq 'dodmodapi.dll' -or $_.Name -ieq 'dodmodapi.dll.disabled' })
    if ($existing | Where-Object { $_.Name -ieq 'dodmodapi.dll' }) {
        Say 'DODModAPI вже встановлено.' Green
        return
    }
    $disabled = $existing | Select-Object -First 1
    if ($disabled) {
        Rename-Item -LiteralPath $disabled.FullName -NewName 'dodmodapi.dll' -Force
        Say 'DODModAPI знову увімкнено.' Green
        return
    }

    Say "Завантажую DODModAPI $DODModAPIVersion (потрібен для нових монстрів) з nuget.org..." Gray
    $url = "https://api.nuget.org/v3-flatcontainer/digordie.dodmodapi/$DODModAPIVersion/digordie.dodmodapi.$DODModAPIVersion.nupkg"
    $tmp = Join-Path ([IO.Path]::GetTempPath()) ('dodmodapi-' + [guid]::NewGuid().ToString() + '.zip')
    try {
        [Net.ServicePointManager]::SecurityProtocol = [Net.ServicePointManager]::SecurityProtocol -bor [Net.SecurityProtocolType]::Tls12
        Invoke-WebRequest -UseBasicParsing -Uri $url -OutFile $tmp
        Add-Type -AssemblyName System.IO.Compression.FileSystem
        $zip = [IO.Compression.ZipFile]::OpenRead($tmp)
        try {
            $entry = $zip.Entries | Where-Object { $_.FullName -ieq 'lib/net35/dodmodapi.dll' } | Select-Object -First 1
            if (-not $entry) { throw 'у пакеті немає lib/net35/dodmodapi.dll' }
            $destDir = Join-Path $plugins 'DODModAPI'
            New-Item -ItemType Directory -Path $destDir -Force | Out-Null
            [IO.Compression.ZipFileExtensions]::ExtractToFile($entry, (Join-Path $destDir 'dodmodapi.dll'), $true)
        } finally {
            $zip.Dispose()
        }
        Say 'DODModAPI встановлено.' Green
    } catch {
        Say ('Не вдалося завантажити DODModAPI: ' + $_.Exception.Message) Red
        Say 'Без нього нові монстри не з''являться. Завантажте dodmodapi.dll вручну:' Yellow
        Say '  https://github.com/ddmitv/dig-or-die-mods/releases  і покладіть його в BepInEx\plugins' Yellow
    } finally {
        Remove-Item -LiteralPath $tmp -Force -ErrorAction SilentlyContinue
    }
}

try {
    Say ''
    Say '=== Dig or Die: моди від Reya ===' Cyan
    $hasManager = Test-Path -LiteralPath (Join-Path $Source 'BepInEx\plugins\DigOrDieModManager')
    $hasMobs = Test-Path -LiteralPath (Join-Path $Source 'BepInEx\plugins\DigOrDieMobs')

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
        # Remove exactly the mod folders this package installs.
        $dirs = @()
        foreach ($sub in @('BepInEx\plugins', 'BepInEx\patchers')) {
            $packaged = Join-Path $Source $sub
            if (Test-Path -LiteralPath $packaged) {
                $dirs += @(Get-ChildItem -LiteralPath $packaged -Directory | ForEach-Object { Join-Path $sub $_.Name })
            }
        }
        if ($dirs.Count -eq 0) { $dirs = @('BepInEx\plugins\DigOrDieModManager', 'BepInEx\patchers\DigOrDieModManager') }
        foreach ($dir in $dirs) {
            $full = Join-Path $GamePath $dir
            if (Test-Path -LiteralPath $full) { Remove-Item -LiteralPath $full -Recurse -Force; Say "Видалено: $dir" }
        }
        Say 'Готово. BepInEx та інші моди залишилися.' Green
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
    Say 'Скопійовано BepInEx і моди.' Green

    if ($hadCfg) {
        Set-EntrypointType $cfgTarget
        Say 'Оновлено BepInEx.cfg (Entrypoint Type = MonoBehaviour).' Green
    }

    if ($hasMobs) { Ensure-DODModAPI $GamePath }

    Disable-OtherMods $GamePath

    Say ''
    Say 'Готово! Запускайте Dig or Die ЧЕРЕЗ STEAM.' Cyan
    if ($hasManager) {
        Say 'У головному меню між «Настройки» і «Выход» з''явиться кнопка МОДИ.' Cyan
    }
    if ($hasMobs) {
        Say 'Нові монстри з''являються самі у своїх місцях. Щоб побачити їх одразу: Enter у грі, команда /mobs' Cyan
    }
    Say "Встановлено в: $GamePath" Gray
    Say 'Нові моди (.dll) кладіть у: BepInEx\plugins' Gray
    Say 'Перший запуск з BepInEx може тривати трохи довше.' Gray
    if ($hasManager) {
        Say ''
        Say 'Якщо стара кнопка «МОДИ» у правому верхньому куті не зникне: Steam -> Dig or Die -> Властивості ->' Gray
        Say 'Встановлені файли -> «Перевірити цілісність файлів гри», потім ще раз запустіть Install.bat.' Gray
    }
}
catch {
    Say ''
    Say ("Помилка: " + $_.Exception.Message) Red
    exit 1
}
