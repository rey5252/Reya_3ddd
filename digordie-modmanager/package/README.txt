DIG OR DIE - МЕНЕДЖЕР МОДІВ
===========================

Додає в головне меню Dig or Die кнопку «МОДИ» (між «Настройки» і «Выход»).
Вона відкриває менеджер модів: список усіх модів, увімкнення/вимкнення одним кліком,
пошук, статус кожного мода, відкриття налаштувань (.cfg) і папки з модами.


ВСТАНОВЛЕННЯ (автоматично)
--------------------------
1. Повністю розпакуйте цей архів (ПКМ -> «Видобути все...»).
2. Закрийте гру і двічі клацніть Install.bat.
   Скрипт сам знайде Dig or Die через Steam, встановить BepInEx 5.4.23.3
   і менеджер модів. Якщо гру не знайдено - він попросить вибрати папку гри.
3. Запустіть гру ЧЕРЕЗ STEAM. У головному меню з'явиться кнопка «МОДИ».


ВСТАНОВЛЕННЯ (вручну)
---------------------
1. Steam -> Dig or Die -> ПКМ -> Керування -> Переглянути локальні файли.
2. Скопіюйте ВЕСЬ вміст папки game-files у папку гри (туди, де DigOrDie.exe).
3. Якщо у вас вже був BepInEx: відкрийте BepInEx\config\BepInEx.cfg і в розділі
   [Preloader.Entrypoint] поставте  Type = MonoBehaviour
4. Запускайте гру через Steam (запуск DigOrDie.exe напряму не завантажує моди).


ЯК КОРИСТУВАТИСЯ
----------------
* Нові моди (.dll файли BepInEx-плагінів) кладіть у папку  BepInEx\plugins
  (кнопка «Відкрити папку модів» у менеджері відкриває саме її).
* Перемикач зліва вмикає/вимикає мод. Вимкнений мод перейменовується
  в  Назва.dll.disabled  і BepInEx його не завантажує.
* Зміни діють після перезапуску гри - кнопка «Перезапустити гру» робить це сама.
* Статуси: АКТИВНИЙ / ВИМКНЕНО / УВІМК.(ВИМК.) ПІСЛЯ ПЕРЕЗАПУСКУ /
  НЕ ЗАВАНТАЖЕНО (мод увімкнений, але BepInEx не зміг його завантажити -
  дивіться BepInEx\LogOutput.log) / БІБЛІОТЕКА (допоміжний .dll для інших модів).
* Мову менеджера можна змінити в BepInEx\config\reya.digordie.modmanager.cfg
  (Language = Auto / English / Russian / Ukrainian). Там же можна задати
  клавішу для відкриття менеджера (OpenHotkey) і місце кнопки (ButtonInMenuColumn).
* Escape закриває вікно менеджера.

Моди для Dig or Die: https://www.nexusmods.com/digordie
                     https://github.com/ddmitv/dig-or-die-mods


ВИДАЛЕННЯ
---------
* Тільки менеджер: Uninstall.bat (BepInEx та інші моди залишаться).
* Повністю всі моди: видаліть з папки гри BepInEx, winhttp.dll, doorstop_config.ini,
  .doorstop_version, changelog.txt і перевірте цілісність файлів гри в Steam.


ENGLISH (short)
---------------
Adds a MODS button to the Dig or Die main menu that opens a mod manager
(enable/disable BepInEx plugins, search, status, open configs and the mods folder).
Install: extract the archive, close the game, run Install.bat, then start the game from Steam.
Manual install: copy the contents of game-files into the game folder (next to DigOrDie.exe);
if you already had BepInEx, set "Type = MonoBehaviour" under [Preloader.Entrypoint] in BepInEx.cfg.

BepInEx (https://github.com/BepInEx/BepInEx) is included under the LGPL-2.1 license.
