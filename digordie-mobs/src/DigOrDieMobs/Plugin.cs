using System;
using System.Collections.Generic;
using System.Linq;
using System.Reflection;
using BepInEx;
using BepInEx.Logging;
using DODModAPI;
using HarmonyLib;
using UnityEngine;

namespace DigOrDieMobs
{
    [BepInPlugin(Guid, PluginName, PluginVersion)]
    [BepInDependency(DODModAPIPlugin.GUID)]
    public sealed class NewMobsPlugin : BaseUnityPlugin
    {
        public const string Guid = "reya.digordie.newmobs";
        public const string PluginName = "New Mobs";
        public const string PluginVersion = "1.1.0";

        internal static ManualLogSource Log;

        private readonly BossBar _bossBar = new BossBar();

        private void Awake()
        {
            Log = Logger;

            foreach (Mob mob in Mobs.All)
            {
                if (mob.IsRare)
                {
                    string kind = mob.Rank == MobRank.Boss ? "boss" : "mini-boss";
                    mob.RareEnabled = Config.Bind("Bosses", mob.CodeName + "Enabled", true,
                        "Whether the " + kind + " " + mob.NameEnglish + " appears on its own (" + mob.HabitatEnglish + "). /mobs can always spawn it.");
                    mob.RareCooldownMinutes = Config.Bind("Bosses", mob.CodeName + "CooldownMinutes", mob.DefaultCooldownMinutes, new BepInEx.Configuration.ConfigDescription(
                        "Minutes of play before " + mob.NameEnglish + " can appear (again). Only one can be alive at a time.",
                        new BepInEx.Configuration.AcceptableValueRange<float>(0f, 600f)));
                    mob.NextAllowedTime = Time.realtimeSinceStartup + BossBehaviour.CooldownSeconds(mob);
                }
                else
                {
                    mob.SpawnWeight = Config.Bind("Spawn", mob.CodeName, 1, new BepInEx.Configuration.ConfigDescription(
                        "How often " + mob.NameEnglish + " spawns in its habitat (" + mob.HabitatEnglish + "): " +
                        "0 = never, 1 = like one vanilla monster of that place, 2-5 = more often.",
                        new BepInEx.Configuration.AcceptableValueRange<int>(0, 5)));
                }
            }

            // Sprites: the atlases are embedded in this dll and loaded by DODModAPI when the game asks for them.
            TextureManager.RegisterTexture(MobAssets.UnitsAtlasResource_128);
            TextureManager.RegisterTexture(MobAssets.UnitsAtlasResource_256);
            foreach (Mob mob in Mobs.All)
                UnitManager.RegisterUnit(mob.Unit);

            var harmony = new Harmony(Guid);
            Patch(harmony, typeof(Patches));
            Patch(harmony, typeof(SpawnPatch));
            Patch(harmony, typeof(BossBehaviour));
            RegisterCommand();

            Log.LogInfo("New Mobs: " + string.Join(", ", Mobs.All.Select(m => m.NameEnglish).ToArray()));
        }

        private void Update()
        {
            _bossBar.Update();
        }

        private void OnGUI()
        {
            _bossBar.OnGUI();
        }

        private static void Patch(Harmony harmony, Type patches)
        {
            try
            {
                harmony.PatchAll(patches);
            }
            catch (Exception e)
            {
                Log.LogError("Failed to apply " + patches.Name + " (unsupported game version?): " + e);
            }
        }

        /// <summary>/mobs lists the new monsters, /mobs &lt;name&gt; [count] spawns them next to you.</summary>
        private static void RegisterCommand()
        {
            var options = new CommandManager.CommandOptions
            {
                Local = true,
                DisableAchievements = true,
                TabCompleter = argIndex => argIndex == 0 ? Mobs.All.Select(m => m.CodeName).ToList() : null,
            };
            CommandManager.Register("/mobs", options, args =>
            {
                if (!args.HasNext)
                {
                    foreach (Mob mob in Mobs.All)
                        Misc.SendChatMessageLocal(LocalName(mob) + RankTag(mob) + " — /mobs " + mob.CodeName + " (" + Habitat(mob) + ")");
                    return;
                }

                string name = args.ArgString("monster");
                Mob target = Mobs.Find(name);
                if (target == null)
                    throw new CommandException("Unknown monster \"" + name + "\". Use: " + string.Join(", ", Mobs.All.Select(m => m.CodeName).ToArray()), args.Index - 1);
                int count = args.HasNext ? Mathf.Clamp(args.ArgInt("count"), 1, 20) : 1;
                args.ArgNone();

                CUnitPlayer player = args.PlayerSender.m_unitPlayer;
                for (int i = 0; i < count; i++)
                {
                    Vector2 offset = new Vector2(3f + (i % 5) * 1.5f, 1.5f + (i / 5) * 1.5f);
                    SUnits.SpawnUnit(target.Desc, player.Pos + offset);
                }
                Misc.SendChatMessageLocal(LocalName(target) + " x" + count);
            });
        }

        internal static bool IsGameRussian()
        {
            try
            {
                return IsRussian(SLoc.GetLanguage());
            }
            catch (Exception)
            {
                return false;
            }
        }

        internal static bool IsRussian(string language)
        {
            language = (language ?? string.Empty).ToLowerInvariant();
            return language.Contains("rus") || language.Contains("рус") || language == "ru";
        }

        internal static string LocalName(Mob mob)
        {
            return IsGameRussian() ? mob.NameRussian : mob.NameEnglish;
        }

        private static string Habitat(Mob mob)
        {
            return IsGameRussian() ? mob.HabitatRussian : mob.HabitatEnglish;
        }

        private static string RankTag(Mob mob)
        {
            bool russian = IsGameRussian();
            switch (mob.Rank)
            {
                case MobRank.Boss: return russian ? " [БОСС]" : " [BOSS]";
                case MobRank.MiniBoss: return russian ? " [мини-босс]" : " [mini-boss]";
                default: return string.Empty;
            }
        }
    }

    internal static class Patches
    {
        /// <summary>Ranged mobs throw the game's own projectiles (they exist once the game has started).</summary>
        [HarmonyPrefix]
        [HarmonyPatch(typeof(SUnits), nameof(SUnits.OnInit))]
        private static void SUnits_OnInit()
        {
            SetBullet(Mobs.LavaWisp, GBullets.fireballSmall);
            SetBullet(Mobs.PyreLord, GBullets.fireballBig);
            SetBullet(Mobs.AbyssEye, GBullets.particleMedium);
        }

        private static void SetBullet(Mob mob, CBulletDesc bullet)
        {
            if (mob.Desc.m_attackDesc.m_bulletDesc == null)
                mob.Desc.m_attackDesc.m_bulletDesc = bullet;
        }

        /// <summary>Monster names in the game's language (the game font has Russian, not Ukrainian, letters).</summary>
        [HarmonyPostfix]
        [HarmonyPatch(typeof(SLoc), nameof(SLoc.LoadLanguage))]
        private static void SLoc_LoadLanguage(SLoc __instance, string language)
        {
            try
            {
                bool russian = NewMobsPlugin.IsRussian(language);
                foreach (Mob mob in Mobs.All)
                    __instance.m_dico[mob.LocId] = new SLoc.CSentence(mob.LocId, russian ? mob.NameRussian : mob.NameEnglish);
            }
            catch (Exception e)
            {
                NewMobsPlugin.Log.LogWarning("Could not set monster names: " + e.Message);
            }
        }
    }

    /// <summary>
    /// Natural spawning: every game mode answers "which monsters can spawn here?" through
    /// GetMonstersList. The new monsters are added to the answer where their companions live.
    /// </summary>
    [HarmonyPatch]
    internal static class SpawnPatch
    {
        private static IEnumerable<MethodBase> TargetMethods()
        {
            foreach (Type mode in new[] { typeof(CModeSolo), typeof(CModeSkyWorld), typeof(CModeUnderTheSea) })
            {
                MethodInfo method = AccessTools.DeclaredMethod(mode, nameof(CMode.GetMonstersList));
                if (method != null)
                    yield return method;
            }
        }

        private static void Postfix(ref CUnitMonster.CDesc[] __result)
        {
            try
            {
                __result = Mobs.AddToSpawnList(__result);
            }
            catch (Exception e)
            {
                NewMobsPlugin.Log.LogError("Spawn list patch failed: " + e);
            }
        }
    }
}
