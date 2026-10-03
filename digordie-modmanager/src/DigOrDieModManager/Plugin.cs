using BepInEx;
using BepInEx.Configuration;
using BepInEx.Logging;
using HarmonyLib;
using UnityEngine;

namespace DigOrDieModManager
{
    [BepInPlugin(Guid, PluginName, PluginVersion)]
    public sealed class ModManagerPlugin : BaseUnityPlugin
    {
        public const string Guid = "reya.digordie.modmanager";
        public const string PluginName = "Mod Manager";
        public const string PluginVersion = "1.0.1";

        internal static ManualLogSource Log;
        internal static ConfigEntry<UiLanguage> Language;
        internal static ConfigEntry<bool> ButtonInMenuColumn;
        internal static ConfigEntry<KeyboardShortcut> OpenHotkey;

        private ModManagerWindow _window;

        private void Awake()
        {
            Log = Logger;

            Language = Config.Bind("General", "Language", UiLanguage.Auto,
                "Language of the Mod Manager. Auto: Ukrainian on a Ukrainian system, otherwise the game's language (Russian or English).");
            ButtonInMenuColumn = Config.Bind("General", "ButtonInMenuColumn", true,
                "true: the MODS button is placed in the main menu column, between Options and Quit. false: it is placed at the bottom of the screen.");
            OpenHotkey = Config.Bind("General", "OpenHotkey", KeyboardShortcut.Empty,
                "Optional key that opens the Mod Manager from anywhere (for example F8). Empty = only the menu button.");

            // Pending enable/disable changes are applied by the preloader before plugins load,
            // so what is on disk now is exactly what BepInEx loaded.
            ModCatalog.TakeStartupSnapshot();

            _window = new ModManagerWindow();
            ModManagerWindow.Instance = _window;

            var harmony = new Harmony(Guid);
            Patch(harmony, typeof(MainMenuPatches));
            Patch(harmony, typeof(InputLockPatch));
            Log.LogInfo("Mod Manager " + PluginVersion + " loaded, waiting for the main menu");
        }

        private static void Patch(Harmony harmony, System.Type patches)
        {
            try
            {
                harmony.PatchAll(patches);
            }
            catch (System.Exception e)
            {
                Log.LogError("Failed to apply " + patches.Name + " (unsupported game version?): " + e);
            }
        }

        private void Update()
        {
            if (OpenHotkey.Value.MainKey != KeyCode.None && OpenHotkey.Value.IsDown())
            {
                if (_window.IsOpen)
                    _window.Close();
                else
                    _window.Open();
            }
        }

        private void OnGUI()
        {
            _window.OnGUI();
        }
    }
}
