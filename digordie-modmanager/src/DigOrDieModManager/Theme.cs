using System;
using UnityEngine;

namespace DigOrDieModManager
{
    /// <summary>
    /// IMGUI look of the Mod Manager, modelled on the Dig or Die menu: dark navy panels with
    /// cyan frames and the same bright blue buttons with a darker bottom/right edge.
    /// Built for one UI scale (screen height / 1080) so text stays sharp at any resolution.
    /// </summary>
    internal sealed class Theme
    {
        // Colours picked from the main menu.
        private static readonly Color ButtonBlue = Hex(0x1F9BD1);
        private static readonly Color ButtonBlueHover = Hex(0x39B2E6);
        private static readonly Color ButtonBluePressed = Hex(0x167FAE);
        private static readonly Color PanelFill = new Color32(9, 27, 40, 245);
        private static readonly Color PanelFrame = Hex(0x2A9FD6);
        private static readonly Color HeaderFill = Hex(0x135A7C);
        private static readonly Color RowFill = Hex(0x10304A);
        private static readonly Color RowFrame = Hex(0x1C4E6E);
        private static readonly Color FieldFill = Hex(0x0A1E2C);
        private static readonly Color MutedButton = Hex(0x24506B);
        private static readonly Color MutedButtonHover = Hex(0x2F6688);

        public static readonly Color TextWhite = Color.white;
        public static readonly Color TextMuted = Hex(0x8FB3C8);
        public static readonly Color TextAccent = Hex(0x5FC8F5);
        public static readonly Color TextWarning = Hex(0xF0AD4E);
        public static readonly Color TextError = Hex(0xFF7B72);

        public static readonly Color StatusActive = Hex(0x2FA862);
        public static readonly Color StatusDisabled = Hex(0x4D5E6B);
        public static readonly Color StatusPending = Hex(0xD98A1C);
        public static readonly Color StatusFailed = Hex(0xC9443F);
        public static readonly Color StatusLibrary = Hex(0x7A62C4);

        public readonly float Scale;
        public GUISkin Skin;

        public GUIStyle Panel, Header, Title, Summary, CloseX;
        public GUIStyle Row, ModName, ModVersion, ModMeta, ModText, ModWarning, ModError, ErrorBox;
        public GUIStyle Button, SmallButton, Tab, TabSelected, Search, Placeholder, Notice, Empty;
        public GUIStyle SwitchOn, SwitchOff, SwitchLocked;
        public GUIStyle BadgeActive, BadgeDisabled, BadgePending, BadgeFailed, BadgeLibrary;
        public Texture2D Dim, Knob;

        public Theme(float scale, GUISkin baseSkin)
        {
            Scale = scale;

            Skin = UnityEngine.Object.Instantiate(baseSkin);
            Skin.hideFlags = HideFlags.HideAndDontSave;

            Dim = Solid(new Color(0f, 0f, 0f, 0.7f));
            Knob = Circle(64, Color.white);

            int frame = Mathf.Max(2, S(3));
            Panel = new GUIStyle { border = Offset(frame + 1) };
            Panel.normal.background = Framed(4 * frame + 4, frame, PanelFill, PanelFrame);

            Header = new GUIStyle();
            Header.normal.background = Solid(HeaderFill);

            Title = Text(40, TextWhite, FontStyle.Bold);
            Title.alignment = TextAnchor.MiddleLeft;
            Summary = Text(19, TextMuted, FontStyle.Normal);
            Summary.alignment = TextAnchor.MiddleRight;

            CloseX = GameButton(MutedButton, MutedButtonHover, ButtonBluePressed, 34);
            CloseX.padding = new RectOffset(0, 0, 0, S(4));

            Row = new GUIStyle { border = Offset(2), padding = new RectOffset(S(18), S(18), S(14), S(14)) };
            Row.normal.background = Framed(8, 1, RowFill, RowFrame);

            ModName = Text(27, TextWhite, FontStyle.Bold);
            ModVersion = Text(20, TextAccent, FontStyle.Normal);
            ModVersion.padding = new RectOffset(S(10), 0, S(6), 0);
            ModMeta = Text(17, TextMuted, FontStyle.Normal);
            ModText = Text(19, Hex(0xC9DCE8), FontStyle.Normal);
            ModText.wordWrap = true;
            ModWarning = Text(18, TextWarning, FontStyle.Normal);
            ModWarning.wordWrap = true;
            ModError = Text(18, TextError, FontStyle.Normal);
            ModError.wordWrap = true;

            ErrorBox = new GUIStyle { border = Offset(2), padding = new RectOffset(S(16), S(16), S(12), S(12)) };
            ErrorBox.normal.background = Framed(8, 1, Hex(0x3A1618), Hex(0x8C2F2B));

            Button = GameButton(ButtonBlue, ButtonBlueHover, ButtonBluePressed, 24);
            SmallButton = GameButton(MutedButton, MutedButtonHover, ButtonBluePressed, 18);
            SmallButton.padding = new RectOffset(S(14), S(16), S(6), S(9));
            Tab = GameButton(MutedButton, MutedButtonHover, ButtonBluePressed, 20);
            TabSelected = GameButton(ButtonBlue, ButtonBlue, ButtonBlue, 20);

            Search = new GUIStyle(baseSkin.textField)
            {
                fontSize = S(22),
                border = Offset(3),
                padding = new RectOffset(S(14), S(14), S(10), S(10)),
                alignment = TextAnchor.MiddleLeft,
                clipping = TextClipping.Clip,
            };
            Texture2D fieldTex = Framed(12, 2, FieldFill, RowFrame);
            Texture2D fieldFocusedTex = Framed(12, 2, FieldFill, PanelFrame);
            SetAll(Search, fieldTex, TextWhite);
            Search.focused.background = fieldFocusedTex;
            Search.onFocused.background = fieldFocusedTex;

            Placeholder = Text(22, Hex(0x56788C), FontStyle.Italic);
            Placeholder.padding = Search.padding;
            Placeholder.alignment = TextAnchor.MiddleLeft;

            Notice = Text(20, TextWarning, FontStyle.Bold);
            Notice.wordWrap = true;
            Notice.alignment = TextAnchor.MiddleLeft;
            Empty = Text(22, TextMuted, FontStyle.Normal);
            Empty.wordWrap = true;
            Empty.alignment = TextAnchor.MiddleCenter;
            Empty.padding = new RectOffset(S(20), S(20), S(60), S(60));

            SwitchOn = Pill(StatusActive);
            SwitchOff = Pill(Hex(0x3B4B57));
            SwitchLocked = Pill(Hex(0x2C6B4A));

            BadgeActive = Badge(StatusActive);
            BadgeDisabled = Badge(StatusDisabled);
            BadgePending = Badge(StatusPending);
            BadgeFailed = Badge(StatusFailed);
            BadgeLibrary = Badge(StatusLibrary);

            // Scrollbars in the same palette. The skin's own styles are edited in place (not replaced),
            // because Unity finds the thumb/buttons of a scrollbar by the style names.
            GUIStyle bar = Skin.verticalScrollbar;
            bar.fixedWidth = S(14);
            bar.margin = new RectOffset(S(8), 0, 0, 0);
            bar.border = Offset(1);
            bar.padding = new RectOffset(0, 0, 0, 0);
            SetAll(bar, Solid(FieldFill), TextWhite);
            GUIStyle thumb = Skin.verticalScrollbarThumb;
            thumb.fixedWidth = S(14);
            thumb.border = Offset(1);
            thumb.padding = new RectOffset(0, 0, 0, 0);
            SetAll(thumb, Solid(ButtonBlue), TextWhite);
            thumb.hover.background = Solid(ButtonBlueHover);
            thumb.active.background = Solid(ButtonBlueHover);
            Skin.scrollView.normal.background = null;
            Skin.scrollView.padding = new RectOffset(0, 0, 0, 0);
        }

        public int S(float value)
        {
            return Mathf.RoundToInt(value * Scale);
        }

        public GUIStyle BadgeFor(ModStatus status)
        {
            switch (status)
            {
                case ModStatus.Active: return BadgeActive;
                case ModStatus.WillEnable:
                case ModStatus.WillDisable: return BadgePending;
                case ModStatus.Failed: return BadgeFailed;
                case ModStatus.Library: return BadgeLibrary;
                default: return BadgeDisabled;
            }
        }

        private GUIStyle Text(int size, Color color, FontStyle style)
        {
            var s = new GUIStyle { fontSize = S(size), fontStyle = style, richText = false, wordWrap = false };
            s.normal.textColor = color;
            return s;
        }

        /// <summary>A Dig or Die style button: flat colour with a darker bottom and right edge.</summary>
        private GUIStyle GameButton(Color fill, Color hover, Color pressed, int fontSize)
        {
            var s = new GUIStyle
            {
                fontSize = S(fontSize),
                fontStyle = FontStyle.Bold,
                alignment = TextAnchor.MiddleCenter,
                border = new RectOffset(2, 4, 2, 5),
                padding = new RectOffset(S(22), S(24), S(10), S(13)),
                margin = new RectOffset(S(6), S(6), S(4), S(4)),
            };
            s.normal.background = Bevel(fill);
            s.normal.textColor = Color.white;
            s.hover.background = Bevel(hover);
            s.hover.textColor = Color.white;
            s.active.background = Bevel(pressed);
            s.active.textColor = Hex(0xDDEFFA);
            return s;
        }

        private GUIStyle Pill(Color color)
        {
            const int height = 32;
            var s = new GUIStyle { border = new RectOffset(height / 2, height / 2, height / 2 - 1, height / 2 - 1) };
            s.normal.background = Capsule(height + 4, height, color);
            return s;
        }

        private GUIStyle Badge(Color color)
        {
            const int height = 24;
            var s = new GUIStyle
            {
                fontSize = S(15),
                fontStyle = FontStyle.Bold,
                alignment = TextAnchor.MiddleCenter,
                border = new RectOffset(height / 2, height / 2, height / 2 - 1, height / 2 - 1),
                padding = new RectOffset(S(14), S(14), S(5), S(6)),
                margin = new RectOffset(0, 0, S(2), S(8)),
            };
            s.normal.background = Capsule(height + 4, height, color);
            s.normal.textColor = Color.white;
            return s;
        }

        private static void SetAll(GUIStyle style, Texture2D background, Color textColor)
        {
            foreach (GUIStyleState state in new[] { style.normal, style.hover, style.active, style.focused, style.onNormal, style.onHover, style.onActive, style.onFocused })
            {
                state.background = background;
                state.textColor = textColor;
            }
        }

        private static RectOffset Offset(int all)
        {
            return new RectOffset(all, all, all, all);
        }

        private static Color Hex(int rgb)
        {
            return new Color32((byte)(rgb >> 16), (byte)(rgb >> 8), (byte)rgb, 255);
        }

        private static Color Shade(Color c, float factor)
        {
            return new Color(c.r * factor, c.g * factor, c.b * factor, c.a);
        }

        // ---- procedural textures (pixel row 0 is the bottom of the texture) ----

        private static Texture2D Make(int width, int height, Func<int, int, Color> pixel)
        {
            var tex = new Texture2D(width, height, TextureFormat.ARGB32, false)
            {
                hideFlags = HideFlags.HideAndDontSave,
                wrapMode = TextureWrapMode.Clamp,
                filterMode = FilterMode.Bilinear,
            };
            var pixels = new Color[width * height];
            for (int y = 0; y < height; y++)
            {
                for (int x = 0; x < width; x++)
                    pixels[y * width + x] = pixel(x, y);
            }
            tex.SetPixels(pixels);
            tex.Apply();
            return tex;
        }

        private static Texture2D Solid(Color color)
        {
            return Make(2, 2, (x, y) => color);
        }

        private static Texture2D Framed(int size, int frame, Color fill, Color frameColor)
        {
            return Make(size, size, (x, y) =>
                x < frame || y < frame || x >= size - frame || y >= size - frame ? frameColor : fill);
        }

        private static Texture2D Bevel(Color fill)
        {
            const int size = 16;
            Color dark = Shade(fill, 0.62f);
            Color light = Color.Lerp(fill, Color.white, 0.25f);
            return Make(size, size, (x, y) =>
            {
                if (y < 4 || x >= size - 3)
                    return dark;
                if (y == size - 1)
                    return light;
                return fill;
            });
        }

        private static Texture2D Capsule(int width, int height, Color color)
        {
            float r = height / 2f;
            return Make(width, height, (x, y) =>
            {
                float px = x + 0.5f, py = y + 0.5f;
                float cx = Mathf.Clamp(px, r, width - r);
                float dist = Mathf.Sqrt((px - cx) * (px - cx) + (py - r) * (py - r));
                float alpha = Mathf.Clamp01(r - dist + 0.5f);
                return new Color(color.r, color.g, color.b, color.a * alpha);
            });
        }

        private static Texture2D Circle(int size, Color color)
        {
            return Capsule(size, size, color);
        }
    }
}
