using System;
using System.Diagnostics;
using UnityEngine;

namespace DigOrDieModManager
{
    /// <summary>Opening folders/files in Windows and restarting the game through Steam.</summary>
    internal static class SystemShell
    {
        public const int SteamAppId = 315460; // Dig or Die

        public static void OpenFolder(string path)
        {
            Run("explorer.exe", Quote(path), path);
        }

        public static void ShowFile(string path)
        {
            Run("explorer.exe", "/select," + Quote(path), System.IO.Path.GetDirectoryName(path));
        }

        public static void OpenTextFile(string path)
        {
            Run("notepad.exe", Quote(path), path);
        }

        /// <summary>
        /// Starts a hidden helper that waits for this game process to exit and then launches the
        /// game again through Steam (BepInEx only works when the game is started by Steam), then quits.
        /// </summary>
        public static bool RestartGame()
        {
            try
            {
                int pid = Process.GetCurrentProcess().Id;
                string script = "Wait-Process -Id " + pid + " -ErrorAction SilentlyContinue; Start-Sleep -Seconds 2; "
                                + "Start-Process 'steam://rungameid/" + SteamAppId + "'";
                var info = new ProcessStartInfo("powershell.exe",
                    "-NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -Command \"" + script + "\"")
                {
                    UseShellExecute = false,
                    CreateNoWindow = true,
                };
                Process.Start(info);
            }
            catch (Exception e)
            {
                ModManagerPlugin.Log.LogWarning("Could not restart the game: " + e.Message);
                return false;
            }

            ModManagerPlugin.Log.LogInfo("Restarting the game to apply mod changes");
            Application.Quit();
            return true;
        }

        private static void Run(string program, string arguments, string fallbackPath)
        {
            try
            {
                Process.Start(program, arguments);
            }
            catch (Exception e)
            {
                ModManagerPlugin.Log.LogDebug(program + " failed (" + e.Message + "), using OpenURL");
                Application.OpenURL("file:///" + fallbackPath.Replace('\\', '/'));
            }
        }

        private static string Quote(string path)
        {
            return "\"" + path + "\"";
        }
    }
}
