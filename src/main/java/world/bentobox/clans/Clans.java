package world.bentobox.clans;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.Listener;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.scheduler.BukkitRunnable;
import world.bentobox.bentobox.api.addons.Addon;
import world.bentobox.bentobox.api.configuration.Config;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.clans.commands.ClanAdminCommand;
import world.bentobox.clans.commands.ClanCommand;
import world.bentobox.clans.data.PenitenceData;
import world.bentobox.clans.managers.ClanManager;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;

public class Clans extends Addon implements Listener {

    private ClanManager clanManager;
    private Config<Settings> config;
    private Settings settings;
    private Economy economy;
    public final Map<UUID, DisbandRequest> disbandRequests = new HashMap<>();
    public final Map<UUID, CreateRequest> createRequests = new HashMap<>();
    public final Map<UUID, LeaveRequest> leaveRequests = new HashMap<>();
    public final Map<UUID, KickRequest> kickRequests = new HashMap<>();
    public final Map<UUID, BanRequest> banRequests = new HashMap<>();
    private final Map<UUID, Long> penitenceExpirations = new HashMap<>();
    public final Map<UUID, TagRequest> tagRequests = new HashMap<>();
    private world.bentobox.bentobox.database.Database<PenitenceData> penitenceDatabase;
    public final boolean warEventActive = false;

    public static class DisbandRequest {
        public String clanName;
        long timestamp;

        public DisbandRequest(String clanName, long timestamp) {
            this.clanName = clanName;
            this.timestamp = timestamp;
        }
    }

    public static class CreateRequest {
        public String clanName;
        public String tag;
        long timestamp;

        public CreateRequest(String clanName, String tag, long timestamp) {
            this.clanName = clanName;
            this.tag = tag;
            this.timestamp = timestamp;
        }
    }

    public static class LeaveRequest {
        public final String clanName;
        final long timestamp;

        public LeaveRequest(String clanName, long timestamp) {
            this.clanName = clanName;
            this.timestamp = timestamp;
        }
    }

    public static class KickRequest {
        final String clanName;
        public final String targetName;
        final long timestamp;

        public KickRequest(String clanName, String targetName, long timestamp) {
            this.clanName = clanName;
            this.targetName = targetName;
            this.timestamp = timestamp;
        }
    }

    public static class BanRequest {
        final String clanName;
        public final String targetName;
        final long timestamp;

        public BanRequest(String clanName, String targetName, long timestamp) {
            this.clanName = clanName;
            this.targetName = targetName;
            this.timestamp = timestamp;
        }
    }

    // Nueva clase para solicitudes de cambio de tag
    public static class TagRequest {
        public final String clanName;
        public final String newTag;
        public final long timestamp;

        public TagRequest(String clanName, String newTag, long timestamp) {
            this.clanName = clanName;
            this.newTag = newTag;
            this.timestamp = timestamp;
        }
    }

    @Override
    public void onLoad() {
        log("Loading Clans...");
        config = new Config<>(this, Settings.class);
        File dataFolder = getDataFolder();
        if (!dataFolder.exists()) {
            try {
                Files.createDirectories(dataFolder.toPath());
            } catch (IOException e) {
                logError("Error al crear carpeta addons/Clans: " + e.getMessage());
                setState(State.DISABLED);
                return;
            }
        }
        File panelsDir = new File(dataFolder, "panels");
        if (!panelsDir.exists()) {
            try {
                Files.createDirectories(panelsDir.toPath());
            } catch (IOException e) {
                logError("Error al crear carpeta panels: " + e.getMessage());
                setState(State.DISABLED);
                return;
            }
        }
        // Generar clan_list.yml si no existe
        File clanListFile = new File(panelsDir, "clan_list.yml");
        if (!clanListFile.exists()) {
            saveResource(clanListFile);
        }
        // Generar clan_invite.yml si no existe
        File clanInviteFile = new File(panelsDir, "clan_invite.yml");
        if (!clanInviteFile.exists()) {
            saveResource(clanInviteFile);
        }
        if (loadConfiguration()) {
            setState(State.DISABLED);
        }
    }

    private void saveResource(File destination) {
        try {
            if (!destination.exists()) {
                String resourcePath;
                switch (destination.getName()) {
                    case "es-ES.yml" -> resourcePath = "locales/es-ES.yml";
                    case "clan_list.yml" -> resourcePath = "panels/clan_list.yml";
                    case "clan_invite.yml" -> resourcePath = "panels/clan_invite.yml";
                    default -> {
                        logError("Archivo no reconocido: " + destination.getName());
                        return;
                    }
                }
                Files.copy(Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream(resourcePath)), destination.toPath());
                log("Archivo " + destination.getName() + " generado con éxito.");
            }
        } catch (IOException e) {
            logError("Error al copiar " + destination.getName() + ": " + e.getMessage());
        }
    }

    public boolean loadConfiguration() {
        File configFile = new File(getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            saveDefaultConfig();
        }
        settings = config.loadConfigObject();
        if (settings == null) {
            logError("No se pudo cargar config.yml!");
            return true;
        }
        config.saveConfigObject(settings);
        return false;
    }

    public boolean loadLocalizations() {
        File localeDir = new File(getPlugin().getDataFolder(), "locales/Clans");
        File localeFile = new File(localeDir, "es-ES.yml");
        try {
            if (!localeDir.exists()) {
                Files.createDirectories(localeDir.toPath());
            }
            if (!localeFile.exists()) {
                saveResource(localeFile);
            }
            getPlugin().getLocalesManager().loadLocalesFromFile("Clans");
            File[] localeFiles = localeDir.listFiles((dir, name) -> name.endsWith(".yml"));
            List<String> loadedLocales = new ArrayList<>();
            if (localeFiles != null) {
                for (File file : localeFiles) {
                    loadedLocales.add(file.getName().replace(".yml", ""));
                }
            }
            if (loadedLocales.isEmpty()) {
                logError("No se encontraron archivos de idioma en " + localeDir.getAbsolutePath());
                return true;
            }
            return false;
        } catch (Exception e) {
            logError("Error al cargar localizaciones: " + e.getMessage());
            return true;
        }
    }

    private void setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return;
        }
        economy = rsp.getProvider();
    }

    private void startRequestTimer() {
        new BukkitRunnable() {
            @Override
            public void run() {
                long currentTime = System.currentTimeMillis();
                // Limpiar solicitudes de abandono expiradas
                Iterator<Map.Entry<UUID, LeaveRequest>> leaveIterator = leaveRequests.entrySet().iterator();
                while (leaveIterator.hasNext()) {
                    Map.Entry<UUID, LeaveRequest> entry = leaveIterator.next();
                    if (currentTime - entry.getValue().timestamp >= 15000) { // 15 segundos
                        User user = User.getInstance(entry.getKey());
                        if (user.isOnline()) {
                            user.sendMessage(getTranslation(user, "clans.commands.clan.leave.timeout"));
                        }
                        leaveIterator.remove();
                    }
                }
                // Limpiar otras solicitudes
                disbandRequests.entrySet().removeIf(entry -> {
                    if (currentTime - entry.getValue().timestamp >= 15000) {
                        User user = User.getInstance(entry.getKey());
                        if (user.isOnline()) {
                            user.sendMessage(getTranslation(user, "clans.commands.clan.disband.timeout"));
                        }
                        return true;
                    }
                    return false;
                });
                createRequests.entrySet().removeIf(entry -> {
                    if (currentTime - entry.getValue().timestamp >= 15000) {
                        User user = User.getInstance(entry.getKey());
                        if (user.isOnline()) {
                            user.sendMessage(getTranslation(user, "clans.commands.clan.create.timeout"));
                        }
                        return true;
                    }
                    return false;
                });
                kickRequests.entrySet().removeIf(entry -> {
                    if (currentTime - entry.getValue().timestamp >= 15000) {
                        User user = User.getInstance(entry.getKey());
                        if (user.isOnline()) {
                            user.sendMessage(getTranslation(user, "clans.commands.clan.kick.timeout"));
                        }
                        return true;
                    }
                    return false;
                });
                banRequests.entrySet().removeIf(entry -> {
                    if (currentTime - entry.getValue().timestamp >= 15000) {
                        User user = User.getInstance(entry.getKey());
                        if (user.isOnline()) {
                            user.sendMessage(getTranslation(user, "clans.commands.clan.ban.timeout"));
                        }
                        return true;
                    }
                    return false;
                });
                // Limpiar penitencias expiradas
                Iterator<Map.Entry<UUID, Long>> penitenceIterator = penitenceExpirations.entrySet().iterator();
                while (penitenceIterator.hasNext()) {
                    Map.Entry<UUID, Long> entry = penitenceIterator.next();
                    if (currentTime >= entry.getValue()) {
                        penitenceDatabase.deleteID(entry.getKey().toString());
                        penitenceIterator.remove();
                        User user = User.getInstance(entry.getKey());
                        if (user.isOnline()) {
                            user.sendMessage(getTranslation(user, "clans.commands.clan.penitence.expired"));
                        }
                    }
                }
            }
        }.runTaskTimer(getPlugin(), 0L, 20L); // Ejecutar cada segundo (20 ticks)
    }

    private void loadPenitences() {
        penitenceDatabase = new world.bentobox.bentobox.database.Database<>(this, PenitenceData.class);
        List<PenitenceData> penitenceDataList = penitenceDatabase.loadObjects();
        long currentTime = System.currentTimeMillis();
        penitenceExpirations.clear(); // Limpiar el mapa antes de cargar
        for (PenitenceData data : penitenceDataList) {
            if (data.getExpirationTime() > currentTime) {
                penitenceExpirations.put(UUID.fromString(data.getUniqueId()), data.getExpirationTime());
            } else {
                penitenceDatabase.deleteID(data.getUniqueId());
            }
        }
    }

    public void startPenitence(User user) {
        if (!settings.isPenitenceEnabled() || settings.getPenitenceTimeSeconds() <= 0) {
            return;
        }
        UUID playerUUID = user.getUniqueId();
        if (penitenceExpirations.containsKey(playerUUID) || user.hasPermission("clans.commands.clan.penitence.bypass-penitence")) {
            return;
        }
        long expirationTime = System.currentTimeMillis() + (settings.getPenitenceTimeSeconds() * 1000L);
        penitenceExpirations.put(playerUUID, expirationTime);
        penitenceDatabase.saveObject(new PenitenceData(playerUUID.toString(), expirationTime));
        if (user.isOnline()) {
            user.sendMessage(getTranslation(user, "clans.commands.clan.penitence.started",
                    "[time]", formatTime(settings.getPenitenceTimeSeconds())));
        }
    }

    public boolean isUnderPenitence(User user, User requester, boolean sendMessage) {
        if (!settings.isPenitenceEnabled() || settings.getPenitenceTimeSeconds() <= 0) {
            return false;
        }
        if (user.hasPermission("clans.commands.clan.penitence.bypass-penitence")) {
            return false;
        }
        UUID playerUUID = user.getUniqueId();
        Long expiration = penitenceExpirations.get(playerUUID);
        if (expiration == null) {
            return false;
        }
        long currentTime = System.currentTimeMillis();
        if (currentTime >= expiration) {
            penitenceExpirations.remove(playerUUID);
            penitenceDatabase.deleteID(playerUUID.toString());
            if (user.isOnline()) {
                user.sendMessage(getTranslation(user, "clans.commands.clan.penitence.expired"));
            }
            return false;
        }
        // Enviar mensaje solo si sendMessage es true
        if (sendMessage) {
            if (requester != null && !requester.getUniqueId().equals(playerUUID)) {
                requester.sendMessage(getTranslation(requester, "clans.commands.clan.penitence.blocked-third-party",
                        "[player]", user.getName(),
                        "[time]", formatTime(getPenitenceRemainingTime(user))));
            } else if (user.isOnline()) {
                user.sendMessage(getTranslation(user, "clans.commands.clan.penitence.blocked",
                        "[time]", formatTime(getPenitenceRemainingTime(user))));
            }
        }
        return true;
    }

    // Sobrecarga para mantener compatibilidad con las llamadas existentes
    public boolean isUnderPenitence(User user) {
        return isUnderPenitence(user, null, true);
    }

    public long getPenitenceRemainingTime(User user) {
        if (!settings.isPenitenceEnabled() || settings.getPenitenceTimeSeconds() <= 0) {
            return 0;
        }
        if (user.hasPermission("clans.commands.clan.penitence.bypass-penitence")) {
            return 0;
        }
        UUID playerUUID = user.getUniqueId();
        Long expiration = penitenceExpirations.get(playerUUID);
        if (expiration == null) {
            return 0;
        }
        long currentTime = System.currentTimeMillis();
        return Math.max(0, (expiration - currentTime) / 1000); // Segundos restantes
    }

    public void clearPenitence(User user) {
        UUID playerUUID = user.getUniqueId();
        if (penitenceExpirations.remove(playerUUID) != null) {
            penitenceDatabase.deleteID(playerUUID.toString());
            User admin = User.getInstance(user.getUniqueId());
            if (admin.isOnline() && !admin.getUniqueId().equals(playerUUID)) {
                admin.sendMessage(getTranslation(admin, "clans.commands.admin.penitence.clear.success",
                        "[player]", user.getName()));
            }
            if (user.isOnline()) {
                user.sendMessage(getTranslation(user, "clans.commands.clan.penitence.cleared"));
            }
        }
    }

    public String formatTime(long seconds) {
        if (seconds < 60) {
            return seconds + " segundo" + (seconds != 1 ? "s" : "");
        }
        long minutes = seconds / 60;
        seconds %= 60;
        if (minutes < 60) {
            String result = minutes + " minuto" + (minutes != 1 ? "s" : "");
            if (seconds > 0) {
                result += " y " + seconds + " segundo" + (seconds != 1 ? "s" : "");
            }
            return result;
        }
        long hours = minutes / 60;
        minutes %= 60;
        String result = hours + " hora" + (hours != 1 ? "s" : "");
        if (minutes > 0) {
            result += " y " + minutes + " minuto" + (minutes != 1 ? "s" : "");
        }
        return result;
    }

    @Override
    public void onEnable() {
        if (getState() == State.DISABLED) {
            logError("Addon deshabilitado debido a errores previos.");
            return;
        }
        if (settings == null) {
            logError("Settings no inicializados. Verifica config.yml.");
            setState(State.DISABLED);
            return;
        }
        if (loadLocalizations() || !settings.isEnabled()) {
            logError("Localizaciones fallaron o addon deshabilitado en config.");
            setState(State.DISABLED);
            return;
        }
        setupEconomy();
        clanManager = new ClanManager(this);
        clanManager.loadAllClans();
        loadPenitences();
        Bukkit.getPluginManager().registerEvents(this, getPlugin());
        startRequestTimer();
        new ClansPlaceholders(this, getPlugin().getPlaceholdersManager());

        new ClanCommand(this);
        new ClanAdminCommand(this);

        log("Clans enabled");
    }

    @Override
    public void onDisable() {
        if (clanManager != null) {
            clanManager.saveClans();
        }
        log("Clans addon deshabilitado.");
    }

    public ClanManager getClanManager() {
        return clanManager;
    }

    public Settings getSettings() {
        return settings;
    }

    public Economy getEconomy() {
        return economy;
    }

    public YamlConfiguration getLocaleConfig(User user) {
        String userLang = user != null ? user.getLocale().toLanguageTag() : settings.getDefaultLanguage();
        File localeFile = new File(getPlugin().getDataFolder(), "locales/Clans/" + userLang + ".yml");
        File targetFile = localeFile.exists() ? localeFile : new File(getPlugin().getDataFolder(), "locales/Clans/" + settings.getDefaultLanguage() + ".yml");
        return YamlConfiguration.loadConfiguration(targetFile);
    }

    public String getTranslation(User user, String reference, String... variables) {
        YamlConfiguration config = getLocaleConfig(user);
        String prefix = config.getString("clans.prefix", "&6[Clans] &r");
        String translation = config.getString(reference, reference);

        translation = translation.replace("[prefix]", prefix);
        for (int i = 0; i < variables.length - 1; i += 2) {
            String placeholder = variables[i];
            String value = variables[i + 1] != null ? variables[i + 1] : "";
            translation = translation.replace(placeholder, value);
        }

        return serializeWithReset(translation);
    }

    public List<String> getTranslationList(User user, String reference) {
        YamlConfiguration config = getLocaleConfig(user);
        List<String> translations = config.getStringList(reference);
        if (translations.isEmpty()) {
            String singleTranslation = config.getString(reference, reference);
            translations = Collections.singletonList(singleTranslation);
        }
        String prefix = config.getString("clans.prefix", "&6[Clans] &r");
        return translations.stream()
                .map(line -> line.replace("[prefix]", prefix))
                .collect(Collectors.toList());
    }

    public String serializeWithReset(String input) {
        Component component = LegacyComponentSerializer.legacyAmpersand().deserialize(input);
        return LegacyComponentSerializer.legacyAmpersand().serialize(component.append(Component.text("&r")));
    }

    public String stripColor(String input) {
        Component component = LegacyComponentSerializer.legacyAmpersand().deserialize(input);
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    public void sendClanInfo(User user, ClanManager.Clan clan, String showReference) {
        String coloredName = serializeWithReset(clan.getDisplayName());
        String tag = clan.getTag() != null ? clan.getTag() : stripColor(clan.getCleanName()).substring(0, Math.min(3, clan.getCleanName().length()));
        String coloredTag = serializeWithReset(tag);

        OfflinePlayer leader = Bukkit.getOfflinePlayer(UUID.fromString(clan.getOwnerUUID()));
        String leaderName = leader.getName() != null ? leader.getName() : "Desconocido";

        List<String> coLeaderNames = clan.getRanks().entrySet().stream()
                .filter(entry -> entry.getValue() == ClanManager.Clan.Rank.CO_LEADER.getValue())
                .map(entry -> {
                    OfflinePlayer player = Bukkit.getOfflinePlayer(UUID.fromString(entry.getKey()));
                    return player.getName() != null ? player.getName() : "Desconocido";
                })
                .collect(Collectors.toList());
        String coLeadersText = coLeaderNames.isEmpty() ? getTranslation(user, "clans.commands.clan.info.none") : String.join(", ", coLeaderNames);

        List<String> commanderNames = clan.getRanks().entrySet().stream()
                .filter(entry -> entry.getValue() == ClanManager.Clan.Rank.COMMANDER.getValue())
                .map(entry -> {
                    OfflinePlayer player = Bukkit.getOfflinePlayer(UUID.fromString(entry.getKey()));
                    return player.getName() != null ? player.getName() : "Desconocido";
                })
                .collect(Collectors.toList());
        String commandersText = commanderNames.isEmpty() ? getTranslation(user, "clans.commands.clan.info.none") : String.join(", ", commanderNames);

        List<String> allMemberNames = clan.getRanks().keySet().stream()
                .filter(integer -> !integer.equals(clan.getOwnerUUID()))
                .map(integer -> {
                    OfflinePlayer player = Bukkit.getOfflinePlayer(UUID.fromString(integer));
                    return player.getName() != null ? player.getName() : "Desconocido";
                })
                .sorted()
                .collect(Collectors.toList());
        String membersText = allMemberNames.isEmpty() ? getTranslation(user, "clans.commands.clan.info.none") : String.join(", ", allMemberNames);

        List<String> showMessages = getTranslationList(user, showReference);

        for (String message : showMessages) {
            String translated = message
                    .replace("[name]", coloredName)
                    .replace("[tag]", coloredTag)
                    .replace("[rank_leader]", settings.getRanks().getOrDefault("leader", "Líder"))
                    .replace("[leader]", leaderName)
                    .replace("[rank_co_leaders]", settings.getRanks().getOrDefault("co_leaders", "Co-Líderes"))
                    .replace("[count_co_leaders]", String.valueOf(clan.getCoLeaderCount()))
                    .replace("[max_co_leaders]", String.valueOf(clan.getMaxCoLeaders()))
                    .replace("[co_leaders]", coLeadersText)
                    .replace("[rank_commanders]", settings.getRanks().getOrDefault("commanders", "Comandantes"))
                    .replace("[count_commanders]", String.valueOf(clan.getCommanderCount()))
                    .replace("[max_commanders]", String.valueOf(clan.getMaxCommanders()))
                    .replace("[commanders]", commandersText)
                    .replace("[rank_members]", settings.getRanks().getOrDefault("members", "Miembros"))
                    .replace("[count_members]", String.valueOf(clan.getTotalMemberCount()))
                    .replace("[max_members]", String.valueOf(clan.getMaxMembers()))
                    .replace("[members]", membersText);
            user.sendMessage(translated);
        }
    }
}