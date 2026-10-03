DIG OR DIE - НОВІ МОНСТРИ (New Mobs)
====================================

Додає в Dig or Die чотири нових монстри. Вони самі з'являються у світі
там, де живуть схожі звичайні монстри гри:

  Кислотний слизень (Acid Slime)      - поверхня і земляні печери
      повільний, 25 HP, кусає на 5. Випадає: сірка.
  Кристальний паук (Crystal Spider)   - глибокі печери (де мурахи, чорні гончаки)
      дуже швидкий, броньований, 80 HP, кусає на 10. Випадає: кристали, панцир.
  Лавовий дух (Lava Wisp)             - лава і вулкан (де лавові кажани)
      літає, кидає вогняні кулі, не горить, 70 HP. Випадає: здобич лавового кажана, сірка.
  Глибинна медуза (Deep Jelly)        - океани (де риби й акули)
      плаває, світиться, жалить на 9, 55 HP. Випадає: світні водорості.

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
Команда - це чит: досягнення Steam у цьому світі вимикаються.


НАЛАШТУВАННЯ
------------
BepInEx\config\reya.digordie.newmobs.cfg - як часто кожен монстр з'являється:
  0 = ніколи, 1 = як один звичайний монстр цього місця (за замовчуванням), до 5 = частіше.
Менеджер модів (кнопка МОДИ) вмикає/вимикає цей мод однією кнопкою.


ВАЖЛИВО
-------
* У мультиплеєрі мод потрібен усім гравцям.
* Перш ніж видаляти мод, краще позбутися нових монстрів у світі (або не
  завантажувати потім той самий світ без мода).
* Видалити тільки цей мод: Uninstall.bat.


ENGLISH (short)
---------------
Four new Dig or Die monsters (Acid Slime, Crystal Spider, Lava Wisp, Deep Jelly) that spawn
where similar vanilla monsters live. Install: extract, close the game, run Install.bat (it also
downloads the required DODModAPI library from nuget.org), start the game from Steam.
In chat: /mobs lists them, /mobs <name> [count] spawns them next to you (disables achievements).
Requires BepInEx 5 and DODModAPI (https://github.com/ddmitv/dig-or-die-mods).
BepInEx (https://github.com/BepInEx/BepInEx) is included under the LGPL-2.1 license.
