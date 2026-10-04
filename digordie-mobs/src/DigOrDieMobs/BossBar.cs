using System;
using System.Collections.Generic;
using UnityEngine;

namespace DigOrDieMobs
{
    /// <summary>
    /// Health bar at the top of the screen for the nearest mini-boss or boss
    /// (red for the boss, orange for mini-bosses). Drawn with Unity IMGUI.
    /// </summary>
    internal sealed class BossBar
    {
        private const float ShowDistance = 45f;
        private const float SearchPeriod = 0.25f;

        private CUnit _target;
        private Mob _targetMob;
        private float _nextSearch;
        private Texture2D _white;
        private GUIStyle _name;
        private GUIStyle _numbers;
        private float _styleScale;

        public void Update()
        {
            if (Time.unscaledTime < _nextSearch)
                return;
            _nextSearch = Time.unscaledTime + SearchPeriod;
            _target = null;
            _targetMob = null;

            try
            {
                CUnitPlayerLocal player = G.m_player;
                List<CUnit> units = SUnits.Units;
                if (player == null || units == null || !player.IsAlive())
                    return;

                float best = ShowDistance;
                foreach (CUnit unit in units)
                {
                    if (unit == null || !unit.IsAlive())
                        continue;
                    Mob mob = Mobs.ByDesc(unit.m_uDesc);
                    if (mob == null || !mob.IsRare)
                        continue;
                    float distance = (unit.Pos - player.Pos).magnitude;
                    // the boss wins over mini-bosses when both are around
                    if (mob.Rank == MobRank.Boss)
                        distance *= 0.5f;
                    if (distance < best)
                    {
                        best = distance;
                        _target = unit;
                        _targetMob = mob;
                    }
                }
            }
            catch (Exception)
            {
                _target = null; // not in a game
            }
        }

        public void OnGUI()
        {
            if (_target == null || _targetMob == null || Event.current.type != EventType.Repaint)
                return;
            if (!_target.IsAlive())
                return;

            float scale = Mathf.Max(0.5f, Screen.height / 1080f);
            EnsureStyles(scale);

            float width = Mathf.Round(Screen.width * 0.42f);
            float barHeight = Mathf.Round(26 * scale);
            float x = Mathf.Round((Screen.width - width) / 2f);
            float y = Mathf.Round(54 * scale);
            float border = Mathf.Max(2f, Mathf.Round(2 * scale));
            float health = Mathf.Clamp01(_target.m_hp / Mathf.Max(1f, _target.GetHpMax()));
            bool boss = _targetMob.Rank == MobRank.Boss;

            string title = NewMobsPlugin.LocalName(_targetMob);
            GUI.Label(new Rect(x, y - 34 * scale, width, 32 * scale), title, _name);

            Fill(new Rect(x - border, y - border, width + border * 2, barHeight + border * 2), new Color(0f, 0f, 0f, 0.75f));
            Fill(new Rect(x, y, width, barHeight), new Color(0.16f, 0.05f, 0.06f, 0.9f));
            Color bar = boss ? new Color(0.84f, 0.12f, 0.2f) : new Color(0.93f, 0.5f, 0.08f);
            Fill(new Rect(x, y, Mathf.Round(width * health), barHeight), bar);
            Fill(new Rect(x, y, Mathf.Round(width * health), Mathf.Round(barHeight * 0.35f)), new Color(1f, 1f, 1f, 0.18f));

            string numbers = Mathf.CeilToInt(_target.m_hp) + " / " + Mathf.CeilToInt(_target.GetHpMax());
            GUI.Label(new Rect(x, y, width, barHeight), numbers, _numbers);
        }

        private void Fill(Rect rect, Color color)
        {
            Color previous = GUI.color;
            GUI.color = color;
            GUI.DrawTexture(rect, _white);
            GUI.color = previous;
        }

        private void EnsureStyles(float scale)
        {
            if (_white == null)
            {
                _white = new Texture2D(2, 2) { hideFlags = HideFlags.HideAndDontSave };
                _white.SetPixels(new[] { Color.white, Color.white, Color.white, Color.white });
                _white.Apply();
            }
            if (_name != null && Mathf.Approximately(_styleScale, scale))
                return;

            _styleScale = scale;
            _name = new GUIStyle(GUI.skin.label)
            {
                fontSize = Mathf.RoundToInt(24 * scale),
                fontStyle = FontStyle.Bold,
                alignment = TextAnchor.LowerCenter,
            };
            _name.normal.textColor = Color.white;
            _numbers = new GUIStyle(GUI.skin.label)
            {
                fontSize = Mathf.RoundToInt(16 * scale),
                fontStyle = FontStyle.Bold,
                alignment = TextAnchor.MiddleCenter,
            };
            _numbers.normal.textColor = new Color(1f, 1f, 1f, 0.95f);
        }
    }
}
