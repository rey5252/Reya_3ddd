using System;
using System.Collections.Generic;
using BepInEx.Configuration;
using DODModAPI;
using UnityEngine;

namespace DigOrDieMobs
{
    /// <summary>One new monster: its game descriptor plus what the mod needs around it.</summary>
    internal sealed class Mob
    {
        public readonly string CodeName;
        public readonly string NameEnglish;
        public readonly string NameRussian;
        public readonly string HabitatEnglish;
        public readonly string HabitatRussian;
        public readonly ModUnit Unit;

        /// <summary>
        /// Vanilla monsters this one lives with: wherever the game would spawn one of them,
        /// this monster can spawn too. Read lazily because GUnits is filled when the game starts.
        /// </summary>
        public readonly Func<CUnit.CDesc[]> Companions;

        public ConfigEntry<int> SpawnWeight;

        public Mob(string codeName, string nameEnglish, string nameRussian, string habitatEnglish, string habitatRussian,
                   CUnitMonster.CDesc desc, Func<CUnit.CDesc[]> companions)
        {
            CodeName = codeName;
            NameEnglish = nameEnglish;
            NameRussian = nameRussian;
            HabitatEnglish = habitatEnglish;
            HabitatRussian = habitatRussian;
            Companions = companions;
            Unit = new ModUnit(codeName, nameEnglish, desc);
        }

        public CUnitMonster.CDesc Desc
        {
            get { return (CUnitMonster.CDesc)Unit.UnitDesc; }
        }

        public string LocId
        {
            get { return "U_" + CodeName; } // see CUnit.CDesc.GetName()
        }
    }

    internal static class Mobs
    {
        /// <summary>Slow acid blob of the surface and the dirt layer. Easy, but comes in numbers.</summary>
        public static readonly Mob AcidSlime = new Mob(
            "acidSlime", "Acid Slime", "Кислотный слизень",
            "surface and dirt caves", "поверхность и земляные пещеры",
            new CUnitGround.CDesc(
                tier: 1,
                speed: 2.5f,
                size: new Vector2(1.0f, 0.8f),
                hpMax: 25,
                armor: 0,
                attackDesc: new CAttackDesc(range: 1.4f, damage: 5, nbAttacks: 1, cooldown: 1.2f, knockbackTarget: 6f),
                tiles: MobAssets.acidSlime,
                loot: new[] { new CStack(GItems.sulfur, 1, 0.4f) })
            {
                m_emitLight = new Color24(30, 90, 20),
            },
            () => new CUnit.CDesc[] { GUnits.hound, GUnits.dweller });

        /// <summary>Fast armoured spider of the deep caves, crystals grow on its back.</summary>
        public static readonly Mob CrystalSpider = new Mob(
            "crystalSpider", "Crystal Spider", "Кристальный паук",
            "deep caves", "глубокие пещеры",
            new CUnitGround.CDesc(
                tier: 2,
                speed: 7f,
                size: new Vector2(1.3f, 0.9f),
                hpMax: 80,
                armor: 6,
                attackDesc: new CAttackDesc(range: 1.8f, damage: 10, nbAttacks: 1, cooldown: 0.8f, knockbackTarget: 8f),
                tiles: MobAssets.crystalSpider,
                loot: new[]
                {
                    new CStack(GItems.crystal, 2, 0.6f),
                    new CStack(GItems.antShell, 1, 0.25f),
                    new CStack(GItems.crystalLight, 1, 0.1f),
                })
            {
                m_emitLight = new Color24(20, 110, 130),
            },
            () => new CUnit.CDesc[] { GUnits.antClose, GUnits.antDist, GUnits.houndBlack, GUnits.dwellerBlack });

        /// <summary>Flying ball of fire near lava. Keeps its distance and throws fireballs; fire does not hurt it.</summary>
        public static readonly Mob LavaWisp = new Mob(
            "lavaWisp", "Lava Wisp", "Лавовый дух",
            "lava and the volcano", "лава и вулкан",
            new CUnitBird.CDesc(
                tier: 3,
                speed: 5f,
                size: new Vector2(1.0f, 1.0f),
                hpMax: 70,
                armor: 3,
                // the projectile (a vanilla small fireball) is attached in Patches.SUnits_OnInit
                attackDesc: new CAttackDesc(range: 12f, damage: 12, nbAttacks: 3, cooldown: 2.5f, knockbackTarget: 4f,
                                            sound: GameAssets.SoundID.firefly),
                tiles: MobAssets.lavaWisp,
                loot: new[]
                {
                    new CStack(GItems.lootLavaBat, 1, 0.35f),
                    new CStack(GItems.sulfur, 2, 0.3f),
                    new CStack(GItems.energyGem, 1, 0.08f),
                })
            {
                m_emitLight = new Color24(255, 120, 30),
                m_immuneToFire = true,
            },
            () => new CUnit.CDesc[] { GUnits.lavaBat, GUnits.lavaAnt, GUnits.balrogMini });

        /// <summary>Glowing jellyfish of the oceans. Stings anything that swims too close.</summary>
        public static readonly Mob DeepJelly = new Mob(
            "deepJelly", "Deep Jelly", "Глубинная медуза",
            "oceans", "океаны",
            new CUnitFish.CDesc(
                tier: 2,
                speed: 3.5f,
                size: new Vector2(1.0f, 1.0f),
                hpMax: 55,
                armor: 2,
                attackDesc: new CAttackDesc(range: 1.6f, damage: 9, nbAttacks: 1, cooldown: 1f, knockbackTarget: 4f),
                tiles: MobAssets.deepJelly,
                loot: new[]
                {
                    new CStack(GItems.waterLight, 1, 0.5f),
                    new CStack(GItems.fish2Regen, 1, 0.2f),
                    new CStack(GItems.sapphire, 1, 0.05f),
                })
            {
                m_emitLight = new Color24(40, 150, 255),
            },
            () => new CUnit.CDesc[] { GUnits.fish, GUnits.fishBlack, GUnits.shark });

        public static readonly Mob[] All = { AcidSlime, CrystalSpider, LavaWisp, DeepJelly };

        public static Mob Find(string text)
        {
            foreach (Mob mob in All)
            {
                if (string.Equals(mob.CodeName, text, StringComparison.OrdinalIgnoreCase))
                    return mob;
            }
            return null;
        }

        /// <summary>
        /// Adds the new monsters to a vanilla spawn list: each one joins the list when one of its
        /// companions is in it, SpawnWeight times (0 = never spawns naturally).
        /// </summary>
        public static CUnitMonster.CDesc[] AddToSpawnList(CUnitMonster.CDesc[] list)
        {
            if (list == null || list.Length == 0)
                return list;

            List<CUnitMonster.CDesc> result = null;
            foreach (Mob mob in All)
            {
                int weight = mob.SpawnWeight != null ? mob.SpawnWeight.Value : 1;
                if (weight <= 0 || Array.IndexOf(list, mob.Desc) >= 0 || !HasCompanion(list, mob))
                    continue;
                if (result == null)
                    result = new List<CUnitMonster.CDesc>(list);
                for (int i = 0; i < weight; i++)
                    result.Add(mob.Desc);
            }
            return result != null ? result.ToArray() : list;
        }

        private static bool HasCompanion(CUnitMonster.CDesc[] list, Mob mob)
        {
            foreach (CUnit.CDesc companion in mob.Companions())
            {
                if (companion != null && Array.IndexOf(list, companion) >= 0)
                    return true;
            }
            return false;
        }
    }
}
