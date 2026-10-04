using System;
using System.Collections.Generic;
using DODModAPI;
using HarmonyLib;
using UnityEngine;

namespace DigOrDieMobs
{
    /// <summary>
    /// What makes mini-bosses and the boss more than big monsters: summoning helpers (at health
    /// thresholds or periodically), splitting on death, an announcement when they appear and the
    /// pause between two appearances. Runs on the host / in single player only (clients receive
    /// the spawned units over the network).
    /// </summary>
    internal static class BossBehaviour
    {
        private sealed class State
        {
            public int NextThreshold;
            public float NextPeriodicSummon;
            public bool DeathHandled;
        }

        private const float SummonPlayerDistance = 40f;
        private const float MinionCountDistance = 25f;

        private static readonly Dictionary<CUnit, State> States = new Dictionary<CUnit, State>();
        private static readonly List<KeyValuePair<Mob, Vector2>> PendingSpawns = new List<KeyValuePair<Mob, Vector2>>();
        private static readonly List<CUnit> Gone = new List<CUnit>();

        /// <summary>A rare mob appeared: start its cooldown and tell nearby players.</summary>
        [HarmonyPostfix]
        [HarmonyPatch(typeof(SUnits), nameof(SUnits.SpawnUnit_Local))]
        private static void SUnits_SpawnUnit_Local(CUnit __result, CUnit.CDesc uDesc)
        {
            try
            {
                Mob mob = Mobs.ByDesc(uDesc);
                if (mob == null || !mob.IsRare || __result == null)
                    return;

                mob.NextAllowedTime = Time.realtimeSinceStartup + CooldownSeconds(mob);
                CUnitPlayerLocal player = G.m_player;
                if (player == null || (player.Pos - __result.Pos).magnitude > 80f)
                    return;

                bool russian = NewMobsPlugin.IsGameRussian();
                string name = NewMobsPlugin.LocalName(mob);
                Misc.SendChatMessageLocal(mob.Rank == MobRank.Boss
                    ? (russian ? name + " пробудилось!" : name + " has awakened!")
                    : (russian ? name + " где-то рядом!" : name + " is nearby!"));
                if (mob.Rank == MobRank.Boss)
                    SGame.ShakeCam(0f, 1.5f);
            }
            catch (Exception e)
            {
                NewMobsPlugin.Log.LogDebug("Boss spawn hook: " + e.Message);
            }
        }

        [HarmonyPostfix]
        [HarmonyPatch(typeof(SUnits), nameof(SUnits.OnUpdateSimu))]
        private static void SUnits_OnUpdateSimu()
        {
            try
            {
                Tick();
            }
            catch (Exception e)
            {
                NewMobsPlugin.Log.LogDebug("Boss behaviour: " + e.Message);
            }
        }

        public static float CooldownSeconds(Mob mob)
        {
            float minutes = mob.RareCooldownMinutes != null ? mob.RareCooldownMinutes.Value : mob.DefaultCooldownMinutes;
            return Mathf.Max(0f, minutes) * 60f;
        }

        private static void Tick()
        {
            List<CUnit> units = SUnits.Units;
            if (units == null)
                return;

            PendingSpawns.Clear();
            foreach (CUnit unit in units)
            {
                if (unit == null)
                    continue;
                Mob mob = Mobs.ByDesc(unit.m_uDesc);
                if (mob == null || !mob.IsRare || unit.IsNetworkControlled())
                    continue;

                State state;
                if (!States.TryGetValue(unit, out state))
                    States[unit] = state = new State { NextPeriodicSummon = Time.time + mob.SummonEverySeconds };

                if (!unit.IsAlive())
                {
                    if (!state.DeathHandled)
                    {
                        state.DeathHandled = true;
                        for (int i = 0; i < mob.DeathSplitCount; i++)
                            PendingSpawns.Add(new KeyValuePair<Mob, Vector2>(mob.DeathSplitInto, unit.Pos + Spread(i, mob.DeathSplitCount)));
                    }
                    continue;
                }

                // waves at health thresholds
                float health = unit.m_hp / Mathf.Max(1f, unit.GetHpMax());
                while (state.NextThreshold < mob.SummonAtHealth.Length && health <= mob.SummonAtHealth[state.NextThreshold])
                {
                    state.NextThreshold++;
                    QueueWave(mob, unit);
                }

                // periodic waves while fighting a nearby player
                if (mob.SummonEverySeconds > 0f && Time.time >= state.NextPeriodicSummon)
                {
                    state.NextPeriodicSummon = Time.time + mob.SummonEverySeconds;
                    var monster = unit as CUnitMonster;
                    if (monster != null && monster.m_target != null && IsPlayerNear(unit.Pos, SummonPlayerDistance)
                        && CountMinionsNear(mob, unit.Pos, units) < mob.MaxMinionsNearby)
                    {
                        QueueWave(mob, unit);
                    }
                }
            }

            // forget units that are gone
            Gone.Clear();
            foreach (CUnit unit in States.Keys)
            {
                if (!units.Contains(unit))
                    Gone.Add(unit);
            }
            foreach (CUnit unit in Gone)
                States.Remove(unit);

            foreach (var spawn in PendingSpawns)
            {
                if (spawn.Key != null)
                    SUnits.SpawnUnit(spawn.Key.Desc, spawn.Value);
            }
        }

        private static void QueueWave(Mob mob, CUnit unit)
        {
            for (int i = 0; i < mob.SummonWave.Length; i++)
                PendingSpawns.Add(new KeyValuePair<Mob, Vector2>(mob.SummonWave[i], unit.Pos + Spread(i, mob.SummonWave.Length)));
        }

        /// <summary>Positions left and right of the summoner, a little above it.</summary>
        private static Vector2 Spread(int index, int count)
        {
            float side = index % 2 == 0 ? -1f : 1f;
            float distance = 1.5f + (index / 2) * 1.2f;
            return new Vector2(side * distance, 0.8f + (index / 2) * 0.4f);
        }

        private static bool IsPlayerNear(Vector2 pos, float distance)
        {
            CUnitPlayer player = SUnits.GetClosestPlayer(pos, distance);
            return player != null;
        }

        private static int CountMinionsNear(Mob mob, Vector2 pos, List<CUnit> units)
        {
            int count = 0;
            foreach (CUnit unit in units)
            {
                if (unit == null || !unit.IsAlive() || (unit.Pos - pos).magnitude > MinionCountDistance)
                    continue;
                foreach (Mob minion in mob.SummonWave)
                {
                    if (unit.m_uDesc == minion.Desc)
                    {
                        count++;
                        break;
                    }
                }
            }
            return count;
        }
    }
}
