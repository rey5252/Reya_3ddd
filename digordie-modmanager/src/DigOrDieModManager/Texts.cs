using System;
using UnityEngine;

namespace DigOrDieModManager
{
    public enum UiLanguage
    {
        Auto,
        English,
        Russian,
        Ukrainian,
    }

    /// <summary>All user-visible strings in English, Russian and Ukrainian.</summary>
    internal sealed class Texts
    {
        public string MenuButton, Title, Summary, SearchHint, FilterAll, FilterEnabled, FilterDisabled;
        public string StatusActive, StatusDisabled, StatusWillEnable, StatusWillDisable, StatusFailed, StatusLibrary;
        public string ThisMod, Config, ShowFile, OpenFolder, Refresh, Close, Restart, RestartNotice, RestartFailed;
        public string NoMods, NoMatches, LoadErrors, ToggleError, Requires, LibraryHint, FailedHint;

        private static readonly Texts English = new Texts
        {
            MenuButton = "MODS",
            Title = "MODS",
            Summary = "Installed: {0} · active: {1} · BepInEx {2}",
            SearchHint = "Search mods…",
            FilterAll = "All",
            FilterEnabled = "Enabled",
            FilterDisabled = "Disabled",
            StatusActive = "ACTIVE",
            StatusDisabled = "DISABLED",
            StatusWillEnable = "ON AFTER RESTART",
            StatusWillDisable = "OFF AFTER RESTART",
            StatusFailed = "NOT LOADED",
            StatusLibrary = "LIBRARY",
            ThisMod = "This is the Mod Manager itself",
            Config = "Config",
            ShowFile = "File",
            OpenFolder = "Open mods folder",
            Refresh = "Refresh",
            Close = "Close",
            Restart = "Restart game",
            RestartNotice = "Changes will take effect after the game is restarted.",
            RestartFailed = "Could not restart automatically. Please start the game again from Steam.",
            NoMods = "No mods yet. Put mod .dll files into the BepInEx/plugins folder and press Refresh.",
            NoMatches = "Nothing found.",
            LoadErrors = "Some mods could not be loaded:",
            ToggleError = "Could not change \"{0}\": {1}",
            Requires = "Requires: {0}",
            LibraryHint = "Helper library used by other mods",
            FailedHint = "Enabled, but BepInEx did not load it (missing dependency or incompatible). See BepInEx/LogOutput.log.",
        };

        private static readonly Texts Russian = new Texts
        {
            MenuButton = "МОДЫ",
            Title = "МОДЫ",
            Summary = "Установлено: {0} · активно: {1} · BepInEx {2}",
            SearchHint = "Поиск модов…",
            FilterAll = "Все",
            FilterEnabled = "Включённые",
            FilterDisabled = "Выключенные",
            StatusActive = "АКТИВЕН",
            StatusDisabled = "ВЫКЛЮЧЕН",
            StatusWillEnable = "ВКЛ. ПОСЛЕ ПЕРЕЗАПУСКА",
            StatusWillDisable = "ВЫКЛ. ПОСЛЕ ПЕРЕЗАПУСКА",
            StatusFailed = "НЕ ЗАГРУЖЕН",
            StatusLibrary = "БИБЛИОТЕКА",
            ThisMod = "Это сам менеджер модов",
            Config = "Настройки",
            ShowFile = "Файл",
            OpenFolder = "Открыть папку модов",
            Refresh = "Обновить",
            Close = "Закрыть",
            Restart = "Перезапустить игру",
            RestartNotice = "Изменения вступят в силу после перезапуска игры.",
            RestartFailed = "Не удалось перезапустить автоматически. Запустите игру снова через Steam.",
            NoMods = "Модов пока нет. Положите .dll файлы модов в папку BepInEx/plugins и нажмите «Обновить».",
            NoMatches = "Ничего не найдено.",
            LoadErrors = "Некоторые моды не загрузились:",
            ToggleError = "Не удалось изменить «{0}»: {1}",
            Requires = "Требует: {0}",
            LibraryHint = "Вспомогательная библиотека для других модов",
            FailedHint = "Включён, но BepInEx его не загрузил (нет зависимости или несовместим). Подробности в BepInEx/LogOutput.log.",
        };

        private static readonly Texts Ukrainian = new Texts
        {
            MenuButton = "МОДИ",
            Title = "МОДИ",
            Summary = "Встановлено: {0} · активних: {1} · BepInEx {2}",
            SearchHint = "Пошук модів…",
            FilterAll = "Усі",
            FilterEnabled = "Увімкнені",
            FilterDisabled = "Вимкнені",
            StatusActive = "АКТИВНИЙ",
            StatusDisabled = "ВИМКНЕНО",
            StatusWillEnable = "УВІМК. ПІСЛЯ ПЕРЕЗАПУСКУ",
            StatusWillDisable = "ВИМК. ПІСЛЯ ПЕРЕЗАПУСКУ",
            StatusFailed = "НЕ ЗАВАНТАЖЕНО",
            StatusLibrary = "БІБЛІОТЕКА",
            ThisMod = "Це сам менеджер модів",
            Config = "Налаштування",
            ShowFile = "Файл",
            OpenFolder = "Відкрити папку модів",
            Refresh = "Оновити",
            Close = "Закрити",
            Restart = "Перезапустити гру",
            RestartNotice = "Зміни запрацюють після перезапуску гри.",
            RestartFailed = "Не вдалося перезапустити автоматично. Запустіть гру знову через Steam.",
            NoMods = "Модів поки немає. Покладіть .dll файли модів у папку BepInEx/plugins і натисніть «Оновити».",
            NoMatches = "Нічого не знайдено.",
            LoadErrors = "Деякі моди не завантажилися:",
            ToggleError = "Не вдалося змінити «{0}»: {1}",
            Requires = "Потребує: {0}",
            LibraryHint = "Допоміжна бібліотека для інших модів",
            FailedHint = "Увімкнений, але BepInEx його не завантажив (немає залежності або несумісний). Деталі в BepInEx/LogOutput.log.",
        };

        public static Texts Current
        {
            get
            {
                switch (ResolveLanguage())
                {
                    case UiLanguage.Ukrainian: return Ukrainian;
                    case UiLanguage.Russian: return Russian;
                    default: return English;
                }
            }
        }

        private static UiLanguage ResolveLanguage()
        {
            UiLanguage configured = ModManagerPlugin.Language != null ? ModManagerPlugin.Language.Value : UiLanguage.Auto;
            if (configured != UiLanguage.Auto)
                return configured;

            if (Application.systemLanguage == SystemLanguage.Ukrainian)
                return UiLanguage.Ukrainian;

            string gameLanguage = GetGameLanguage();
            if (gameLanguage.Contains("rus") || gameLanguage.Contains("рус") || gameLanguage == "ru")
                return UiLanguage.Russian;
            if (gameLanguage.Length == 0
                && (Application.systemLanguage == SystemLanguage.Russian || Application.systemLanguage == SystemLanguage.Belarusian))
                return UiLanguage.Russian;

            return UiLanguage.English;
        }

        private static string GetGameLanguage()
        {
            try
            {
                return (SLoc.GetLanguage() ?? string.Empty).Trim().ToLowerInvariant();
            }
            catch (Exception)
            {
                return string.Empty; // localization not initialized yet
            }
        }
    }
}
