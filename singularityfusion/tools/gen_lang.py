"""Writes the mod's three languages from one table, so none of them misses a key.

    python3 tools/gen_lang.py
"""
import json
import os

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
LANG = os.path.join(ROOT, "src", "main", "resources", "assets", "singularityfusion", "lang")

P = "singularityfusion"

# key: (English, Ukrainian, Russian)
T = {
    "itemGroup.%s" % P: ("Singularity Fusion", "Синтез сингулярності", "Синтез сингулярности"),
    "block.%s.void_casing" % P: ("Void Casing", "Корпус порожнечі", "Корпус пустоты"),
    "block.%s.void_casing_rune" % P: ("Runed Void Casing", "Руновий корпус порожнечі", "Рунный корпус пустоты"),
    "block.%s.void_casing_seam" % P: ("Seamed Void Casing", "Прошитий корпус порожнечі", "Прошитый корпус пустоты"),
    "block.%s.fusion_core" % P: ("Singularity Fusion Core", "Ядро синтезу сингулярності", "Ядро синтеза сингулярности"),
    "block.%s.graviton_pylon" % P: ("Graviton Pylon", "Гравітонний пілон", "Гравитонный пилон"),
    "block.%s.creative_cell" % P: ("Creative Energy Cell", "Творча енергокомірка", "Творческая энергоячейка"),
    "item.%s.graviton_crystal" % P: ("Graviton Crystal", "Гравітонний кристал", "Гравитонный кристалл"),
    "item.%s.singularity_shard" % P: ("Singularity Shard", "Уламок сингулярності", "Осколок сингулярности"),
    "item.%s.collapsed_star" % P: ("Collapsed Star", "Колапсована зоря", "Коллапсировавшая звезда"),
    "item.%s.event_horizon_core" % P: ("Event Horizon Core", "Ядро горизонту подій", "Ядро горизонта событий"),

    # ------------------------------------------------------------------ tooltips (lines .1, .2, ...)
    "tooltip.%s.fusion_core.1" % P: (
        "The heart of the structure: stands on a 3×3 floor of void casing, with 7 blocks of air above it.",
        "Серце структури: стоїть на підлозі 3×3 з корпусу порожнечі, над ним 7 блоків повітря.",
        "Сердце структуры: стоит на полу 3×3 из корпуса пустоты, над ним 7 блоков воздуха."),
    "tooltip.%s.fusion_core.2" % P: (
        "Graviton pylons round it (up to 6 blocks away) hold the ingredients; the catalyst goes into the core.",
        "Гравітонні пілони навколо (до 6 блоків) тримають інгредієнти; каталізатор кладеться в ядро.",
        "Гравитонные пилоны вокруг (до 6 блоков) держат ингредиенты; катализатор кладётся в ядро."),
    "tooltip.%s.fusion_core.3" % P: (
        "Takes energy (RF/FE/OP) from any side: the black hole over it grows as it fills.",
        "Приймає енергію (RF/FE/OP) з будь-якого боку: чорна діра над ним росте, як воно наповнюється.",
        "Принимает энергию (RF/FE/OP) с любой стороны: чёрная дыра над ним растёт по мере наполнения."),
    "tooltip.%s.graviton_pylon.1" % P: (
        "Holds one ingredient for a fusion core near it: use it with an item, with an empty hand to take it back.",
        "Тримає один інгредієнт для ядра синтезу поруч: клацни предметом, порожньою рукою — забрати.",
        "Держит один ингредиент для ядра синтеза рядом: кликни предметом, пустой рукой — забрать."),
    "tooltip.%s.graviton_pylon.2" % P: (
        "Turns toward the singularity, and pours its item into it in a fusion.",
        "Повертається до сингулярності й під час синтезу вливає в неї свій предмет.",
        "Поворачивается к сингулярности и во время синтеза вливает в неё свой предмет."),
    "tooltip.%s.void_casing.1" % P: ("A fusion core's foundation (any of the three casings will do).",
                                     "Основа для ядра синтезу (годиться будь-який із трьох корпусів).",
                                     "Основание для ядра синтеза (годится любой из трёх корпусов)."),
    "tooltip.%s.void_casing_rune.1" % P: ("A fusion core's foundation, a rune pulsing in it.",
                                          "Основа для ядра синтезу, у ній пульсує руна.",
                                          "Основание для ядра синтеза, в нём пульсирует руна."),
    "tooltip.%s.void_casing_seam.1" % P: ("A fusion core's foundation, light running along its seams.",
                                          "Основа для ядра синтезу, світло біжить її швами.",
                                          "Основание для ядра синтеза, свет бежит по его швам."),
    "tooltip.%s.creative_cell.1" % P: ("Endless energy: pushes all it can into every block beside it.",
                                       "Нескінченна енергія: штовхає скільки може в кожен сусідній блок.",
                                       "Бесконечная энергия: толкает сколько может в каждый соседний блок."),
    "tooltip.%s.graviton_crystal.1" % P: ("It bends gravity. Pylons and cores are built of it.",
                                          "Викривлює гравітацію. З нього будують пілони та ядра.",
                                          "Искривляет гравитацию. Из него строят пилоны и ядра."),
    "tooltip.%s.singularity_shard.1" % P: ("A splinter of a singularity.", "Скалка сингулярності.", "Частица сингулярности."),
    "tooltip.%s.collapsed_star.1" % P: ("A star crushed to a point, its light still turning round it.",
                                        "Зоря, стиснута в точку; її світло досі кружляє навколо.",
                                        "Звезда, сжатая в точку; её свет до сих пор кружит вокруг."),
    "tooltip.%s.event_horizon_core.1" % P: ("The heart of a black hole, held in the hand. Beyond chaos.",
                                            "Серце чорної діри в долоні. Понад хаос.",
                                            "Сердце чёрной дыры в ладони. За гранью хаоса."),

    # ------------------------------------------------------------------ the core's screen
    "gui.%s.status.no_foundation" % P: ("Needs a 3×3 void casing floor under it", "Потрібна підлога 3×3 з корпусу порожнечі",
                                        "Нужен пол 3×3 из корпуса пустоты"),
    "gui.%s.status.blocked" % P: ("The space above it is blocked", "Простір над ядром зайнятий", "Пространство над ядром занято"),
    "gui.%s.status.no_pylons" % P: ("No graviton pylons near it", "Поруч немає гравітонних пілонів", "Рядом нет гравитонных пилонов"),
    "gui.%s.status.no_recipe" % P: ("Nothing to fuse", "Нічого синтезувати", "Нечего синтезировать"),
    "gui.%s.status.output_full" % P: ("The output is full", "Вихід заповнений", "Выход заполнен"),
    "gui.%s.status.no_energy" % P: ("Not enough energy", "Недостатньо енергії", "Недостаточно энергии"),
    "gui.%s.status.ready" % P: ("Ready to fuse", "Готове до синтезу", "Готово к синтезу"),
    "gui.%s.status.fusing" % P: ("Fusing…", "Синтез…", "Синтез…"),
    "gui.%s.making" % P: ("%s — %s%%", "%s — %s%%", "%s — %s%%"),
    "gui.%s.cost" % P: ("Energy cost: %s OP", "Затрати енергії: %s OP", "Затраты энергии: %s OP"),
    "gui.%s.stored" % P: ("Stored: %s OP", "Накопичено: %s OP", "Накоплено: %s OP"),
    "gui.%s.energy" % P: ("Energy: %s / %s OP", "Енергія: %s / %s OP", "Энергия: %s / %s OP"),
    "gui.%s.charge" % P: ("Singularity: %s%%", "Сингулярність: %s%%", "Сингулярность: %s%%"),
    "gui.%s.progress" % P: ("Fusion: %s%%", "Синтез: %s%%", "Синтез: %s%%"),
    "gui.%s.idle" % P: ("No fusion under way", "Синтез не йде", "Синтез не идёт"),
    "gui.%s.time" % P: ("Takes %s s", "Триває %s с", "Длится %s с"),
    "gui.%s.start" % P: ("Start the fusion", "Почати синтез", "Начать синтез"),
    "gui.%s.start.redstone" % P: ("A redstone pulse into the core starts it too", "Його також запускає імпульс редстоуну в ядро",
                                  "Его также запускает импульс редстоуна в ядро"),
    "gui.%s.singularity" % P: ("Singularity: %s%% formed", "Сингулярність сформована на %s%%", "Сингулярность сформирована на %s%%"),
    "gui.%s.unformed" % P: ("The structure isn't whole: no singularity forms", "Структура неповна: сингулярність не формується",
                            "Структура неполная: сингулярность не формируется"),
    "gui.%s.pylon.none" % P: ("No pylon here", "Тут немає пілона", "Здесь нет пилона"),
    "gui.%s.pylon.empty" % P: ("An empty pylon", "Порожній пілон", "Пустой пилон"),

    # ------------------------------------------------------------------ JEI
    "jei.%s.fusion" % P: ("Singularity Fusion", "Синтез сингулярності", "Синтез сингулярности"),
    "jei.%s.energy" % P: ("%s OP", "%s OP", "%s OP"),
    "jei.%s.time" % P: ("%s s", "%s с", "%s с"),
    "jei.%s.catalyst" % P: ("Catalyst: goes into the core", "Каталізатор: кладеться в ядро", "Катализатор: кладётся в ядро"),
    "jei.%s.ingredient" % P: ("On a graviton pylon", "На гравітонний пілон", "На гравитонный пилон"),
}


def main():
    os.makedirs(LANG, exist_ok=True)
    for i, code in enumerate(("en_us", "uk_ua", "ru_ru")):
        with open(os.path.join(LANG, code + ".json"), "w", encoding="utf-8") as f:
            json.dump({k: v[i] for k, v in T.items()}, f, ensure_ascii=False, indent=2)
            f.write("\n")
    print("wrote %d keys in 3 languages" % len(T))


if __name__ == "__main__":
    main()
