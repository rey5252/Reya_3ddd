using DODModAPI;
using UnityEngine;

namespace DigOrDieMobs
{
    /// <summary>An item of the mod with its English and Russian name/description.</summary>
    internal sealed class ModItemInfo
    {
        public readonly ModItem Mod;
        public readonly string NameEnglish, DescEnglish, NameRussian, DescRussian;
        public readonly int TestAmount; // how many /mobs items gives

        public ModItemInfo(string codeName, string nameEnglish, string descEnglish, string nameRussian, string descRussian,
                           CItem item, ModItem.ItemRecipe recipe, int testAmount)
        {
            NameEnglish = nameEnglish;
            DescEnglish = descEnglish;
            NameRussian = nameRussian;
            DescRussian = descRussian;
            TestAmount = testAmount;
            Mod = new ModItem(codeName, nameEnglish, descEnglish, item, recipe);
        }

        public CItem Item
        {
            get { return Mod.item; }
        }

        public void ApplyLanguage(bool russian)
        {
            Item.m_name = russian ? NameRussian : NameEnglish;
            Item.m_desc = russian ? DescRussian : DescEnglish;
        }
    }

    /// <summary>
    /// New items, made from what the new monsters and bosses drop:
    /// crafting materials, four weapons and three devices.
    /// </summary>
    internal static class Items
    {
        private static ModItem.ItemRecipe Recipe(string group, bool upgrade, int nbOut,
                                                 CItem in1, int nb1, CItem in2 = null, int nb2 = 0, CItem in3 = null, int nb3 = 0)
        {
            var recipe = new ModItem.ItemRecipe(group, upgrade) { nbOut = nbOut };
            recipe.in1 = new ModItem.Ingredient(in1, nb1);
            if (in2 != null)
                recipe.in2 = new ModItem.Ingredient(in2, nb2);
            if (in3 != null)
                recipe.in3 = new ModItem.Ingredient(in3, nb3);
            return recipe;
        }

        private static CItem_Material Material(ModTile tile)
        {
            return new CItem_Material(tile, null, 0, tile.MainColor);
        }

        // ------------------------------------------------------------- materials (monster loot)

        public static readonly ModItemInfo AcidGland = new ModItemInfo("acidGland",
            "Acid Gland", "A sac of glowing acid from slimes. Used for acid weapons and potions.",
            "Кислотная железа", "Мешочек светящейся кислоты из слизней. Нужен для кислотного оружия и зелий.",
            Material(MobAssets.acidGland), null, 20);

        public static readonly ModItemInfo CrystalHeart = new ModItemInfo("crystalHeart",
            "Crystal Heart", "The living heart of the Crystal Queen.",
            "Кристальное сердце", "Живое сердце Кристальной королевы.",
            Material(MobAssets.crystalHeart), null, 5);

        public static readonly ModItemInfo PyreCore = new ModItemInfo("pyreCore",
            "Pyre Core", "A never-cooling ember in obsidian claws. Dropped by the Pyre Lord, rarely by Lava Wisps.",
            "Ядро пламени", "Неостывающий уголь в обсидиановых когтях. Выпадает из Повелителя пламени, редко из лавовых духов.",
            Material(MobAssets.pyreCore), null, 5);

        public static readonly ModItemInfo AbyssTear = new ModItemInfo("abyssTear",
            "Abyss Tear", "A tear of the Eye of the Abyss. It is still looking at you.",
            "Слеза Бездны", "Слеза Ока Бездны. Она всё ещё смотрит на вас.",
            Material(MobAssets.abyssTear), null, 5);

        public static readonly ModItemInfo JellyGoo = new ModItemInfo("jellyGoo",
            "Jelly Goo", "Glowing goo of a deep jelly.",
            "Медузий гель", "Светящийся гель глубинной медузы.",
            Material(MobAssets.jellyGoo), null, 10);

        // ----------------------------------------------------------------------------- weapons

        public static readonly ModItemInfo AcidBlaster = new ModItemInfo("gunAcidBlaster",
            "Acid Blaster", "Automatic gun that fires blobs of slime acid.",
            "Кислотный бластер", "Автоматическое оружие, стреляет сгустками кислоты слизней.",
            new CItem_Weapon(MobAssets.gunAcidBlaster_tile, MobAssets.gunAcidBlaster_icon, heatingPerShot: 0.05f, isAuto: true,
                attackDesc: new CAttackDesc(range: 18f, damage: 9, nbAttacks: 1, cooldown: 0.12f, knockbackOwn: 0.5f, knockbackTarget: 3f,
                    projDesc: new ModBulletDesc(MobAssets.projAcidBolt, radius: 0.25f, dispersionAngleRad: 0.06f,
                                                speedStart: 28f, speedEnd: 22f, light: new Color24(60, 160, 30)),
                    sound: GameAssets.SoundID.plasma)),
            Recipe("MK II", false, 1, AcidGland.Item, 6, GItems.iron, 10, GItems.copper, 6), 1);

        public static readonly ModItemInfo CrystalShotgun = new ModItemInfo("gunCrystalShotgun",
            "Crystal Shotgun", "Fires 7 crystal shards that pierce armor.",
            "Кристальный дробовик", "Стреляет 7 кристальными осколками, которые пробивают броню.",
            new CItem_Weapon(MobAssets.gunCrystalShotgun_tile, MobAssets.gunCrystalShotgun_icon, heatingPerShot: 0.35f, isAuto: false,
                attackDesc: new CAttackDesc(range: 16f, damage: 10, nbAttacks: 7, cooldown: 0.6f, knockbackOwn: 8f, knockbackTarget: 6f,
                    projDesc: new ModBulletDesc(MobAssets.projCrystalShard, radius: 0.2f, dispersionAngleRad: 0.45f,
                                                speedStart: 34f, speedEnd: 26f, light: new Color24(110, 60, 200))
                    {
                        m_pierceArmor = true,
                        m_hasTrail = true,
                    },
                    sound: GameAssets.SoundID.shotgun)),
            Recipe("MK III", true, 1, GItems.gunShotgun, 1, CrystalHeart.Item, 1, GItems.crystal, 15), 1);

        public static readonly ModItemInfo PyreLauncher = new ModItemInfo("gunPyreLauncher",
            "Pyre Launcher", "Launches fireballs that set enemies on fire.",
            "Пламенная пушка", "Метает огненные шары, которые поджигают врагов.",
            new CItem_Weapon(MobAssets.gunPyreLauncher_tile, MobAssets.gunPyreLauncher_icon, heatingPerShot: 0.5f, isAuto: false,
                attackDesc: new CAttackDesc(range: 22f, damage: 70, nbAttacks: 1, cooldown: 0.9f, knockbackOwn: 6f, knockbackTarget: 20f,
                    projDesc: new ModBulletDesc(MobAssets.projPyreBall, radius: 0.45f, dispersionAngleRad: 0.02f,
                                                speedStart: 24f, speedEnd: 20f, light: new Color24(255, 120, 30))
                    {
                        m_inflame = true,
                        m_hasSmoke = true,
                    },
                    sound: GameAssets.SoundID.rocketFire)),
            Recipe("MK IV", true, 1, GItems.gunRocket, 1, PyreCore.Item, 3, GItems.sulfur, 20), 1);

        public static readonly ModItemInfo AbyssScepter = new ModItemInfo("gunAbyssScepter",
            "Abyss Scepter", "Fires beams of the abyss that pass through every enemy in their way.",
            "Скипетр Бездны", "Стреляет лучами Бездны, которые пронзают всех врагов на пути.",
            new CItem_Weapon(MobAssets.gunAbyssScepter_tile, MobAssets.gunAbyssScepter_icon, heatingPerShot: 0.1f, isAuto: true,
                attackDesc: new CAttackDesc(range: 30f, damage: 45, nbAttacks: 1, cooldown: 0.2f, knockbackOwn: 1f, knockbackTarget: 5f,
                    projDesc: new ModBulletDesc(MobAssets.projAbyssBolt, radius: 0.3f, dispersionAngleRad: 0.01f,
                                                speedStart: 45f, speedEnd: 40f, light: new Color24(170, 60, 255))
                    {
                        m_goThroughEnnemies = true,
                        m_pierceArmor = true,
                        m_hasTrail = true,
                    },
                    sound: GameAssets.SoundID.particle)),
            Recipe("MK V", false, 1, AbyssTear.Item, 3, GItems.darkGem, 5, GItems.energyGem, 5), 1);

        // ----------------------------------------------------------------------------- devices

        public static readonly ModItemInfo RoyalJellyPotion = new ModItemInfo("potionRoyalJelly",
            "Royal Jelly Potion", "Strong health regeneration for 45 seconds.",
            "Королевское зелье", "Сильная регенерация здоровья на 45 секунд.",
            new CItem_Device(MobAssets.potionRoyalJelly, null, DeviceGroupIds.potionHPRegen, CItem_Device.Type.Consumable, 2f)
            {
                m_cooldown = 90f,
                m_duration = 45f,
            },
            Recipe("MK III", false, 2, AcidGland.Item, 3, JellyGoo.Item, 2, GItems.flowerBlue, 2), 5);

        public static readonly ModItemInfo CrystalShield = new ModItemInfo("crystalShield",
            "Crystal Shield", "A crystal barrier that absorbs damage. Stronger than the Defense Shield.",
            "Кристальный щит", "Кристальный барьер, поглощающий урон. Сильнее обычного защитного щита.",
            new CItem_Device(MobAssets.crystalShield, null, DeviceGroupIds.shield, CItem_Device.Type.Passive, 1.25f),
            Recipe("MK IV", true, 1, GItems.defenseShield, 1, CrystalHeart.Item, 2, GItems.diamonds, 2), 1);

        public static readonly ModItemInfo JellyLamp = new ModItemInfo("jellyLamp",
            "Jelly Lamp", "A living jellyfish in a lantern: bright light around you.",
            "Медузий фонарь", "Живая медуза в фонаре: яркий свет вокруг вас.",
            new CItem_Device(MobAssets.jellyLamp, null, DeviceGroupIds.flashLight, CItem_Device.Type.Passive, 9f),
            Recipe("MK II", true, 1, GItems.flashLight, 1, JellyGoo.Item, 4, GItems.copper, 4), 1);

        public static readonly ModItemInfo[] All =
        {
            AcidGland, CrystalHeart, PyreCore, AbyssTear, JellyGoo,
            AcidBlaster, CrystalShotgun, PyreLauncher, AbyssScepter,
            RoyalJellyPotion, CrystalShield, JellyLamp,
        };

        /// <summary>Set by Patches once DODModAPI has added the items to the game.</summary>
        public static bool Registered;

        public static void ApplyLanguage(bool russian)
        {
            foreach (ModItemInfo info in All)
                info.ApplyLanguage(russian);
        }
    }
}
