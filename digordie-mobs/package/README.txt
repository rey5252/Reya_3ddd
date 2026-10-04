DIG OR DIE - НОВІ МОНСТРИ (New Mobs)
====================================

Додає в Dig or Die чотири нових монстри, три міні-боси і боса. Вони самі з'являються у світі
там, де живуть схожі звичайні монстри гри:

  Кислотний слизень (Acid Slime)      - поверхня і земляні печери
      повільний, 25 HP, кусає на 5. Випадає: сірка.
  Кристальний паук (Crystal Spider)   - глибокі печери (де мурахи, чорні гончаки)
      дуже швидкий, броньований, 80 HP, кусає на 10. Випадає: кристали, панцир.
  Лавовий дух (Lava Wisp)             - лава і вулкан (де лавові кажани)
      літає, кидає вогняні кулі, не горить, 70 HP. Випадає: здобич лавового кажана, сірка.
  Глибинна медуза (Deep Jelly)        - океани (де риби й акули)
      плаває, світиться, жалить на 9, 55 HP. Випадає: світні водорості.

МІНІ-БОСИ І БОС (з'являються рідко, по одному, з паузою між появами):

  Король слизнів (Slime King)          - міні-бос, поверхня/земляні печери
      450 HP, б'є на 18. Коли гине - розпадається на 4 кислотні слизні.
  Кришталева королева (Crystal Queen)  - міні-бос, глибокі печери
      700 HP, броня 12. Кожні 20 с бою кличе 2 кришталевих павуків.
  Повелитель полум'я (Pyre Lord)       - міні-бос, лава і вулкан
      600 HP, літає, великі вогняні кулі; на 66% і 33% HP кличе лавових духів.
  ОКО БЕЗОДНІ (Eye of the Abyss)       - БОС, глибокі печери і лава
      4000 HP, броня 14, залпи енергії; на 75/50/25% HP кличе павуків і духів.
      Найкраща здобич: темні камені, енергокамені, діаманти, майстер-камінь.

Коли бос поруч - угорі екрана смужка його здоров'я, у чаті - попередження.

Назви монстрів у грі - російською (якщо гра російською) або англійською.


ВСТАНОВЛЕННЯ
------------
1. Повністю розпакуйте архів (ПКМ -> «Видобути все...»).
2. Закрийте гру і двічі клацніть Install.bat.
   Скрипт сам знайде гру, встановить BepInEx (якщо його ще немає), цей мод
   і бібліотеку DODModAPI (завантажить її з nuget.org - потрібен інтернет).
3. Запускайте гру ЧЕРЕЗ STEAM.

Вручну: скопіюйте вміст game-files у папку гри, а dodmodapi.dll
(https://github.com/ddmitv/dig-or-die-mods/releases) покладіть у BepInEx\plugins.


ЯК ПОБАЧИТИ МОНСТРІВ ОДРАЗУ
---------------------------
У грі натисніть Enter (чат) і напишіть:
  /mobs                     - список нових монстрів
  /mobs acidSlime           - поставити слизня поруч із вами
  /mobs crystalSpider 5     - п'ять павуків
  /mobs lavaWisp
  /mobs deepJelly           - медуза (ставте у воді)
  /mobs slimeKing           - міні-боси: slimeKing, crystalQueen, pyreLord
  /mobs abyssEye            - бос Око Безодні
Команда - це чит: досягнення Steam у цьому світі вимикаються.


НАЛАШТУВАННЯ
------------
BepInEx\config\reya.digordie.newmobs.cfg - як часто кожен монстр з'являється:
  0 = ніколи, 1 = як один звичайний монстр цього місця (за замовчуванням), до 5 = частіше.
  Розділ [Bosses]: чи з'являються боси самі (...Enabled) і пауза між появами в хвилинах
  (...CooldownMinutes: 10 / 12 / 12 / 30 за замовчуванням).
Менеджер модів (кнопка МОДИ) вмикає/вимикає цей мод однією кнопкою.


ВАЖЛИВО
-------
* У мультиплеєрі мод потрібен усім гравцям.
* Перш ніж видаляти мод, краще позбутися нових монстрів у світі (або не
  завантажувати потім той самий світ без мода).
* Видалити тільки цей мод: Uninstall.bat.


ENGLISH (short)
---------------
Four new Dig or Die monsters (Acid Slime, Crystal Spider, Lava Wisp, Deep Jelly), three mini-bosses
(Slime King, Crystal Queen, Pyre Lord) and a boss (Eye of the Abyss) that spawn where similar vanilla
monsters live; bosses are rare, one at a time, with a boss health bar. Install: extract, close the game, run Install.bat (it also
downloads the required DODModAPI library from nuget.org), start the game from Steam.
In chat: /mobs lists them, /mobs <name> [count] spawns them next to you (disables achievements).
Requires BepInEx 5 and DODModAPI (https://github.com/ddmitv/dig-or-die-mods).
BepInEx (https://github.com/BepInEx/BepInEx) is included under the LGPL-2.1 license.
