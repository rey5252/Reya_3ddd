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
            if (_modsButton != null && _home == __instance && _modsButton.IsClicked())
                ModManagerWindow.Instance.Open();
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
                _layout = MenuColumnLayout.TryCreate(new[]
                {
                    home.m_btNew, home.m_btLoad, home.m_btMulti, home.m_btCustom, home.m_btHelp, home.m_btOptions, home.m_btQuit,
                }, button);
                if (_layout == null)
                    ModManagerPlugin.Log.LogWarning("Unexpected main menu layout, the MODS button is placed in the corner instead");
            }

            if (_layout != null)
                _layout.Apply();
            else
                PlaceInCorner(button);
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

        public static MenuColumnLayout TryCreate(CGuiButton[] column, CGuiButton modsButton)
        {
            int count = column.Length;
            if (count < 2)
                return null;
            foreach (CGuiButton b in column)
            {
                if (b == null)
                    return null;
            }

            CGuiButton first = column[0];
            CGuiButton last = column[count - 1];
            int height = last.m_height;
            int span = last.m_y - first.m_y;
            if (span == 0 || height <= 0)
                return null;

            // All buttons must form one evenly spaced vertical column with the same anchors and size.
            float step = span / (float)(count - 1);
            for (int i = 0; i < count; i++)
            {
                CGuiButton b = column[i];
                if (b.m_parentAnchor != first.m_parentAnchor || b.m_coordsOrigin != first.m_coordsOrigin)
                    return null;
                if (Math.Abs(b.m_x - first.m_x) > 4 || Math.Abs(b.m_height - height) > 4)
                    return null;
                if (Math.Abs(b.m_y - (first.m_y + step * i)) > Math.Abs(step) * 0.25f)
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
