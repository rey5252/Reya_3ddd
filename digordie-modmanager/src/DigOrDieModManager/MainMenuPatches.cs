using System;
using HarmonyLib;

namespace DigOrDieModManager
{
    /// <summary>
    /// Adds the MODS button to the main menu (SScreenHome). The button is a regular game
    /// CGuiButton that copies the look of the "Quit" button, so it matches the other menu buttons.
    /// </summary>
    internal static class MainMenuPatches
    {
        private static SScreenHome _home;
        private static CGuiButton _modsButton;
        private static MenuColumnLayout _layout;
        private static int _framesShown;
        private static bool _stateLogged;
        private static bool _updateErrorLogged;

        [HarmonyPostfix]
        [HarmonyPatch(typeof(SScreenHome), nameof(SScreenHome.OnInit))]
        private static void SScreenHome_OnInit(SScreenHome __instance)
        {
            try
            {
                CreateButton(__instance);
            }
            catch (Exception e)
            {
                ModManagerPlugin.Log.LogError("Could not add the MODS button to the main menu: " + e);
            }
        }

        [HarmonyPostfix]
        [HarmonyPatch(typeof(SScreenHome), nameof(SScreenHome.OnActivate))]
        private static void SScreenHome_OnActivate(SScreenHome __instance)
        {
            try
            {
                if (_home != __instance || _modsButton == null)
                    CreateButton(__instance);
                if (_modsButton == null)
                    return;

                _modsButton.SetText(Texts.Current.MenuButton);
                _modsButton.m_visible = true;
                BringToFront(_modsButton);
                if (_layout != null)
                    _layout.Apply();
                else
                    PlaceInCorner(_modsButton);
            }
            catch (Exception e)
            {
                ModManagerPlugin.Log.LogError("Could not update the MODS button: " + e);
            }
        }

        [HarmonyPostfix]
        [HarmonyPatch(typeof(SScreenHome), nameof(SScreenHome.OnUpdate))]
        private static void SScreenHome_OnUpdate(SScreenHome __instance)
        {
            if (_modsButton == null || _home != __instance)
                return;
            try
            {
                // The game may place its own menu buttons again while the menu is shown,
                // so the column layout is re-applied every frame (8 assignments, cheap).
                if (_layout != null)
                    _layout.Apply();

                if (!_stateLogged && ++_framesShown == 120)
                {
                    _stateLogged = true;
                    LogMenuState("Main menu after 120 frames");
                }

                if (_modsButton.IsClicked())
                    ModManagerWindow.Instance.Open();
            }
            catch (Exception e)
            {
                if (!_updateErrorLogged)
                {
                    _updateErrorLogged = true;
                    ModManagerPlugin.Log.LogError("MODS button update failed: " + e);
                }
            }
        }

        private static void CreateButton(SScreenHome home)
        {
            _home = home;
            _modsButton = null;
            _layout = null;

            CGuiButton template = home.m_btQuit ?? home.m_btOptions;
            if (template == null)
            {
                ModManagerPlugin.Log.LogWarning("Main menu buttons not found, the MODS button is not added");
                return;
            }
            LogMenuState("Main menu found, adding the MODS button. Original buttons");

            CGui parent = template.m_parent ?? home.m_guiRoot;
            var button = new CGuiButton(home, parent, template.m_parentAnchor, template.m_x, template.m_y, template.m_coordsOrigin)
            {
                m_width = template.m_width,
                m_height = template.m_height,
                m_textSize = template.m_textSize,
            };
            button.SetText(Texts.Current.MenuButton);
            // SetText may resize the button to its text: keep the size of the other menu buttons.
            button.m_width = template.m_width;
            button.m_height = template.m_height;

            if (parent.m_children != null && !parent.m_children.Contains(button))
                parent.m_children.Add(button);

            _modsButton = button;

            if (ModManagerPlugin.ButtonInMenuColumn.Value)
            {
                string reason;
                _layout = MenuColumnLayout.TryCreate(MenuColumn(home), button, out reason);
                if (_layout == null)
                    ModManagerPlugin.Log.LogWarning("Unexpected main menu layout (" + reason + "), the MODS button is placed at the bottom instead");
            }

            if (_layout != null)
                _layout.Apply();
            else
                PlaceInCorner(button);

            _framesShown = 0;
            _stateLogged = false;
            LogMenuState("MODS button added (" + (_layout != null ? "menu column" : "bottom of the screen") + ")");
        }

        private static CGuiButton[] MenuColumn(SScreenHome home)
        {
            return new[] { home.m_btNew, home.m_btLoad, home.m_btMulti, home.m_btCustom, home.m_btHelp, home.m_btOptions, home.m_btQuit };
        }

        private static void BringToFront(CGui gui)
        {
            // Children are drawn in order: the last one is on top.
            var siblings = gui.m_parent != null ? gui.m_parent.m_children : null;
            if (siblings == null || siblings.Count == 0 || siblings[siblings.Count - 1] == gui)
                return;
            siblings.Remove(gui);
            siblings.Add(gui);
        }

        /// <summary>Writes the menu buttons' layout to the BepInEx log (helps to fix layout problems).</summary>
        private static void LogMenuState(string title)
        {
            try
            {
                var sb = new System.Text.StringBuilder(title).Append(':');
                string[] names = { "New", "Load", "Multi", "Custom", "Help", "Options", "Quit" };
                CGuiButton[] column = MenuColumn(_home);
                for (int i = 0; i < column.Length; i++)
                    Describe(sb, names[i], column[i]);
                if (_modsButton != null)
                    Describe(sb, "MODS", _modsButton);
                ModManagerPlugin.Log.LogInfo(sb.ToString());
            }
            catch (Exception e)
            {
                ModManagerPlugin.Log.LogDebug("Could not describe the menu: " + e.Message);
            }
        }

        private static void Describe(System.Text.StringBuilder sb, string name, CGuiButton b)
        {
            sb.Append("\n  ").Append(name).Append(": ");
            if (b == null)
            {
                sb.Append("null");
                return;
            }
            sb.Append("x=").Append(b.m_x).Append(" y=").Append(b.m_y)
              .Append(" w=").Append(b.m_width).Append(" h=").Append(b.m_height)
              .Append(" anchor=").Append(b.m_parentAnchor).Append(" origin=").Append(b.m_coordsOrigin)
              .Append(" visible=").Append(b.m_visible)
              .Append(" rect=").Append(b.m_guiRect)
              .Append(" parent=").Append(b.m_parent == null ? "none" : b.m_parent.GetType().Name);
            if (b.m_parent != null && b.m_parent.m_children != null)
                sb.Append(" index=").Append(b.m_parent.m_children.IndexOf(b)).Append('/').Append(b.m_parent.m_children.Count);
        }

        private static void PlaceInCorner(CGuiButton button)
        {
            // Bottom center: the only spot of the menu that is free of panels.
            button.m_parentAnchor = EAnchor.LowerCenter;
            button.m_coordsOrigin = EAnchor.LowerCenter;
            button.m_x = 0;
            button.m_y = -30;
            Refresh(button);
        }

        internal static void Refresh(CGui gui)
        {
            if (gui.m_parent == null)
                return; // not initialized yet: the game computes its rectangle during init
            try
            {
                gui.CalculateGuiRect();
            }
            catch (Exception)
            {
                // the rectangle is recomputed by the game anyway
            }
        }
    }

    internal static class InputLockPatch
    {
        /// <summary>While the Mod Manager window is open, the game menus underneath ignore the mouse and keyboard.</summary>
        [HarmonyPostfix]
        [HarmonyPatch(typeof(SScreen), nameof(SScreen.IsInputLocked))]
        private static void SScreen_IsInputLocked(ref bool __result)
        {
            if (ModManagerWindow.Instance != null && ModManagerWindow.Instance.IsOpen)
                __result = true;
        }
    }

    /// <summary>
    /// Re-spaces the main menu column so that the MODS button fits between "Options" and "Quit"
    /// inside the same vertical range: 8 buttons instead of 7, each a bit lower.
    /// Works from the original (scene) positions, so it can be applied any number of times.
    /// </summary>
    internal sealed class MenuColumnLayout
    {
        private readonly CGuiButton[] _ordered;
        private readonly int[] _targetY;
        private readonly int _targetHeight;

        private MenuColumnLayout(CGuiButton[] ordered, int[] targetY, int targetHeight)
        {
            _ordered = ordered;
            _targetY = targetY;
            _targetHeight = targetHeight;
        }

        public static MenuColumnLayout TryCreate(CGuiButton[] column, CGuiButton modsButton, out string reason)
        {
            reason = null;
            int count = column.Length;
            if (count < 2)
            {
                reason = "too few buttons";
                return null;
            }
            foreach (CGuiButton b in column)
            {
                if (b == null)
                {
                    reason = "a menu button is missing";
                    return null;
                }
            }

            CGuiButton first = column[0];
            CGuiButton last = column[count - 1];
            int height = last.m_height;
            int span = last.m_y - first.m_y;
            if (span == 0 || height <= 0)
            {
                reason = "span=" + span + " height=" + height;
                return null;
            }

            // All buttons must form one evenly spaced vertical column with the same anchors and size.
            float step = span / (float)(count - 1);
            for (int i = 0; i < count; i++)
            {
                CGuiButton b = column[i];
                if (b.m_parentAnchor != first.m_parentAnchor || b.m_coordsOrigin != first.m_coordsOrigin)
                    reason = "button " + i + " has other anchors";
                else if (Math.Abs(b.m_x - first.m_x) > 4 || Math.Abs(b.m_height - height) > 4)
                    reason = "button " + i + " has another x or height";
                else if (Math.Abs(b.m_y - (first.m_y + step * i)) > Math.Abs(step) * 0.25f)
                    reason = "button " + i + " is not evenly spaced";
                if (reason != null)
                    return null;
            }

            // +1 when y grows downwards on screen (the first button is the top one).
            int down = span > 0 ? 1 : -1;
            float originShift = OriginToCenter(first.m_coordsOrigin, down);

            float firstCenter = first.m_y + originShift * height;
            float lastCenter = last.m_y + originShift * height;
            float newStep = (lastCenter - firstCenter) / count; // count gaps for count + 1 buttons
            int newHeight = Math.Max(1, (int)Math.Round(height * Math.Abs(newStep) / Math.Abs(step)));

            var ordered = new CGuiButton[count + 1];
            Array.Copy(column, ordered, count - 1);
            ordered[count - 1] = modsButton;     // MODS goes right above "Quit"
            ordered[count] = last;

            var targetY = new int[count + 1];
            for (int i = 0; i <= count; i++)
            {
                float center = firstCenter + newStep * i;
                targetY[i] = (int)Math.Round(center - originShift * newHeight);
            }

            modsButton.m_parentAnchor = first.m_parentAnchor;
            modsButton.m_coordsOrigin = first.m_coordsOrigin;
            modsButton.m_x = last.m_x;
            return new MenuColumnLayout(ordered, targetY, newHeight);
        }

        public void Apply()
        {
            for (int i = 0; i < _ordered.Length; i++)
            {
                CGuiButton b = _ordered[i];
                b.m_y = _targetY[i];
                b.m_height = _targetHeight;
                MainMenuPatches.Refresh(b);
            }
        }

        /// <summary>Offset (in button heights) from the element's origin point to its vertical center.</summary>
        private static float OriginToCenter(EAnchor origin, int down)
        {
            switch (origin)
            {
                case EAnchor.UpperLeft:
                case EAnchor.UpperCenter:
                case EAnchor.UpperRight:
                    return 0.5f * down;
                case EAnchor.LowerLeft:
                case EAnchor.LowerCenter:
                case EAnchor.LowerRight:
                    return -0.5f * down;
                default:
                    return 0f;
            }
        }
    }
}
