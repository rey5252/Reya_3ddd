# FortressTowers (Fortress Protocol) — мод для Red Alert 3

**FortressTowers** — мод для *Command & Conquer: Red Alert 3* (v1.12), зроблений на офіційному
**RA3 Mod SDK** (рушій SAGE). Він додає **10 нових оборонних веж — по 3 для кожної фракції плюс Цар-гармата для СРСР — і суперзброю «Велика Берта»**,
кнопки їх будівництва на будівельних майданчиках і загальний апгрейд
**«Посилена броня фортець»** (`Upgrade_FortressReinforcedPlating`, +25% HP усім вежам мода, $1500,
купується на будівельному майданчику).

Кожна вежа має **власну 3D-модель з текстурами у стилі RA3** (`FortressTowers/art/FT/*.w3x` + `.tga`, уже експортовані),
а логіка (зброя, броня, FX) зроблена як змінена копія ванільної споруди з `SageXml`. Деталі — розділ «3D-моделі» нижче.

Внутрішнє ім'я: `FortressTowers`, версія `1.0`. Контракт (ID, цифри, правила) — у [SPEC.md](SPEC.md).

## Вежі

| # | Вежа | ID | Фракція | Роль | Ціна | Час | HP | Дальність | Енергія | Основа |
|---|------|----|---------|------|-----:|----:|---:|----------:|--------:|--------|
| 1 | Призматичний шпиль | `AlliedPrismSpire` | Альянс | Протиназемна, ланцюговий промінь (3 цілі) | $1800 | 25 с | 3200 | 400 | −8 | Spectrum Tower |
| 2 | Кріо-бастіон | `AlliedCryoBastion` | Альянс | Контроль: заморожує й сповільнює по зоні | $1400 | 20 с | 2600 | 300 | −6 | Spectrum Tower + кріо-FX |
| 3 | Вежа «Егіда» | `AlliedAegisSpire` | Альянс | ППО + щит-проєктор (−35% урону по союзних спорудах у радіусі 200) | $1200 | 18 с | 2200 | 450 (повітря) | −5 | Multigunner Turret |
| 4 | Тесла-обеліск | `SovietTeslaObelisk` | СРСР | Протиназемна, далекобійна блискавка, ланцюг на 4 цілі; заряджається тесла-солдатами | $2000 | 28 с | 3600 | 450 | −10 | Tesla Coil |
| 5 | Флак-гніздо | `SovietFlakNest` | СРСР | ППО: 3 зенітні гармати, осколкова зона | $1000 | 15 с | 2400 | 500 (повітря) | −4 | Flak Cannon |
| 6 | Бункер «Пекло» | `SovietInfernoBunker` | СРСР | Проти піхоти: вогнемети 360°, підпал зони; гарнізон на 3 піхотинці | $1100 | 16 с | 3000 | 220 | −4 | Sentry Gun + вогняні FX |
| 7 | Шпиль Тенгу | `JapanTenguSpire` | Імперія | ППО: залпи самонавідних ракет | $1100 | 16 с | 2000 | 480 (повітря) | -4 | Wave-Force Tower / Nanoswarm Hive |
| 8 | Хвильова цитадель | `JapanWaveCitadel` | Імперія | Артилерія: заряджений хвильовий удар, пробиває лінію цілей | $2200 | 30 с | 3400 | 600 | -10 | Wave-Force Tower |
| 9 | Святилище Кіцуне | `JapanKitsuneShrine` | Імперія | Підтримка: псіонічний імпульс проти піхоти, виявляє невидимих (радіус 350) | $900 | 14 с | 1800 | 250 | -4 | Nanoswarm Hive |
| 10 | Цар-гармата | `SovietTsarCannon` | СРСР | Надважка далекобійна артилерія з довгим стволом: 1500 урону по площі, мертва зона 250 | $3500 | 40 с | 4500 | 1100 | -15 | власна модель |
| СЗ | Велика Берта | `SovietBigBertha` | СРСР | Будівля-суперзброя: велетенська залізнична гармата, раз на 4 хв залп 3 × 3000 урону в будь-яку точку карти | $5000 | 50 с | 6000 | вся карта | -20 | власна модель |

Умови будівництва:

- **Альянс:** 1 і 2 — Defense Bureau + Command Hub; 3 — лише Construction Yard.
- **СРСР:** 4 — Battle Lab; 5 — Construction Yard; 6 — Construction Yard + Barracks.
- **Імперія:** вежі будуються як **наноядра** (`<TowerId>Nanocore` — рухомий юніт, що розгортається
  у вежу), як і всі споруди Імперії. 8 потребує Nanotech Mainframe.

## Структура

```
ra3/
  README.md                  ← цей файл
  SPEC.md                    ← специфікація (джерело правди)
  scripts/
    merge_strings.py         ← зливає data/strings/*.str → data/mod.str
    validate.py              ← перевірка XML, підключень, ID, рядків, команд, VERIFY
    build_mod.bat            ← повне збирання через SDK (Windows)
    launch_mod.bat           ← запуск RA3 з модом (Windows)
  FortressTowers/            ← копіюється як RA3ModSDK\Mods\FortressTowers\
    data/
      mod.xml                ← кореневий AssetDeclaration, підключає все нижче
      mod.str                ← усі рядки (генерується merge_strings.py)
      Towers/Allied/*.xml    ← вежі Альянсу (одна вежа = один файл: GameObject + зброя)
      Towers/Soviet/*.xml    ← вежі СРСР
      Towers/Japan/*.xml     ← вежі Імперії (+ об'єкти наноядер)
      Logic/*.xml            ← LogicCommand, CommandSet-и, апгрейд
      strings/*.str          ← фрагменти рядків (по файлу на вежу + _Logic.str)
  preview/
    index.html               ← інтерактивне прев'ю «як це виглядає»
    art/<TowerId>.svg        ← ілюстрації веж
```

## Вимоги

- **Command & Conquer: Red Alert 3, патч 1.12** (Steam / EA app / диск — усі оновлені до 1.12).
- **Офіційний RA3 Mod SDK** (EA, 2009) — лише **Windows**. Збирання робить `BuildMod.bat` із кореня SDK
  (всередині — `BinaryAssetBuilder` і `MakeBig`).
- **Python 3.8+** — для `merge_strings.py` / `validate.py` (лише стандартна бібліотека).
  На Windows підійде `py` з python.org.

## Збирання крок за кроком

> Коротко: `scripts\build_mod.bat "C:\шлях\до\RA3ModSDK"` робить кроки 1–4 автоматично
> (зливає рядки, запускає перевірку, копіює мод, викликає `BuildMod.bat`). Якщо `validate.py`
> знаходить помилки, скрипт зупиняється; `build_mod.bat "<SDK>" /force` — збирати попри них.

1. **Злийте рядки** (з кореня репозиторію або будь-звідки):
   ```bat
   py -3 ra3\scripts\merge_strings.py
   ```
   Скрипт збирає `data\strings\*.str` за алфавітом (`_Logic.str` — останнім) у `data\mod.str`
   і зупиняється з помилкою, якщо мітка визначена двічі або блок не закритий `END`.
   `mod.str` вручну не редагуйте — правте фрагменти й запускайте скрипт знову.
2. **Перевірте мод:**
   ```bat
   py -3 ra3\scripts\validate.py
   ```
   Має бути `РЕЗУЛЬТАТ: ПРОЙДЕНО` (попередження допускаються).
3. **Скопіюйте мод у SDK:** теку `ra3\FortressTowers` → `RA3ModSDK\Mods\FortressTowers`
   (має вийти `RA3ModSDK\Mods\FortressTowers\data\mod.xml`).
4. **Зберіть** — з кореня SDK:
   ```bat
   cd /d C:\шлях\до\RA3ModSDK
   BuildMod.bat FortressTowers
   ```
   Уважно читайте вивід: помилки `BinaryAssetBuilder` вказують файл і рядок XML.
5. **Де лежить результат.** Проміжні файли — у `RA3ModSDK\BuiltMods\`. Готовий мод (`FortressTowers.big`
   + `FortressTowers_1.0.skudef`) має лежати у власній підтеці теки модів гри:
   **`%USERPROFILE%\Documents\Red Alert 3\Mods\FortressTowers\`**.
   В окремих інсталяціях/версіях SDK це може бути `%APPDATA%\Red Alert 3\Mods\FortressTowers\`,
   а «Документи» можуть бути перенаправлені (наприклад, в OneDrive) — **перевірте в кінці виводу
   `BuildMod.bat`, куди саме скопійовано `.big` і `.skudef`.**

## Запуск

- **Steam:** бібліотека → ПКМ по Red Alert 3 → **Властивості** → **Параметри запуску** → впишіть `-ui`.
  Тоді замість гри відкриється RA3 Control Center → **Game Browser** → вкладка **Mods** →
  виберіть `FortressTowers` → **Launch Game**. Якщо мода немає у списку — перевірте, що він
  лежить саме у `Documents\Red Alert 3\Mods\FortressTowers\` (або `%APPDATA%\Red Alert 3\Mods\`).
- **RA3 Control Center** (EA app / диск) → **Game Browser** → вкладка **Mods** → виберіть
  `FortressTowers` → **Launch Game**.
- **Командний рядок:**
  ```bat
  RA3.exe -modConfig "%USERPROFILE%\Documents\Red Alert 3\Mods\FortressTowers\FortressTowers_1.0.skudef"
  ```
- **Скриптом:** `ra3\scripts\launch_mod.bat` (шукає RA3.exe у реєстрі та стандартних теках;
  інший `.skudef` — першим аргументом, свій RA3.exe — змінною `set RA3_EXE=...`).

У грі: нові вежі — у вкладці «Оборона» будівельного майданчика (у Імперії — наноядра).
Грайте в режимі сутички або мережевої гри з тим самим модом в усіх гравців.

## Перевірка перед збиранням

**Будьте чесні з собою: цей XML написано без доступу до `SageXml` з SDK.** Структура відповідає
схемі RA3 (простір імен `uri:ea.com:eala:asset`, `AssetDeclaration`, `GameObject`, `WeaponTemplate`,
`LogicCommand`, `CommandSet`), але точні імена ванільних асетів — моделей W3X, анімацій, FX,
звуків, ArmorSet-ів, DamageType-ів, споруд-передумов, наборів команд будівельних майданчиків —
записано з пам'яті. Кожне таке місце позначено коментарем:

```xml
<!-- VERIFY: ім'я моделі Spectrum Tower у SageXml -->
```

Перед першим збиранням пройдіть **кожен** `<!-- VERIFY -->`:

1. Список і кількість по файлах показує `validate.py` (розділ «(f) Коментарі VERIFY»).
   Знайти їх у тексті:
   ```bat
   findstr /s /n /c:"VERIFY" ra3\FortressTowers\data\*.xml
   ```
2. Знайдіть оригінал у ванільних даних SDK — пошуком по `RA3ModSDK\SageXml`:
   ```bat
   cd /d C:\шлях\до\RA3ModSDK\SageXml
   findstr /s /i /m "TeslaCoil" *.xml
   findstr /s /i /n "id=\"SovietTeslaCoil\"" *.xml
   ```
   або PowerShell:
   ```powershell
   Get-ChildItem -Recurse -Filter *.xml | Select-String -Pattern 'SpectrumTower' | Select-Object Path, LineNumber -First 20
   ```
   Шукайте за англійськими назвами з колонки «Основа» (Spectrum Tower, Multigunner, Tesla Coil,
   Flak Cannon, Sentry Gun, Wave-Force Tower, Nanoswarm Hive), за ID будівельних майданчиків
   (`...ConstructionYard`) і за ID їхніх `CommandSet`.
3. Порівняйте з ванільним файлом і виправте ім'я в моді (регістр важливий). Коментар `VERIFY`
   після перевірки можна прибрати.
4. Особливо уважно: **перевизначення CommandSet-ів будівельних майданчиків** (`Logic/`) — вони
   мають містити *всі* ванільні кнопки, інакше вони зникнуть з гри; і **`ObjectPrerequisites`** —
   неправильне ім'я споруди-передумови зробить вежу недоступною.
5. Запустіть `validate.py` ще раз, потім `BuildMod.bat`. Помилка `BinaryAssetBuilder` на кшталт
   «unresolved reference» майже завжди означає ім'я, яке слід було перевірити.

Також перевірте в грі:

- **Кирилицю.** `mod.str` пишеться як UTF-8 без BOM з CRLF. Якщо в грі замість тексту
  «кракозябри» або `MISSING:` — спробуйте `merge_strings.py --bom` і перезберіть.
- **Енергію Імперії.** У моді вежі Імперії споживають енергію (-4 / -10 / -4), як оборона
  інших фракцій. Звірте з ванільною Wave-Force Tower у `SageXml`: якщо вона має інше
  `EnergyProduction`, виправте значення у `Towers/Japan/*.xml`.

## Нотатки щодо балансу

Цифри — стартова точка, не перевірена в реальних матчах. На що звернути увагу під час тестів:

- **Хвильова цитадель (600)** — найдальша вежа мода; вона може перестрілювати ванільну артилерію й
  інші вежі. Якщо оборона Імперії стає «черепахою», яку неможливо зламати, — зменште дальність
  або збільште час заряду.
- **Тесла-обеліск ($2000, 3600 HP)** — найміцніший, але найдорожчий за енергією (−10). Підзарядка
  тесла-солдатами робить його ще сильнішим — стежте, щоб комбінація не була непробивною.
- **Призматичний шпиль і Тесла-обеліск** б'ють ланцюгом (3 і 4 цілі) — небезпечні для щільних груп
  піхоти й легкої техніки; розподіл урону по стрибках варто тестувати окремо.
- **ППО (Егіда, Флак-гніздо, Шпиль Тенгу)** — дешевші ($1000–1200) і з більшою дальністю (450–500),
  ніж наземні вежі; щит «Егіди» (−35%) може робити бази Альянсу надто стійкими, якщо поставити
  кілька — перевірте, чи ефект не складається.
- **Святилище Кіцуне ($900, 1800 HP)** — найдешевше й найслабше, цінне виявленням невидимих
  (радіус 350) — це пряма контра шпигунам/невидимим юнітам.
- **Бункер «Пекло»** — короткий радіус (220), але гарнізон і підпал зони; сильний проти масової піхоти.
- **Апгрейд +25% HP за $1500** окупається лише за 3+ веж — так і задумано.
- Вежі Імперії розгортаються з наноядер: їх можна будувати далеко від бази, що підсилює агресивну
  «тауер-раш» тактику. Можливо, варто обмежити радіус розгортання.

Ванільні ціни й HP для порівняння дивіться в `SageXml` (атрибути `BuildCost`, `MaxHealth`).

## Прев'ю

Інтерактивне прев'ю з ілюстраціями й характеристиками веж: [preview/index.html](preview/index.html) —
відкрийте файл у будь-якому браузері (працює без інтернету).

## 3D-моделі

У мода є **власні 3D-моделі всіх 10 веж + 3 наноядер Імперії** з текстурами (див. [models/textures/README.md](models/textures/README.md)).
Їх будує процедурний генератор на Python (контракт — розділи «3D-моделі» й «Текстури» у [SPEC.md](SPEC.md)):

```
models/
  meshkit.py               ← бібліотека примітивів (numpy) + запис glTF 2.0 (.glb)
  towers/<TowerId>.py      ← генератор вежі (для Японії — ще й <TowerId>Nanocore)
  glb/<TowerId>.glb        ← результат (Y — вгору, 1 одиниця ≈ 1 м, фундамент 6×6 м)
  textures/                ← процедурні текстури <set>/<name>_{dif,nrm,spm}.png — див. models/textures/README.md
scripts/
  build_models.py          ← запуск усіх генераторів + звіт + (опційно) експорт у W3X
  blender_export_w3x.py    ← Blender-скрипт: .glb → .w3x (+ .tga текстури) у FortressTowers/art/FT/
FortressTowers/
  art/FT/*.w3x, *.tga      ← експортований арт (ART:-асети для SDK), див. FortressTowers/art/README.md
  data/Art/FortressModels.xml  ← Include усіх 12 моделей (ART:FT<Name>.w3x)
```

Ім'я моделі в грі — `FT` + назва без префікса фракції:

| Вежа | Модель (W3DContainer) | Рухомі кістки | Точки FX |
|------|-----------------------|---------------|----------|
| `AlliedPrismSpire` | `FTPrismSpire` | `TURRET` | `FX_MUZZLE01`, `FX_GLOW` |
| `AlliedCryoBastion` | `FTCryoBastion` | `TURRET`, нахил `EMITTER` | `FX_MUZZLE01`, `FX_GLOW` |
| `AlliedAegisSpire` | `FTAegisSpire` | `TURRET` | `FX_MUZZLE01..02`, `FX_SHIELDDOME` |
| `SovietTeslaObelisk` | `FTTeslaObelisk` | `EMITTER` | `FX_MUZZLE01`, `FX_GLOW` |
| `SovietFlakNest` | `FTFlakNest` | `TURRET`, нахил `BARREL` | `FX_MUZZLE01..03` |
| `SovietInfernoBunker` | `FTInfernoBunker` | `TURRET` | `FX_MUZZLE01..04`, `FX_GLOW` |
| `JapanTenguSpire` | `FTTenguSpire` (+ `FTTenguSpireNanocore`) | `TURRET` | `FX_MUZZLE01..06`, `FX_GLOW` |
| `JapanWaveCitadel` | `FTWaveCitadel` (+ `FTWaveCitadelNanocore`) | `TURRET`, нахил `EMITTER` | `FX_MUZZLE01`, `FX_GLOW` |
| `JapanKitsuneShrine` | `FTKitsuneShrine` (+ `FTKitsuneShrineNanocore`) | `EMITTER` | `FX_MUZZLE01`, `FX_GLOW` |

Кожна модель має ще кістку `BASE` (корінь). Бюджет — 3 000–12 000 трикутників на вежу, ≤ 2 500 на наноядро
(`build_models.py` перевіряє й показує фактичні цифри).

### Перегенерувати (.py → .glb)

Потрібні Python 3.8+ і `numpy` (для текстур — ще `Pillow`): `py -3 -m pip install numpy pillow`.

```bat
py -3 ra3\scripts\build_models.py                 :: усі models/towers/*.py → models/glb/*.glb + звіт
py -3 ra3\scripts\build_models.py --textures      :: спершу ще й models/textures/gen_*.py
py -3 ra3\scripts\build_models.py --only AlliedPrismSpire
py -3 ra3\scripts\build_models.py --no-generate   :: лише звіт по наявних .glb
```

Звіт: трикутники / вершини / вузли / матеріали / текстури кожного `.glb`, імена кісток, відсутні файли
й посилання на відсутні текстури (скрипт не падає, якщо частини моделей ще немає).
Окремий генератор можна запустити й напряму: `py -3 -I ra3\models\towers\AlliedPrismSpire.py`.

### Переглянути

- Будь-який переглядач glTF: «Засоби перегляду 3D» у Windows, <https://gltf-viewer.donmccurdy.com/>,
  Blender (*File → Import → glTF 2.0*). Текстури підтягуються з `models/textures/` за відносними
  шляхами, тож `.glb` не переносьте окремо від теки `models/`.
- 3D-режим прев'ю [preview/index.html](preview/index.html) (three.js з CDN — потрібен інтернет).
  Якщо браузер не вантажить `.glb` з `file://`, запустіть у теці `ra3` `py -3 -m http.server 8000`
  і відкрийте <http://localhost:8000/preview/>.

### Конвертувати в W3X (для гри)

RA3 читає моделі у форматі **W3X** (XML-варіант W3D). Шлях спільноти — Blender + плагін OpenSAGE:

1. Встановіть **Blender 3.6 LTS або 4.x** (<https://www.blender.org/download/>).
2. Встановіть плагін **OpenSAGE W3D/W3X** (`io_mesh_w3d`): архів `io_mesh_w3d.zip` з
   <https://github.com/OpenSAGE/OpenSAGE.BlenderPlugin/releases> → у Blender *Edit → Preferences →
   Add-ons → Install…* (у Blender 4.2+ архів можна просто перетягнути у вікно — встановиться як extension)
   → увімкніть «Import/Export Westwood W3D Format». Або вкажіть теку з `io_mesh_w3d` скрипту:
   `-- --addon-path D:\OpenSAGE.BlenderPlugin` (для git-клону потрібен `git submodule update --init`).
3. Експорт усіх моделей у `FortressTowers\art\FT\`:
   ```bat
   py -3 ra3\scripts\build_models.py --blender "C:\Program Files\Blender Foundation\Blender 4.2\blender.exe"
   ```
   або напряму:
   ```bat
   blender -b -P ra3\scripts\blender_export_w3x.py -- ra3\models\glb ra3\FortressTowers\art\FT
   ```
   Параметри скрипту (після `--`; через `build_models.py` — після окремого `--`):
   `--scale 10`, `--yaw <град>`, `--shader auto|<Name>.fx`, `--only A,B`, `--no-textures`,
   `--no-flip-normal-g`, `--technique N`, `--individual-files`, `--drop-fx-meshes`, `--keep-case`,
   `--save-blend <тека>` (зберегти підготовлену сцену `.blend` для ручного доопрацювання), `--addon-path <тека>`.

Що робить скрипт (умовності RA3):

- **Масштаб:** 10 одиниць світу RA3 на 1 м (фундамент 6 м → 60 одиниць, Prism 9 м → 90 одиниць —
  збігається з `<Geometry>` у XML веж). `--scale` — якщо в грі вежі завеликі/замалі.
- **Осі:** вбудований імпортер glTF у Blender сам перетворює Y-up → Z-up (як у W3D).
- **Напрям «вперед»:** вежі й юніти у W3D дивляться вздовж +X, а генератори робили по-різному.
  Таблиці поправок у скрипті: `YAW_FIX` (обертає всю модель, кратно 90°, щоб квадратний фундамент лишався
  по сітці; `SovietFlakNest` −90°: гармати дивились у glTF −Z = Blender +Y) і `NODE_YAW_FIX`
  (обертає лише піддерево вузла: `AlliedAegisSpire` → `Turret` +45°, бо голова змодельована
  розвернутою до камери). `AlliedCryoBastion` і `JapanTenguSpireNanocore` вже дивляться в +X.
  Решта — 0 (симетричні або без башти). Якщо башта в грі «цілиться боком» — виправте таблицю
  або `NaturalTurretAngle` у `TurretSettings`. `<!-- VERIFY -->`
- **Ієрархія:** кожен вузол glTF (`Base`, `Turret`, `Barrel`, `Emitter`, `FX_*`) стає кісткою
  (pivot) скелета `<FTName>_SKL` з ВЕЛИКИМИ літерами імен (`TURRET`, `FX_MUZZLE01`); меші жорстко
  прив'язані до своїх кісток, кожен матеріал — окремий W3X-меш `<FTName>.<КІСТКА>_<МАТЕРІАЛ>`.
  Маркери `FX_*` (кубики 5 см) експортуються прихованими й без тіні (`--drop-fx-meshes` — не експортувати).
- **Матеріали:** без текстур — `DefaultW3D.fx` з кольорами з PBR (DiffuseColor/SpecularColor/EmissiveColor).
  З текстурами (див. [models/textures/README.md](models/textures/README.md)) — шейдер фракції
  (`ObjectsAllied.fx` / `ObjectsSoviet.fx` / `ObjectsJapan.fx`, `<!-- VERIFY -->`) з
  `DiffuseTexture` = `_dif`, `NormalMap` = `_nrm` (зелений канал інвертується OpenGL → DirectX,
  `<!-- VERIFY -->`), `SpecMap` = `_spm` (R — блиск, G — самосвітіння, B — колір гравця).
  Текстури зберігаються як `FT<Набір>_<ім'я>_<dif|nrm|spm>.tga` + `.xml` Texture-асет поруч;
  `.w3x` сам їх підключає. DDS: TGA зазвичай стискає `BinaryAssetBuilder`; якщо ні — сконвертуйте
  у DXT5 `.dds` (texconv / NVIDIA Texture Tools) і виправте `File=` у `.xml` текстури.
- **Анімацій немає:** башти повертає рушій через `<Turret TurretNameKey=... TurretPitch=...>`,
  світіння/постріли — через `ParticleSysBone` / `WeaponFireFXBone` на кістках `FX_*`.

### Як вежі підключені до нових моделей

**Уже зроблено:** усі 12 `.w3x` (і 60 текстур `.tga`) експортовано в `FortressTowers/art/FT/`,
`data/mod.xml` підключає `Art/FortressModels.xml`, а `ScriptedModelDraw` усіх веж і наноядер указує на `FT<Name>`.
Якщо змінили модель — перебудуйте: `python3 models/build_all.py`, потім `python3 scripts/build_models.py --no-generate --blender <blender.exe>`.
Щоб повернутися до ванільних моделей, відкотіть ці зміни в git. Що саме змінено:

1. У `data/mod.xml` перед `<!-- Towers: Allies -->` додайте:
   ```xml
   <!-- Art: власні W3X-моделі веж (спершу scripts/build_models.py --blender ...) -->
   <Include type="all" source="Art/FortressModels.xml" />
   ```
2. У файлі кожної вежі (і наноядра) замініть `ScriptedModelDraw` на варіант з новою моделлю:
   `<Model Name="FT<Name>" />` у всіх станах (окремих пошкоджених/зруйнованих моделей немає),
   кістки — з таблиці вище (`TurretNameKey="TURRET"`, `TurretPitch="BARREL"`/`"EMITTER"`,
   `WeaponFireFXBone`/`WeaponLaunchBone BoneName="FX_MUZZLE"` — рушій сам шукає `FX_MUZZLE01`, `02`…),
   приберіть `Scale` (модель уже в масштабі), `<Texture Original=... New=...>`, `DrawScaleSetting`
   і всі `<Animation>` ванільних моделей (їхні скелети не збігаються з нашими).
3. `scripts\build_mod.bat` копіює в SDK і теку `art\` — нічого додатково переносити не треба.

### Чесні застереження

- **Моделі в грі не перевірялися.** Експорт перевірено лише в Blender 4.2 + плагін OpenSAGE 0.7.x
  (повторний імпорт `.w3x` тим самим плагіном дає ту саму геометрію й кістки); `BinaryAssetBuilder`
  і RA3 їх ще не бачили.
- Позначки `VERIFY` у `scripts/blender_export_w3x.py` і `data/Art/FortressModels.xml`: як SDK розв'язує
  `ART:` (тека `art/FT/` за першими двома літерами чи просто `art/`), імена шейдерів RA3 і їхні
  параметри / `TechniqueIndex`, напрям зеленого каналу normal map, канали SPM, масштаб 10 од./м,
  напрям башт. Найнадійніше — порівняти з будь-яким ванільним `.w3x` споруди тієї ж фракції
  (наприклад, імпортувати його тим самим плагіном у Blender).
- Без текстур моделі мають лише пласкі кольори PBR (`DefaultW3D.fx`): у грі вони виглядатимуть
  «пластиково» і без кольору гравця. Для кольору гравця потрібні faction-шейдер і SPM (канал B);
  прозорі матеріали (щити, кристали) можуть потребувати ручного налаштування Blend mode у W3D-панелі
  матеріалу (`--save-blend`, правка в Blender, повторний експорт з UI: *File → Export → Westwood W3D*, формат W3X, режим *Hierarchical Model*).
- Немає моделей пошкодженого стану, руїн, анімацій побудови/розгортання наноядер і LOD.
