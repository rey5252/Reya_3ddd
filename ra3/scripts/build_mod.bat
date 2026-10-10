@echo off
chcp 65001 >nul
setlocal EnableExtensions
rem ==========================================================
rem  FortressTowers 1.0 — збирання мода через RA3 Mod SDK
rem  Використання:  build_mod.bat "C:\RA3ModSDK" [/force]
rem    /force — збирати навіть якщо validate.py знайшов помилки
rem ==========================================================

set "SCRIPT_DIR=%~dp0"
set "MOD_NAME=FortressTowers"
for %%I in ("%SCRIPT_DIR%..\%MOD_NAME%") do set "MOD_SRC=%%~fI"

if "%~1"=="" (
    echo [ПОМИЛКА] Не вказано шлях до RA3 Mod SDK.
    echo Використання: %~nx0 "C:\RA3ModSDK" [/force]
    exit /b 1
)
for %%I in ("%~1") do set "SDK=%%~fI"
set "FORCE="
if /i "%~2"=="/force" set "FORCE=1"

if not exist "%SDK%\BuildMod.bat" (
    echo [ПОМИЛКА] У "%SDK%" немає BuildMod.bat — це не корінь RA3 Mod SDK.
    exit /b 1
)
if not exist "%MOD_SRC%\data\mod.xml" (
    echo [ПОМИЛКА] Не знайдено "%MOD_SRC%\data\mod.xml".
    exit /b 1
)

rem ---- Python ------------------------------------------------
set "PY="
where py >nul 2>nul && set "PY=py -3"
if not defined PY (
    where python >nul 2>nul && set "PY=python"
)
if not defined PY (
    echo [ПОМИЛКА] Не знайдено Python 3 ^(py або python у PATH^). Встановіть з python.org.
    exit /b 1
)

echo.
echo === 1/4  Зливаю рядки data\strings\*.str у data\mod.str ...
%PY% -I "%SCRIPT_DIR%merge_strings.py"
if errorlevel 1 (
    echo [ПОМИЛКА] merge_strings.py завершився з помилкою — див. повідомлення вище.
    exit /b 1
)

echo.
echo === 2/4  Перевіряю мод ^(validate.py^) ...
%PY% -I "%SCRIPT_DIR%validate.py" "%MOD_SRC%"
if errorlevel 1 (
    if defined FORCE (
        echo [УВАГА] Є помилки перевірки, але вказано /force — продовжую.
    ) else (
        echo [ПОМИЛКА] Перевірка знайшла помилки. Виправте їх або запустіть з /force.
        exit /b 1
    )
)

echo.
echo === 3/4  Копіюю "%MOD_SRC%" у "%SDK%\Mods\%MOD_NAME%" ...
if not exist "%SDK%\Mods" mkdir "%SDK%\Mods"
rem /MIR — тека призначення стає точною копією (видаляє застарілі файли саме в Mods\FortressTowers)
robocopy "%MOD_SRC%" "%SDK%\Mods\%MOD_NAME%" /MIR /NFL /NDL /NJH /NJS /NP >nul
if errorlevel 8 (
    echo [ПОМИЛКА] robocopy не зміг скопіювати файли ^(код %errorlevel%^).
    exit /b 1
)
echo     Скопійовано.

echo.
echo === 4/4  Запускаю BuildMod.bat %MOD_NAME% у "%SDK%" ...
echo     ^(це може тривати кілька хвилин; уважно читайте вивід BinaryAssetBuilder^)
pushd "%SDK%"
call BuildMod.bat %MOD_NAME%
set "BUILD_RC=%errorlevel%"
popd
if not "%BUILD_RC%"=="0" (
    echo [ПОМИЛКА] BuildMod.bat повернув код %BUILD_RC%. Шукайте рядки з "error" у виводі вище.
    exit /b 1
)

echo.
echo === Готово.
set "OUT_DIR=%USERPROFILE%\Documents\Red Alert 3\Other\Mods\%MOD_NAME%"
if exist "%OUT_DIR%\%MOD_NAME%_1.0.skudef" (
    echo     Зібраний мод: "%OUT_DIR%"
) else (
    echo     Не знайдено "%OUT_DIR%\%MOD_NAME%_1.0.skudef".
    echo     Перевірте у виводі BuildMod.bat, куди скопійовано .big і .skudef
    echo     ^(також можливі "%APPDATA%\Red Alert 3\Mods\%MOD_NAME%" і "%SDK%\BuiltMods"^).
)
echo     Запуск: scripts\launch_mod.bat  або  RA3 Control Center -^> Game Browser -^> Mods
exit /b 0
