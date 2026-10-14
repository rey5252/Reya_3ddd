# Fortress Protocol — специфікація мода (контракт для всіх частин)

Мод для **Command & Conquer: Red Alert 3** (v1.12, офіційний RA3 Mod SDK, рушій SAGE).
Додає 10 нових оборонних веж — по 3 для кожної фракції плюс надважка артилерія СРСР «Цар-гармата». Внутрішнє ім'я мода: `FortressTowers`, версія `1.0`.

Цей файл — єдине джерело правди. ID, шляхи, рядки й цифри нижче не змінювати.

## Структура репозиторію

```
ra3/
  SPEC.md                          ← цей файл
  README.md                        ← інструкція (укр.)
  scripts/                         ← .bat-обгортки для збирання / запуску
  FortressTowers/                  ← копіюється як RA3ModSDK/Mods/FortressTowers/
    data/
      mod.xml                      ← кореневий AssetDeclaration, підключає все нижче
      mod.str                      ← усі рядки (зливаються з data/strings/*.str)
      Towers/<Faction>/<TowerId>.xml   ← одна вежа = один файл (GameObject + зброя + все своє)
      Logic/                       ← LogicCommand / LogicCommandSet / апгрейди (інтеграція)
      strings/<TowerId>.str        ← фрагмент рядків вежі (потім зливається у mod.str)
  preview/
    index.html                     ← інтерактивне прев'ю "як це виглядає"
    art/<TowerId>.svg              ← ілюстрація вежі
```

## Загальні правила XML (RA3 Mod SDK)

- Кожен XML-файл — `<?xml version="1.0" encoding="utf-8"?>` + `<AssetDeclaration xmlns="uri:ea.com:eala:asset" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">`.
- Файл вежі підключає те, на що посилається, через `<Includes>`: `<Include type="reference" source="DATA:static.xml" />`, `<Include type="reference" source="DATA:global.xml" />`, `<Include type="reference" source="DATA:audio.xml" />`.
- Підхід SDK: **вежа — це модифікована копія оригінальної споруди з `SageXml`**. Щоб мод не потребував нових 3D-моделей, кожна вежа повторно використовує модель/анімації/FX оригіналу (колонка «Основа» нижче), а змінюються ID, статистика, зброя, масштаб (`Scale` у Draw-модулі) і колір/FX.
- На початку кожного файлу — XML-коментар: яку оригінальну споруду взято за основу, що змінено, і що автор має перевірити в `SageXml` (точні імена моделей W3X, FX, звуків). Імена базових асетів, у яких немає певності, позначати коментарем `<!-- VERIFY: ... -->` — не вигадувати мовчки.
- Регістр важливий; відступи — пробіли (4).
- Атрибути GameObject (мінімум): `id`, `inheritFrom="BaseStructure"` (або обґрунтований інший базовий шаблон), `Side`, `EditorSorting="STRUCTURE"`, `KindOf="PRELOAD STRUCTURE SELECTABLE IMMOBILE SCORE ..."`, `BuildCost`, `BuildTime`, `CommandSet`, `EnergyProduction` (від'ємне = споживання), `ThreatLevel`, `VisionRange`, `ShroudClearingRange`.
- Дочірні елементи (мінімум): `DisplayName` (`NAME:<TowerId>`), `Description` (`DESC:<TowerId>`), `TypeDescription` (`TYPE:<TowerId>`), `ArmorSet`, `WeaponSet`/`WeaponSetUpdate`, `Draws`, `Behaviors`, `Body` (`ActiveBody MaxHealth`), `Geometry`, `AI`, `ObjectPrerequisites` (Allies/Soviets), `Sounds`.
- Зброя: `<WeaponTemplate id="<TowerId>Weapon" ...>` (і `<TowerId>WeaponAA`, `...Secondary` за потреби) у тому ж файлі; `AttackRange` = дальності нижче, `Damage` — через `DamageNugget` / `SpecialEffect`-нагети.
- Колір фракції у FX: Allies — синьо-білий, Soviets — червоно-помаранчевий, Empire — білий/червоний/бірюзовий.

## Рядки (`data/strings/<TowerId>.str`)

Формат RA3 `.str` (мова гри одна — пишемо **українською**, англійська назва в дужках не потрібна):

```
NAME:AlliedPrismSpire
"Призматичний шпиль"
END

DESC:AlliedPrismSpire
"Важка протиназемна вежа. Промінь перескакує на 3 цілі.\nСильна проти: техніки, піхоти.\nСлабка проти: авіації."
END

TYPE:AlliedPrismSpire
"Протиназемна оборона"
END
```

Для апгрейдів/кнопок інтеграція додає свої мітки (`TOOLTIP:...` тощо) у `data/strings/_Logic.str`.

## Вежі

| # | TowerId | Назва (укр.) | Фракція (`Side`) | Роль | Ціна $ | Час, с | HP | Дальність | Енергія | Основа (копія з SageXml) |
|---|---------|--------------|------------------|------|-------:|-------:|----:|----------:|--------:|--------------------------|
| 1 | `AlliedPrismSpire` | Призматичний шпиль | Allies | Протиназемна, ланцюговий промінь | 1800 | 25 | 3200 | 400 | -8 | Spectrum Tower |
| 2 | `AlliedCryoBastion` | Кріо-бастіон | Allies | Контроль: заморожує й сповільнює, зона | 1400 | 20 | 2600 | 300 | -6 | Spectrum Tower + FX кріо-легіонера/Cryocopter |
| 3 | `AlliedAegisSpire` | Вежа «Егіда» | Allies | ППО + щит-проєктор (−35% урону по союзних спорудах у радіусі 200) | 1200 | 18 | 2200 | 450 (повітря) | -5 | Multigunner Turret |
| 4 | `SovietTeslaObelisk` | Тесла-обеліск | Soviet | Протиназемна, далекобійна блискавка, ланцюг на 4 цілі; заряджається тесла-солдатами | 2000 | 28 | 3600 | 450 | -10 | Tesla Coil |
| 5 | `SovietFlakNest` | Флак-гніздо | Soviet | ППО, 3 зенітні гармати, осколкова зона | 1000 | 15 | 2400 | 500 (повітря) | -4 | Flak Cannon |
| 6 | `SovietInfernoBunker` | Бункер «Пекло» | Soviet | Проти піхоти: вогнемети по колу 360°, підпал зони; гарнізон на 3 піхотинці | 1100 | 16 | 3000 | 220 | -4 | Sentry Gun (+ FX вогню) |
| 7 | `JapanTenguSpire` | Шпиль Тенгу | Empire (`Japan`) | ППО: залпи самонавідних ракет | 1100 | 16 | 2000 | 480 (повітря) | -4 | Wave-Force Tower / Nanoswarm Hive core art |
| 8 | `JapanWaveCitadel` | Хвильова цитадель | Empire (`Japan`) | Артилерія: заряджений хвильовий удар, пробиває лінію цілей | 2200 | 30 | 3400 | 600 | -10 | Wave-Force Tower |
| 9 | `JapanKitsuneShrine` | Святилище Кіцуне | Empire (`Japan`) | Підтримка: псіонічний імпульс проти піхоти, виявляє невидимих (радіус 350) | 900 | 14 | 1800 | 250 | -4 | Nanoswarm Hive |
| 10 | `SovietTsarCannon` | Цар-гармата | Soviet | Надважка далекобійна артилерія: снаряд 1500 урону, радіус 70, раз на 8 с; мертва зона 250; фундамент 8×8 | 3500 | 40 | 4500 | 1100 | -15 | власна модель (турель + ствол ~11 м) |

Умови будівництва (`ObjectPrerequisites`, імена перевірити в SageXml — `<!-- VERIFY -->`):
- Allies: 1 і 2 — Defense Bureau + Command Hub; 3 — лише Construction Yard.
- Soviet: 4 — Battle Lab; 5 і 6 — Construction Yard (+ Barracks для 6).
- Empire: споруди Імперії будуються як **наноядра**: кожна вежа Японії має ще об'єкт ядра `<TowerId>Nanocore` (рухомий юніт, що розгортається у вежу) в тому ж файлі. 8 — потребує Nanotech Mainframe.

Енергія Імперії: вежі споживають енергію так само, як оборона інших фракцій (`<!-- VERIFY -->` проти ванільної Wave-Force Tower). Наноядра (`<TowerId>Nanocore`) використовують ванільний `JapanNanocoreCommandSet`.

## Кнопки та набори команд (інтеграція, `data/Logic/`)

- Команди: `Command_Construct<TowerId>` (для Japan — `Command_Construct<TowerId>Nanocore`) з `<Object>` на вежу/ядро.
- Набори команд веж: `<TowerId>CommandSet` (вимкнення/продаж/ремонт/стоп + особливі здібності).
- Перевизначити набори команд будівельних майданчиків трьох фракцій (`<!-- VERIFY -->` імен), додавши нові кнопки у вкладку «Оборона».
- Апгрейд `Upgrade_FortressReinforcedPlating` (+25% HP усім вежам мода), купується на будівельному майданчику, $1500.
- Мітки підказок: `TOOLTIP:Construct<TowerId>`.

## Прев'ю (`preview/`)

- `preview/art/<TowerId>.svg`: `viewBox="0 0 240 280"`, прозорий фон, вигляд 3/4 згори (ізометрія, як камера RA3), вежа стоїть на ромбоподібній плиті-фундаменті, низ плити ≈ y=262, центр по x=120. Без зовнішніх ресурсів, шрифтів, `<script>`, `<image>`. Унікальні `id` всередині SVG мають префікс `<TowerId>-` (щоб не конфліктували при вбудовуванні кількох SVG на одну сторінку).
- Палітри: Allies — сталево-синій `#2f6fd6`, білий `#eef3fb`, хром `#9fb3c8`, акцент призми `#7fe3ff`; Soviet — червоний `#c62b1f`, оливкова сталь `#5b5f4a`, іржа `#8a4b2a`, акцент тесли `#6ad0ff`; Empire — білий `#f4f1ec`, багряний `#c8102e`, чорний `#1d1d22`, акцент `#3fe0c5`.

## 3D-моделі (`models/`)

Кожна вежа має власну 3D-модель (не лише перефарбовану ванільну). Джерело — процедурний генератор на Python, результат — glTF 2.0 (`.glb`), який далі конвертується в W3X для гри (Blender + плагін W3D/W3X, див. `scripts/blender_export_w3x.py`).

- Бібліотека: `models/meshkit.py` (лише numpy). Примітиви: `box`, `cylinder`, `cone`, `disc`, `sphere`, `torus`, `lathe`, `extrude`, `ngon`, `crystal`, `tube_between`, `radial`, `merge`, `foundation_plate`; `Model.material(...)`, `Model.node(...)`, `Node.node(...)`, `Node.add(mesh, material)`, `rgb('#hex', k)`.
- Генератор: `models/towers/<TowerId>.py`, запуск `python3 -I models/towers/<TowerId>.py` → пише `models/glb/<TowerId>.glb` (шлях рахувати від розташування скрипта; `sys.path` доповнювати теж від нього).
- Для Імперії додатково `<TowerId>Nanocore.glb` (компактне наноядро-транспорт, ≤ 2500 трикутників) у тому ж скрипті.
- Осі: Y — вгору, 1 одиниця ≈ 1 м. Фундамент — `foundation_plate()` 6×6, верх на `PLATE_TOP`; вся вежа в межах |x|,|z| ≤ 3.
- Орієнтовна висота: Prism 9, Cryo 5, Aegis 7, Tesla 10, Flak 4.5, Inferno 3.5, Tengu 8, Citadel 9, Kitsune 6.
- Бюджет: 3 000–12 000 трикутників на вежу.
- Імена вузлів (стануть кістками W3X): корінь `Base`; те, що обертається по горизонталі, — `Turret`; нахил — `Barrel`/`Emitter`; точки пострілу/FX — `FX_Muzzle01`, `FX_Muzzle02`…, `FX_Glow`. Вузол `FX_*` містить крихітний меш-маркер (куб 0.05) у точці кістки.
- Матеріали: PBR (metallic/roughness), кольори з палітр фракцій вище; світні частини через `emissive=rgb(hex, k)`, k ≤ 2.5.
- Ім'я моделі в грі (W3X-контейнер): `FT` + назва без префікса фракції, напр. `FTPrismSpire`, `FTTenguSpire`, `FTTenguSpireNanocore`.

## Текстури (`models/textures/`)

Стиль — як у Red Alert 3: чисті насичені кольори фракції, чіткі панелі з фасками й темними швами, світлий край зверху (запечене освітлення), легкий бруд і потертості лише по краях, без фотореалізму.

- Бібліотека: `models/texkit.py` (numpy + Pillow): тайлові шуми, панелі, заклепки, смуги, подряпини, normal map з карти висот, `save_set()`, `contact_sheet()`.
- Набори: `models/textures/<set>/`, де `<set>` ∈ `allied`, `soviet`, `empire`; генератор `models/textures/gen_<set>.py` (`python3 -I`, шляхи від `__file__`).
- Кожна текстура — 3 файли 512×512, безшовні: `<name>_dif.png` (колір, sRGB), `<name>_nrm.png` (normal, OpenGL +Y; для гри — інверсія G, `<!-- VERIFY -->`), `<name>_spm.png` (маска RA3: R — блиск, G — самосвітіння, B — маска кольору гравця; `<!-- VERIFY -->` проти ванільних `_SPM`).
- Щільність: 1 тайл = 2 м (2 одиниці моделі).
- Обов'язкові імена:
  - `allied`: `panel_white`, `panel_blue`, `chrome`, `energy_cyan`, `plate_steel`, `dark_metal`
  - `soviet`: `steel_olive`, `paint_red`, `hazard`, `copper_coil`, `concrete`, `sandbag`, `plate_steel`, `dark_metal`, `brass`
  - `empire`: `armor_white`, `lacquer_crimson`, `trim_black`, `energy_teal`, `stone`, `plate_steel`
- Прив'язка до моделей: `models/textures/bindings.json` — `{ "<TowerId>": { "<ім'я матеріалу в моделі>": "<set>/<name>" } }`; `meshkit.save()` читає її, додає box-проєкцію UV (щільність вище) і посилання на текстури (зовнішні `uri` відносно `.glb`: `../textures/<set>/<name>_dif.png`).
- Іконки збудови (камео) — `models/cameos/<TowerId>.png` 256×256 (і `<TowerId>_128.png`), рендер 3D-моделі з рамкою у стилі фракції RA3.
