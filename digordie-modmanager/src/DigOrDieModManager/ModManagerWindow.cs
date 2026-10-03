using System;
using System.Collections.Generic;
using System.Linq;
using BepInEx;
using UnityEngine;

namespace DigOrDieModManager
{
    /// <summary>The Mod Manager window, drawn with Unity IMGUI on top of the game menu.</summary>
    internal sealed class ModManagerWindow
    {
        public static ModManagerWindow Instance;

        private const string SearchControl = "modmanager_search";

        private enum Filter
        {
            All,
            Enabled,
            Disabled,
        }

        private Theme _theme;
        private Texts _t;
        private List<ModEntry> _mods = new List<ModEntry>();
        private string[] _loadErrors = new string[0];
        private string _search = string.Empty;
        private Filter _filter = Filter.All;
        private Vector2 _scroll;
        private string _error;
        private int _openedFrame;
        private string _bepInExVersion;
        private Action _afterDraw; // list changes are applied after the whole frame is drawn
        private bool _drawErrorLogged;

        public bool IsOpen { get; private set; }

        public void Open()
        {
            if (IsOpen)
                return;
            IsOpen = true;
            _openedFrame = Time.frameCount;
            _t = Texts.Current;
            _error = null;
            _scroll = Vector2.zero;
            try
            {
                ModCatalog.RetryPending();
            }
            catch (Exception e)
            {
                ModManagerPlugin.Log.LogDebug("Retrying pending changes failed: " + e.Message);
            }
            Reload();
        }

        public void Close()
        {
            IsOpen = false;
        }

        private void Reload()
        {
            try
            {
                _mods = ModCatalog.Scan();
                _loadErrors = ModCatalog.GetLoadErrors();
            }
            catch (Exception e)
            {
                ModManagerPlugin.Log.LogError("Could not list mods: " + e);
                _error = e.Message;
            }
        }

        public void OnGUI()
        {
            if (!IsOpen)
                return;

            float scale = Mathf.Max(0.5f, Screen.height / 1080f);
            if (_theme == null || !Mathf.Approximately(_theme.Scale, scale))
                _theme = new Theme(scale, GUI.skin);

            GUISkin previousSkin = GUI.skin;
            GUI.skin = _theme.Skin;
            GUI.depth = -1000;
            try
            {
                Event e = Event.current;
                // The click that opened the window must not also press something inside it.
                if (Time.frameCount <= _openedFrame + 1 && e.isMouse)
                    e.Use();
                if (e.type == EventType.KeyDown && e.keyCode == KeyCode.Escape)
                {
                    e.Use();
                    Close();
                    return;
                }
                Draw();

                if (_afterDraw != null)
                {
                    Action action = _afterDraw;
                    _afterDraw = null;
                    action();
                }
            }
            catch (Exception e)
            {
                if (!_drawErrorLogged)
                {
                    _drawErrorLogged = true; // OnGUI runs several times per frame: log only once
                    ModManagerPlugin.Log.LogError("Mod Manager window error: " + e);
                }
            }
            finally
            {
                GUI.skin = previousSkin;
            }
        }

        private void Draw()
        {
            Theme th = _theme;
            float screenW = Screen.width, screenH = Screen.height;

            GUI.DrawTexture(new Rect(0, 0, screenW, screenH), th.Dim);

            float panelW = Mathf.Min(th.S(1240), screenW - th.S(60));
            float panelH = Mathf.Min(th.S(940), screenH - th.S(60));
            var panel = new Rect(Mathf.Round((screenW - panelW) / 2), Mathf.Round((screenH - panelH) / 2), panelW, panelH);
            GUI.Box(panel, GUIContent.none, th.Panel);
            DrawHeader(panel);

            float pad = th.S(26);
            float headerH = th.S(80);
            float footerH = th.S(96);
            var body = new Rect(panel.x + pad, panel.y + headerH + th.S(18), panel.width - pad * 2, panel.height - headerH - footerH - th.S(18));
            GUILayout.BeginArea(body);
            DrawToolbar();
            GUILayout.Space(th.S(14));
            DrawList();
            GUILayout.EndArea();

            var footer = new Rect(panel.x + pad, panel.yMax - footerH, panel.width - pad * 2, footerH - th.S(14));
            DrawFooter(footer);
        }

        private void DrawHeader(Rect panel)
        {
            Theme th = _theme;
            float frame = Mathf.Max(2, th.S(3));
            var header = new Rect(panel.x + frame, panel.y + frame, panel.width - frame * 2, th.S(80) - frame);
            GUI.Box(header, GUIContent.none, th.Header);

            GUI.Label(new Rect(header.x + th.S(28), header.y, header.width / 2, header.height), _t.Title, th.Title);

            int plugins = _mods.Count(m => !m.IsLibrary);
            int active = _mods.Count(m => m.Status == ModStatus.Active);
            string summary = string.Format(_t.Summary, plugins, active, BepInExVersion());
            float closeSize = th.S(50);
            GUI.Label(new Rect(header.x, header.y, header.width - closeSize - th.S(44), header.height), summary, th.Summary);

            var close = new Rect(header.xMax - closeSize - th.S(16), header.y + (header.height - closeSize) / 2, closeSize, closeSize);
            if (GUI.Button(close, "×", th.CloseX))
                Close();
        }

        private void DrawToolbar()
        {
            Theme th = _theme;
            GUILayout.BeginHorizontal();

            GUI.SetNextControlName(SearchControl);
            _search = GUILayout.TextField(_search ?? string.Empty, 64, th.Search, GUILayout.ExpandWidth(true), GUILayout.Height(th.S(54)));
            Rect searchRect = GUILayoutUtility.GetLastRect();
            if (_search.Length == 0 && GUI.GetNameOfFocusedControl() != SearchControl)
                GUI.Label(searchRect, _t.SearchHint, th.Placeholder);

            GUILayout.Space(th.S(10));
            FilterTab(Filter.All, _t.FilterAll);
            FilterTab(Filter.Enabled, _t.FilterEnabled);
            FilterTab(Filter.Disabled, _t.FilterDisabled);

            GUILayout.EndHorizontal();
        }

        private void FilterTab(Filter filter, string label)
        {
            GUIStyle style = _filter == filter ? _theme.TabSelected : _theme.Tab;
            if (GUILayout.Button(label, style, GUILayout.Height(_theme.S(54))))
                _filter = filter;
        }

        private void DrawList()
        {
            Theme th = _theme;
            _scroll = GUILayout.BeginScrollView(_scroll, false, false);

            if (_loadErrors.Length > 0 && _filter != Filter.Disabled)
            {
                GUILayout.BeginVertical(th.ErrorBox);
                GUILayout.Label(_t.LoadErrors, th.ModError);
                foreach (string error in _loadErrors)
                    GUILayout.Label("• " + error, th.ModText);
                GUILayout.EndVertical();
                GUILayout.Space(th.S(12));
            }

            if (_mods.Count == 0)
            {
                GUILayout.Label(_t.NoMods, th.Empty);
            }
            else
            {
                string query = (_search ?? string.Empty).Trim().ToLowerInvariant();
                int shown = 0;
                // Iterate over a copy: toggling a mod rebuilds the list.
                foreach (ModEntry mod in _mods.ToArray())
                {
                    if (_filter == Filter.Enabled && !mod.WantedEnabled)
                        continue;
                    if (_filter == Filter.Disabled && mod.WantedEnabled)
                        continue;
                    if (query.Length > 0 && !mod.SearchText.Contains(query))
                        continue;
                    DrawRow(mod);
                    GUILayout.Space(th.S(10));
                    shown++;
                }
                if (shown == 0)
                    GUILayout.Label(_t.NoMatches, th.Empty);
            }

            GUILayout.EndScrollView();
        }

        private void DrawRow(ModEntry mod)
        {
            Theme th = _theme;
            GUILayout.BeginHorizontal(th.Row);

            // On/off switch
            GUILayout.BeginVertical(GUILayout.Width(th.S(80)));
            GUILayout.Space(th.S(4));
            if (DrawSwitch(mod.WantedEnabled, !mod.IsSelf))
                _afterDraw = () => Toggle(mod);
            GUILayout.EndVertical();
            GUILayout.Space(th.S(20));

            // Name, version and details
            GUILayout.BeginVertical();
            GUILayout.BeginHorizontal();
            GUILayout.Label(mod.Name, th.ModName);
            if (!string.IsNullOrEmpty(mod.Version))
                GUILayout.Label("v" + mod.Version, th.ModVersion);
            GUILayout.FlexibleSpace();
            GUILayout.EndHorizontal();

            string guids = string.Join(", ", mod.Plugins.Select(p => p.Guid).Where(g => !string.IsNullOrEmpty(g)).ToArray());
            GUILayout.Label(guids.Length > 0 ? guids + "   ·   " + mod.DisplayPath : mod.DisplayPath, th.ModMeta);

            if (!string.IsNullOrEmpty(mod.Description))
                GUILayout.Label(mod.Description, th.ModText);
            if (mod.Dependencies.Count > 0)
                GUILayout.Label(string.Format(_t.Requires, string.Join(", ", mod.Dependencies.ToArray())), th.ModMeta);
            if (mod.IsSelf)
                GUILayout.Label(_t.ThisMod, th.ModMeta);
            else if (mod.IsLibrary)
                GUILayout.Label(_t.LibraryHint, th.ModMeta);
            if (mod.Status == ModStatus.Failed)
                GUILayout.Label(_t.FailedHint, th.ModError);
            GUILayout.EndVertical();

            GUILayout.Space(th.S(16));

            // Status and actions
            GUILayout.BeginVertical(GUILayout.Width(th.S(300)));
            GUILayout.BeginHorizontal();
            GUILayout.FlexibleSpace();
            GUILayout.Label(StatusText(mod.Status), th.BadgeFor(mod.Status));
            GUILayout.EndHorizontal();
            GUILayout.BeginHorizontal();
            GUILayout.FlexibleSpace();
            if (mod.ConfigPath != null && GUILayout.Button(_t.Config, th.SmallButton))
                SystemShell.OpenTextFile(mod.ConfigPath);
            if (GUILayout.Button(_t.ShowFile, th.SmallButton))
                SystemShell.ShowFile(mod.CurrentPath);
            GUILayout.EndHorizontal();
            GUILayout.EndVertical();

            GUILayout.EndHorizontal();
        }

        private bool DrawSwitch(bool on, bool interactable)
        {
            Theme th = _theme;
            Rect rect = GUILayoutUtility.GetRect(th.S(80), th.S(40), GUILayout.Width(th.S(80)), GUILayout.Height(th.S(40)));
            bool clicked = interactable && GUI.Button(rect, GUIContent.none, GUIStyle.none);

            if (Event.current.type == EventType.Repaint)
            {
                GUIStyle track = !on ? th.SwitchOff : interactable ? th.SwitchOn : th.SwitchLocked;
                track.Draw(rect, false, false, false, false);
                float inset = th.S(5);
                float knobSize = rect.height - inset * 2;
                float knobX = on ? rect.xMax - inset - knobSize : rect.x + inset;
                GUI.DrawTexture(new Rect(knobX, rect.y + inset, knobSize, knobSize), th.Knob);
            }
            return clicked;
        }

        private void DrawFooter(Rect footer)
        {
            Theme th = _theme;
            bool restartNeeded = _mods.Any(m => m.Status == ModStatus.WillEnable || m.Status == ModStatus.WillDisable);

            GUILayout.BeginArea(footer);
            GUILayout.BeginHorizontal(GUILayout.Height(footer.height));

            GUILayout.BeginVertical();
            GUILayout.FlexibleSpace();
            if (_error != null)
                GUILayout.Label(_error, th.ModError);
            else if (restartNeeded)
                GUILayout.Label(_t.RestartNotice, th.Notice);
            GUILayout.FlexibleSpace();
            GUILayout.EndVertical();

            GUILayout.FlexibleSpace();

            GUILayout.BeginVertical();
            GUILayout.FlexibleSpace();
            GUILayout.BeginHorizontal();
            if (GUILayout.Button(_t.OpenFolder, th.Button))
                SystemShell.OpenFolder(Paths.PluginPath);
            if (GUILayout.Button(_t.Refresh, th.Button))
            {
                _afterDraw = () =>
                {
                    _error = null;
                    ModCatalog.RetryPending();
                    Reload();
                };
            }
            if (restartNeeded && GUILayout.Button(_t.Restart, th.Button))
            {
                _afterDraw = () =>
                {
                    if (!SystemShell.RestartGame())
                        _error = _t.RestartFailed;
                };
            }
            if (GUILayout.Button(_t.Close, th.Button))
                Close();
            GUILayout.EndHorizontal();
            GUILayout.FlexibleSpace();
            GUILayout.EndVertical();

            GUILayout.EndHorizontal();
            GUILayout.EndArea();
        }

        private void Toggle(ModEntry mod)
        {
            string error = ModCatalog.SetEnabled(mod, !mod.WantedEnabled);
            _error = error == null ? null : string.Format(_t.ToggleError, mod.Name, error);
            Reload();
        }

        private string StatusText(ModStatus status)
        {
            switch (status)
            {
                case ModStatus.Active: return _t.StatusActive;
                case ModStatus.WillEnable: return _t.StatusWillEnable;
                case ModStatus.WillDisable: return _t.StatusWillDisable;
                case ModStatus.Failed: return _t.StatusFailed;
                case ModStatus.Library: return _t.StatusLibrary;
                default: return _t.StatusDisabled;
            }
        }

        private string BepInExVersion()
        {
            if (_bepInExVersion == null)
            {
                Version v = typeof(Paths).Assembly.GetName().Version;
                _bepInExVersion = v.Revision > 0 ? v.ToString(4) : v.ToString(3);
            }
            return _bepInExVersion;
        }
    }
}
