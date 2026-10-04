using System;
using System.Collections.Generic;
using BepInEx.Configuration;
using DODModAPI;
using UnityEngine;

namespace DigOrDieMobs
{
    internal enum MobRank
    {
        Monster,
        MiniBoss,
        Boss,
    }

    /// <summary>One new monster: its game descriptor plus what the mod needs around it.</summary>
    internal sealed class Mob
    {
        public MobRank Rank = MobRank.Monster;

        // Mini-bosses and the boss: at most one alive, and a pause between appearances.
        public float DefaultCooldownMinutes;
        public ConfigEntry<bool> RareEnabled;
        public ConfigEntry<float> RareCooldownMinutes;
        public float NextAllowedTime;

        // Behaviour of mini-bosses and the boss (see BossBehaviour)
        public Mob[] SummonWave = new Mob[0];      // monsters called in by a summon
        public float[] SummonAtHealth = new float[0]; // summon when health drops below these fractions
        public float SummonEverySeconds;           // or periodically while fighting (0 = never)
        public int MaxMinionsNearby = 6;
        public Mob DeathSplitInto;                  // monsters left behind on death
        public int DeathSplitCount;

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

        public bool IsRare
        {
            get { return Rank != MobRank.Monster; }
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

        // ---------------------------------------------------------------- mini-bosses

        /// <summary>Huge crowned slime. Hits hard and splits into acid slimes when it dies.</summary>
        public static readonly Mob SlimeKing = new Mob(
            "slimeKing", "Slime King", "Король слизней",
            "surface and dirt caves", "поверхность и земляные пещеры",
            new CUnitGround.CDesc(
                tier: 3,
                speed: 2.2f,
                size: new Vector2(2.0f, 1.6f),
                hpMax: 450,
                armor: 4,
                attackDesc: new CAttackDesc(range: 2.2f, damage: 18, nbAttacks: 1, cooldown: 1.4f, knockbackTarget: 14f),
                tiles: MobAssets.slimeKing,
                loot: new[]
                {
                    new CStack(GItems.sulfur, 6, 1f),
                    new CStack(GItems.gold, 5, 0.4f),
                    new CStack(GItems.potionHpRegen, 1, 0.5f),
                })
            {
                m_emitLight = new Color24(40, 130, 60),
            },
            () => AcidSlime.Companions())
        {
            Rank = MobRank.MiniBoss,
            DefaultCooldownMinutes = 10f,
            DeathSplitInto = AcidSlime,
            DeathSplitCount = 4,
        };

        /// <summary>Mother of the crystal spiders: armoured, fast, keeps calling her brood.</summary>
        public static readonly Mob CrystalQueen = new Mob(
            "crystalQueen", "Crystal Queen", "Кристальная королева",
            "deep caves", "глубокие пещеры",
            new CUnitGround.CDesc(
                tier: 3,
                speed: 5f,
                size: new Vector2(2.2f, 1.5f),
                hpMax: 700,
                armor: 12,
                attackDesc: new CAttackDesc(range: 2.4f, damage: 20, nbAttacks: 1, cooldown: 1f, knockbackTarget: 16f),
                tiles: MobAssets.crystalQueen,
                loot: new[]
                {
                    new CStack(GItems.crystal, 10, 1f),
                    new CStack(GItems.crystalLight, 3, 0.6f),
                    new CStack(GItems.crystalBlack, 2, 0.4f),
                    new CStack(GItems.diamonds, 2, 0.2f),
                })
            {
                m_emitLight = new Color24(120, 60, 200),
            },
            () => CrystalSpider.Companions())
        {
            Rank = MobRank.MiniBoss,
            DefaultCooldownMinutes = 12f,
            SummonWave = new[] { CrystalSpider, CrystalSpider },
            SummonEverySeconds = 20f,
            MaxMinionsNearby = 6,
        };

        /// <summary>Horned lord of the lava wisps: big fireballs, calls wisps when wounded.</summary>
        public static readonly Mob PyreLord = new Mob(
            "pyreLord", "Pyre Lord", "Повелитель пламени",
            "lava and the volcano", "лава и вулкан",
            new CUnitBird.CDesc(
                tier: 3,
                speed: 4.5f,
                size: new Vector2(2.0f, 2.0f),
                hpMax: 600,
                armor: 8,
                // the projectile (a vanilla big fireball) is attached in Patches.SUnits_OnInit
                attackDesc: new CAttackDesc(range: 14f, damage: 25, nbAttacks: 3, cooldown: 2.2f, knockbackTarget: 8f,
                                            sound: GameAssets.SoundID.firefly),
                tiles: MobAssets.pyreLord,
                loot: new[]
                {
                    new CStack(GItems.lootLavaBat, 4, 1f),
                    new CStack(GItems.sulfur, 8, 1f),
                    new CStack(GItems.energyGem, 2, 0.6f),
                    new CStack(GItems.lootMiniBalrog, 1, 0.3f),
                })
            {
                m_emitLight = new Color24(255, 90, 20),
                m_immuneToFire = true,
            },
            () => LavaWisp.Companions())
        {
            Rank = MobRank.MiniBoss,
            DefaultCooldownMinutes = 12f,
            SummonWave = new[] { LavaWisp, LavaWisp },
            SummonAtHealth = new[] { 0.66f, 0.33f },
        };

        // ---------------------------------------------------------------------- boss

        /// <summary>
        /// Giant floating eye of the deep. Shoots volleys of energy and, at 75%, 50% and 25% health,
        /// calls crystal spiders and lava wisps to its side.
        /// </summary>
        public static readonly Mob AbyssEye = new Mob(
            "abyssEye", "Eye of the Abyss", "Око Бездны",
            "deep caves and lava", "глубокие пещеры и лава",
            new CUnitBird.CDesc(
                tier: 3,
                speed: 3.8f,
                size: new Vector2(3.0f, 3.0f),
                hpMax: 4000,
                armor: 14,
                // the projectile (vanilla medium particle) is attached in Patches.SUnits_OnInit
                attackDesc: new CAttackDesc(range: 18f, damage: 28, nbAttacks: 5, cooldown: 2.4f, knockbackTarget: 6f,
                                            sound: GameAssets.SoundID.particle),
                tiles: MobAssets.abyssEye,
                loot: new[]
                {
                    new CStack(GItems.darkGem, 5, 1f),
                    new CStack(GItems.energyGem, 4, 1f),
                    new CStack(GItems.diamonds, 6, 0.7f),
                    new CStack(GItems.masterGem, 1, 0.35f),
                })
            {
                m_emitLight = new Color24(170, 60, 255),
                m_immuneToFire = true,
            },
            () => Concat(CrystalSpider.Companions(), LavaWisp.Companions()))
        {
            Rank = MobRank.Boss,
            DefaultCooldownMinutes = 30f,
            SummonWave = new[] { CrystalSpider, CrystalSpider, LavaWisp, LavaWisp },
            SummonAtHealth = new[] { 0.75f, 0.5f, 0.25f },
        };

        public static readonly Mob[] All = { AcidSlime, CrystalSpider, LavaWisp, DeepJelly, SlimeKing, CrystalQueen, PyreLord, AbyssEye };

        private static Dictionary<CUnit.CDesc, Mob> _byDesc;

        /// <summary>Our mob for a unit descriptor, or null.</summary>
        public static Mob ByDesc(CUnit.CDesc desc)
        {
            if (_byDesc == null)
            {
                _byDesc = new Dictionary<CUnit.CDesc, Mob>();
                foreach (Mob mob in All)
                    _byDesc[mob.Desc] = mob;
            }
            Mob found;
            return desc != null && _byDesc.TryGetValue(desc, out found) ? found : null;
        }

        private static CUnit.CDesc[] Concat(CUnit.CDesc[] a, CUnit.CDesc[] b)
        {
            var result = new CUnit.CDesc[a.Length + b.Length];
            a.CopyTo(result, 0);
            b.CopyTo(result, a.Length);
            return result;
        }

        public static int CountAlive(Mob mob)
        {
            int count = 0;
            foreach (CUnit unit in SUnits.Units)
            {
                if (unit != null && unit.m_uDesc == mob.Desc && unit.IsAlive())
                    count++;
            }
            return count;
        }

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
                int weight;
                if (mob.IsRare)
                {
                    // one at a time, and not again before its cooldown has passed
                    if (mob.RareEnabled != null && !mob.RareEnabled.Value)
                        continue;
                    if (Time.realtimeSinceStartup < mob.NextAllowedTime || CountAlive(mob) > 0)
                        continue;
                    weight = 1;
                }
                else
                {
                    weight = mob.SpawnWeight != null ? mob.SpawnWeight.Value : 1;
                }
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
