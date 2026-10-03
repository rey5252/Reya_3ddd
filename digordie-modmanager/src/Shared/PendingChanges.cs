using System;
using System.Collections.Generic;
using System.IO;
using System.Text;

namespace DigOrDieModManager
{
    /// <summary>
    /// Enable/disable requests that could not be applied while the game was running
    /// (Windows can keep a loaded .dll locked). They are stored in a small text file and
    /// applied on the next launch by the preloader patcher, before BepInEx loads any plugin.
    /// File format: one "enable|plugins/Some/Mod.dll" or "disable|plugins/Some/Mod.dll" per line,
    /// paths relative to the BepInEx folder.
    /// </summary>
    internal static class PendingChanges
    {
        public const string DisabledSuffix = ".disabled";
        public const string FileName = "DigOrDieModManager.pending.txt";

        public static string GetListPath(string bepInExRoot)
        {
            return Path.Combine(Path.Combine(bepInExRoot, "config"), FileName);
        }

        /// <summary>Key: enabled path of the mod relative to the BepInEx folder; value: wanted state.</summary>
        public static Dictionary<string, bool> Load(string bepInExRoot)
        {
            var result = new Dictionary<string, bool>(StringComparer.OrdinalIgnoreCase);
            string listPath = GetListPath(bepInExRoot);
            if (!File.Exists(listPath))
                return result;

            foreach (string rawLine in File.ReadAllLines(listPath))
            {
                string line = rawLine.Trim();
                int sep = line.IndexOf('|');
                if (sep <= 0 || sep == line.Length - 1)
                    continue;
                string action = line.Substring(0, sep);
                string relPath = NormalizeRelative(line.Substring(sep + 1));
                if (action == "enable")
                    result[relPath] = true;
                else if (action == "disable")
                    result[relPath] = false;
            }
            return result;
        }

        public static void Save(string bepInExRoot, Dictionary<string, bool> changes)
        {
            string listPath = GetListPath(bepInExRoot);
            if (changes.Count == 0)
            {
                if (File.Exists(listPath))
                    File.Delete(listPath);
                return;
            }

            var sb = new StringBuilder();
            foreach (var pair in changes)
                sb.Append(pair.Value ? "enable|" : "disable|").Append(pair.Key).Append('\n');

            Directory.CreateDirectory(Path.GetDirectoryName(listPath));
            File.WriteAllText(listPath, sb.ToString());
        }

        /// <summary>
        /// Renames "Mod.dll" &lt;-&gt; "Mod.dll.disabled" for every stored request.
        /// Requests that still fail stay in the list for the next attempt.
        /// </summary>
        public static void ApplyAll(string bepInExRoot, Action<string> logInfo, Action<string> logWarning)
        {
            Dictionary<string, bool> changes = Load(bepInExRoot);
            if (changes.Count == 0)
                return;

            var remaining = new Dictionary<string, bool>(StringComparer.OrdinalIgnoreCase);
            foreach (var pair in changes)
            {
                string enabledPath = Path.Combine(bepInExRoot, pair.Key.Replace('/', Path.DirectorySeparatorChar));
                try
                {
                    if (TryApply(enabledPath, pair.Value))
                        logInfo((pair.Value ? "Enabled " : "Disabled ") + pair.Key);
                }
                catch (Exception e)
                {
                    logWarning("Could not " + (pair.Value ? "enable " : "disable ") + pair.Key + ": " + e.Message);
                    remaining[pair.Key] = pair.Value;
                }
            }
            Save(bepInExRoot, remaining);
        }

        /// <summary>Returns true when a file was renamed, false when nothing had to be done.</summary>
        public static bool TryApply(string enabledPath, bool enable)
        {
            string disabledPath = enabledPath + DisabledSuffix;
            string from = enable ? disabledPath : enabledPath;
            string to = enable ? enabledPath : disabledPath;

            if (!File.Exists(from))
                return false; // already in the wanted state, or the mod was deleted
            if (File.Exists(to))
                throw new IOException("both \"" + Path.GetFileName(from) + "\" and \"" + Path.GetFileName(to) + "\" exist");

            File.Move(from, to);
            return true;
        }

        public static string MakeRelative(string bepInExRoot, string fullPath)
        {
            string root = Path.GetFullPath(bepInExRoot).TrimEnd('\\', '/') + Path.DirectorySeparatorChar;
            string full = Path.GetFullPath(fullPath);
            if (full.StartsWith(root, StringComparison.OrdinalIgnoreCase))
                full = full.Substring(root.Length);
            return NormalizeRelative(full);
        }

        private static string NormalizeRelative(string path)
        {
            return path.Trim().Replace('\\', '/');
        }
    }
}
