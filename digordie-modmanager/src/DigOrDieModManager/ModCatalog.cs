using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using BepInEx;
using BepInEx.Bootstrap;
using Mono.Cecil;

namespace DigOrDieModManager
{
    internal enum ModStatus
    {
        Active,
        Disabled,
        WillEnable,
        WillDisable,
        Failed,
        Library,
    }

    internal sealed class PluginMeta
    {
        public string Guid;
        public string Name;
        public string Version;
    }

    /// <summary>One .dll in BepInEx/plugins (enabled "Mod.dll" or disabled "Mod.dll.disabled").</summary>
    internal sealed class ModEntry
    {
        public string EnabledPath;   // full path of "Mod.dll", whether or not it is disabled right now
        public string RelativePath;  // "plugins/Folder/Mod.dll", relative to the BepInEx folder
        public string DisplayPath;   // "Folder/Mod.dll", relative to BepInEx/plugins
        public bool EnabledOnDisk;
        public bool WantedEnabled;   // EnabledOnDisk, or the state waiting in the pending list
        public bool HasPendingChange;
        public bool Loaded;
        public bool EnabledAtStartup;
        public bool IsSelf;
        public bool NotDotNet;
        public List<PluginMeta> Plugins = new List<PluginMeta>();
        public List<string> Dependencies = new List<string>();
        public string Description;
        public string ConfigPath;
        public string SearchText;

        public string CurrentPath
        {
            get { return EnabledOnDisk ? EnabledPath : EnabledPath + PendingChanges.DisabledSuffix; }
        }

        public bool IsLibrary
        {
            get { return Plugins.Count == 0; }
        }

        public string Name
        {
            get
            {
                if (Plugins.Count == 0)
                    return Path.GetFileNameWithoutExtension(EnabledPath);
                string name = Plugins[0].Name;
                return Plugins.Count > 1 ? name + " (+" + (Plugins.Count - 1) + ")" : name;
            }
        }

        public string Version
        {
            get { return Plugins.Count > 0 ? Plugins[0].Version : null; }
        }

        public ModStatus Status
        {
            get
            {
                if (IsLibrary)
                {
                    if (WantedEnabled == EnabledAtStartup)
                        return WantedEnabled ? ModStatus.Library : ModStatus.Disabled;
                    return WantedEnabled ? ModStatus.WillEnable : ModStatus.WillDisable;
                }
                if (Loaded)
                    return WantedEnabled ? ModStatus.Active : ModStatus.WillDisable;
                if (!WantedEnabled)
                    return ModStatus.Disabled;
                return EnabledAtStartup ? ModStatus.Failed : ModStatus.WillEnable;
            }
        }
    }

    /// <summary>Finds mods in BepInEx/plugins and turns them on and off.</summary>
    internal static class ModCatalog
    {
        private sealed class CachedMetadata
        {
            public long Length;
            public DateTime WriteTime;
            public bool NotDotNet;
            public List<PluginMeta> Plugins;
            public List<string> Dependencies;
            public string Description;
        }

        private static readonly Dictionary<string, CachedMetadata> MetadataCache = new Dictionary<string, CachedMetadata>(StringComparer.OrdinalIgnoreCase);
        private static HashSet<string> _enabledAtStartup = new HashSet<string>(StringComparer.OrdinalIgnoreCase);

        /// <summary>Remembers which mods BepInEx saw when the game started.</summary>
        public static void TakeStartupSnapshot()
        {
            _enabledAtStartup = new HashSet<string>(
                FindModFiles().Where(f => !IsDisabledFile(f)).Select(f => Normalize(f)),
                StringComparer.OrdinalIgnoreCase);
        }

        public static List<ModEntry> Scan()
        {
            string root = Paths.BepInExRootPath;
            Dictionary<string, bool> pending = PendingChanges.Load(root);

            var loadedByPath = new Dictionary<string, List<PluginInfo>>(StringComparer.OrdinalIgnoreCase);
            foreach (PluginInfo info in Chainloader.PluginInfos.Values)
            {
                if (string.IsNullOrEmpty(info.Location))
                    continue;
                string key = Normalize(info.Location);
                List<PluginInfo> list;
                if (!loadedByPath.TryGetValue(key, out list))
                    loadedByPath[key] = list = new List<PluginInfo>();
                list.Add(info);
            }

            var entries = new Dictionary<string, ModEntry>(StringComparer.OrdinalIgnoreCase);
            foreach (string file in FindModFiles())
            {
                bool disabled = IsDisabledFile(file);
                string enabledPath = disabled ? file.Substring(0, file.Length - PendingChanges.DisabledSuffix.Length) : file;
                string key = Normalize(enabledPath);

                ModEntry existing;
                if (entries.TryGetValue(key, out existing))
                {
                    // Both "Mod.dll" and "Mod.dll.disabled" exist: the enabled one is what BepInEx loads.
                    if (!disabled)
                        existing.EnabledOnDisk = true;
                    continue;
                }

                var entry = new ModEntry
                {
                    EnabledPath = enabledPath,
                    RelativePath = PendingChanges.MakeRelative(root, enabledPath),
                    DisplayPath = PendingChanges.MakeRelative(Paths.PluginPath, enabledPath),
                    EnabledOnDisk = !disabled,
                    EnabledAtStartup = _enabledAtStartup.Contains(key),
                };

                List<PluginInfo> loaded;
                if (loadedByPath.TryGetValue(key, out loaded))
                {
                    entry.Loaded = true;
                    foreach (PluginInfo info in loaded)
                    {
                        entry.Plugins.Add(new PluginMeta
                        {
                            Guid = info.Metadata.GUID,
                            Name = info.Metadata.Name,
                            Version = info.Metadata.Version.ToString(),
                        });
                    }
                }

                CachedMetadata meta = ReadMetadata(file);
                entry.NotDotNet = meta.NotDotNet;
                entry.Description = meta.Description;
                entry.Dependencies = meta.Dependencies;
                if (entry.Plugins.Count == 0)
                    entry.Plugins = meta.Plugins;

                entries[key] = entry;
            }

            foreach (ModEntry entry in entries.Values)
            {
                bool wanted;
                entry.HasPendingChange = pending.TryGetValue(entry.RelativePath, out wanted) && wanted != entry.EnabledOnDisk;
                entry.WantedEnabled = entry.HasPendingChange ? wanted : entry.EnabledOnDisk;
                entry.IsSelf = entry.Plugins.Any(p => p.Guid == ModManagerPlugin.Guid);
                entry.ConfigPath = FindConfig(entry);
                entry.SearchText = (entry.Name + " " + entry.DisplayPath + " " + string.Join(" ", entry.Plugins.Select(p => p.Guid).ToArray())
                                    + " " + entry.Description).ToLowerInvariant();
            }

            return entries.Values
                .OrderBy(e => e.IsLibrary)
                .ThenBy(e => e.Name, StringComparer.OrdinalIgnoreCase)
                .ToList();
        }

        /// <summary>
        /// Turns a mod on or off by renaming "Mod.dll" &lt;-&gt; "Mod.dll.disabled".
        /// If Windows keeps the file locked, the change is queued for the next launch.
        /// Returns an error message, or null on success.
        /// </summary>
        public static string SetEnabled(ModEntry entry, bool enable)
        {
            if (entry.IsSelf)
                return null;

            string root = Paths.BepInExRootPath;
            Dictionary<string, bool> pending = PendingChanges.Load(root);
            pending.Remove(entry.RelativePath);

            string error = null;
            if (File.Exists(entry.EnabledPath) && File.Exists(entry.EnabledPath + PendingChanges.DisabledSuffix))
            {
                error = "both \"" + Path.GetFileName(entry.EnabledPath) + "\" and \"" + Path.GetFileName(entry.EnabledPath)
                        + PendingChanges.DisabledSuffix + "\" exist, remove one of them";
            }
            else
            {
                try
                {
                    PendingChanges.TryApply(entry.EnabledPath, enable);
                }
                catch (Exception e)
                {
                    if (e is IOException || e is UnauthorizedAccessException)
                    {
                        pending[entry.RelativePath] = enable;
                        ModManagerPlugin.Log.LogInfo("\"" + entry.DisplayPath + "\" is in use, it will be "
                                                     + (enable ? "enabled" : "disabled") + " on the next launch");
                    }
                    else
                    {
                        error = e.Message;
                    }
                }
            }

            try
            {
                PendingChanges.Save(root, pending);
            }
            catch (Exception e)
            {
                error = error ?? e.Message;
            }

            if (error != null)
                ModManagerPlugin.Log.LogWarning("Could not change \"" + entry.DisplayPath + "\": " + error);
            else
                ModManagerPlugin.Log.LogInfo((enable ? "Enabled " : "Disabled ") + entry.DisplayPath);
            return error;
        }

        /// <summary>Retries queued changes (they normally are applied by the preloader at launch).</summary>
        public static void RetryPending()
        {
            PendingChanges.ApplyAll(Paths.BepInExRootPath, ModManagerPlugin.Log.LogInfo, ModManagerPlugin.Log.LogDebug);
        }

        public static string[] GetLoadErrors()
        {
            return Chainloader.DependencyErrors.ToArray();
        }

        private static IEnumerable<string> FindModFiles()
        {
            if (!Directory.Exists(Paths.PluginPath))
                return new string[0];
            return Directory.GetFiles(Paths.PluginPath, "*", SearchOption.AllDirectories)
                .Where(f => f.EndsWith(".dll", StringComparison.OrdinalIgnoreCase) || IsDisabledFile(f));
        }

        private static bool IsDisabledFile(string path)
        {
            return path.EndsWith(".dll" + PendingChanges.DisabledSuffix, StringComparison.OrdinalIgnoreCase);
        }

        private static string FindConfig(ModEntry entry)
        {
            foreach (PluginMeta plugin in entry.Plugins)
            {
                if (string.IsNullOrEmpty(plugin.Guid))
                    continue;
                string path = Path.Combine(Paths.ConfigPath, plugin.Guid + ".cfg");
                if (File.Exists(path))
                    return path;
            }
            return null;
        }

        private static CachedMetadata ReadMetadata(string file)
        {
            var info = new FileInfo(file);
            CachedMetadata cached;
            if (MetadataCache.TryGetValue(file, out cached) && cached.Length == info.Length && cached.WriteTime == info.LastWriteTimeUtc)
                return cached;

            cached = new CachedMetadata
            {
                Length = info.Length,
                WriteTime = info.LastWriteTimeUtc,
                Plugins = new List<PluginMeta>(),
                Dependencies = new List<string>(),
            };

            try
            {
                // Read from memory so the file is never kept open (it may need to be renamed later).
                // The resolver is needed to decode enum arguments such as BepInDependency's DependencyFlags;
                // disposing it closes every assembly it opened.
                using (var resolver = CreateResolver(file))
                using (var stream = new MemoryStream(File.ReadAllBytes(file)))
                using (AssemblyDefinition assembly = AssemblyDefinition.ReadAssembly(stream, new ReaderParameters { AssemblyResolver = resolver }))
                {
                    foreach (CustomAttribute attribute in assembly.CustomAttributes)
                    {
                        if (attribute.AttributeType.FullName == "System.Reflection.AssemblyDescriptionAttribute"
                            && attribute.ConstructorArguments.Count == 1)
                        {
                            cached.Description = attribute.ConstructorArguments[0].Value as string;
                        }
                    }

                    foreach (TypeDefinition type in AllTypes(assembly.MainModule.Types))
                        ReadPluginAttributes(type, cached);
                }
            }
            catch (BadImageFormatException)
            {
                cached.NotDotNet = true; // a native helper .dll, not a .NET mod
            }
            catch (Exception e)
            {
                ModManagerPlugin.Log.LogDebug("Could not read " + file + ": " + e.Message);
            }

            cached.Dependencies = cached.Dependencies.Distinct().ToList();
            MetadataCache[file] = cached;
            return cached;
        }

        private static DefaultAssemblyResolver CreateResolver(string file)
        {
            var resolver = new DefaultAssemblyResolver();
            string bepInExCore = null;
            try
            {
                bepInExCore = Path.GetDirectoryName(typeof(Paths).Assembly.Location);
            }
            catch (Exception)
            {
                // Location is not available for assemblies loaded from memory
            }

            var dirs = new[]
            {
                Path.GetDirectoryName(file), bepInExCore, Paths.BepInExAssemblyDirectory,
                Path.Combine(Paths.BepInExRootPath, "core"), Paths.ManagedPath, Paths.PluginPath,
            };
            foreach (string dir in dirs.Where(d => !string.IsNullOrEmpty(d)).Distinct(StringComparer.OrdinalIgnoreCase))
            {
                if (Directory.Exists(dir))
                    resolver.AddSearchDirectory(dir);
            }
            return resolver;
        }

        private static void ReadPluginAttributes(TypeDefinition type, CachedMetadata into)
        {
            PluginMeta plugin = null;
            var dependencies = new List<string>();

            foreach (CustomAttribute attribute in type.CustomAttributes)
            {
                string name = attribute.AttributeType.FullName;
                if (name != "BepInEx.BepInPlugin" && name != "BepInEx.BepInDependency")
                    continue;
                try
                {
                    if (name == "BepInEx.BepInPlugin" && attribute.ConstructorArguments.Count >= 3)
                    {
                        plugin = new PluginMeta
                        {
                            Guid = attribute.ConstructorArguments[0].Value as string,
                            Name = attribute.ConstructorArguments[1].Value as string,
                            Version = attribute.ConstructorArguments[2].Value as string,
                        };
                    }
                    else if (name == "BepInEx.BepInDependency" && attribute.ConstructorArguments.Count >= 1)
                    {
                        object flags = attribute.ConstructorArguments.Count >= 2 ? attribute.ConstructorArguments[1].Value : null;
                        bool soft = flags is int && ((int)flags & 2) != 0; // DependencyFlags.SoftDependency
                        string guid = attribute.ConstructorArguments[0].Value as string;
                        if (!soft && !string.IsNullOrEmpty(guid))
                            dependencies.Add(guid);
                    }
                }
                catch (Exception e)
                {
                    // An argument that cannot be decoded only loses that one attribute.
                    ModManagerPlugin.Log.LogDebug("Could not read " + name + " on " + type.FullName + ": " + e.Message);
                }
            }

            if (plugin == null)
                return;
            into.Plugins.Add(plugin);
            into.Dependencies.AddRange(dependencies);
        }

        private static IEnumerable<TypeDefinition> AllTypes(IEnumerable<TypeDefinition> types)
        {
            foreach (TypeDefinition type in types)
            {
                yield return type;
                if (type.HasNestedTypes)
                {
                    foreach (TypeDefinition nested in AllTypes(type.NestedTypes))
                        yield return nested;
                }
            }
        }

        private static string Normalize(string path)
        {
            try
            {
                return Path.GetFullPath(path);
            }
            catch (Exception)
            {
                return path;
            }
        }
    }
}
