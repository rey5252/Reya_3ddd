# Dig or Die — Менеджер модів

Мод для [Dig or Die](https://store.steampowered.com/app/315460/Dig_or_Die/) (BepInEx 5), який додає в головне меню
кнопку **МОДИ** — між «Настройки» і «Выход». Кнопка — справжня кнопка гри (той самий стиль, розмір і шрифт),
колонка меню трохи ущільнюється, щоб усі 8 кнопок помістилися на тому ж місці.

Кнопка відкриває вікно менеджера модів у стилі меню гри:

- список усіх модів з `BepInEx/plugins` (з підпапками): назва, версія, GUID, файл, опис, залежності;
- перемикач для увімкнення/вимкнення мода (вимкнений мод — це `Мод.dll.disabled`, BepInEx його не бачить);
- статуси: **активний**, **вимкнено**, **увімкн./вимкн. після перезапуску**, **не завантажено** (помилка/немає залежності),
  **бібліотека**; помилки завантаження BepInEx показуються зверху;
- пошук і фільтри «Усі / Увімкнені / Вимкнені»;
- кнопки «Налаштування» (відкриває `.cfg` мода), «Файл» (показує мод у провіднику), «Відкрити папку модів»,
  «Оновити», «Перезапустити гру» (через Steam);
- українська, російська та англійська мови (автоматично або через конфіг).

## Встановлення

Готовий архів: [`jars/DigOrDie-ModManager.zip`](../jars/DigOrDie-ModManager.zip) (його збирає GitHub Actions), або
зберіть сам (див. нижче) — `dist/DigOrDie-ModManager.zip`.

1. Розпакуйте архів, закрийте гру, запустіть **`Install.bat`** — він сам знайде гру через Steam, поставить
   BepInEx 5.4.23.3 (x86) і мод, і виставить потрібне для Dig or Die налаштування BepInEx
   (`[Preloader.Entrypoint] Type = MonoBehaviour`).
2. Запускайте гру **через Steam**.

Вручну: скопіювати вміст `game-files/` з архіву в папку гри (де `DigOrDie.exe`). Детальніше — `package/README.txt`.

## Налаштування

`BepInEx/config/reya.digordie.modmanager.cfg`:

| Параметр | За замовчуванням | Опис |
| --- | --- | --- |
| `Language` | `Auto` | `Auto` / `English` / `Russian` / `Ukrainian`. Auto: українська на українській системі, інакше мова гри. |
| `ButtonInMenuColumn` | `true` | `false` — кнопка внизу екрана замість колонки меню. |
| `OpenHotkey` | (порожньо) | Клавіша, що відкриває менеджер будь-де, напр. `F8`. |

## Як це працює

- `src/DigOrDieModManager` — BepInEx-плагін. Harmony-патчі на `SScreenHome.OnInit/OnActivate/OnUpdate` створюють
  `CGuiButton` з параметрами кнопки «Выход» і перераховують позиції колонки (`MenuColumnLayout`).
  Поки вікно відкрите, `SScreen.IsInputLocked` повертає `true`, тож меню гри під ним не реагує.
  Вікно намальоване через Unity IMGUI з власними текстурами в кольорах гри (`Theme`).
- `src/DigOrDieModManager.Preloader` — BepInEx preloader-патчер. Якщо Windows не дала перейменувати `.dll`
  під час гри, зміна записується в `BepInEx/config/DigOrDieModManager.pending.txt` і застосовується при
  наступному запуску ще до завантаження плагінів.
- Ігрові типи беруться з NuGet-пакета [`DigOrDie.GameLibs`](https://www.nuget.org/packages/DigOrDie.GameLibs)
  (публіковані reference-збірки гри 1.11), BepInEx — з офіційного релізу (качається при першій збірці).

## Збірка

Потрібен [.NET SDK](https://dotnet.microsoft.com/download) 8+ (Windows, Linux або macOS):

```
cd digordie-modmanager
dotnet build -c Release
```

Результат: `dist/DigOrDie-ModManager.zip` (і окремі `.dll` у `src/*/bin/Release/`).
