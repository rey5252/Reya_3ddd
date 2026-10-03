using System.Collections.Generic;
using BepInEx;
using BepInEx.Logging;
using Mono.Cecil;

namespace DigOrDieModManager
{
    /// <summary>
    /// BepInEx preloader patcher. It does not patch anything: it only runs early enough
    /// (before the chainloader scans BepInEx/plugins) to rename mods that the in-game
    /// Mod Manager could not rename while their .dll was loaded.
    /// </summary>
    public static class ModManagerPreloader
    {
        private static readonly ManualLogSource Log = Logger.CreateLogSource("ModManager.Preloader");

        public static IEnumerable<string> TargetDLLs { get; } = new string[0];

        public static void Initialize()
        {
            PendingChanges.ApplyAll(Paths.BepInExRootPath, Log.LogInfo, Log.LogWarning);
        }

        public static void Patch(AssemblyDefinition assembly)
        {
        }
    }
}
