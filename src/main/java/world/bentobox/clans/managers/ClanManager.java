package world.bentobox.clans.managers;

import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.database.Database;
import world.bentobox.clans.Clans;
import world.bentobox.clans.data.ClanData;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public class ClanManager {

    private final Clans addon;
    private final Database<ClanData> database;
    private final Map<String, Clan> clans;

    public ClanManager(Clans addon) {
        this.addon = addon;
        this.database = new Database<>(addon, ClanData.class);
        this.clans = new HashMap<>();
    }

    public void loadAllClans() {
        try {
            List<ClanData> clanDataList = database.loadObjects();
            clans.clear();
            for (ClanData data : clanDataList) {
                try {
                    if (data.getUniqueId() == null || data.getCleanName() == null) {
                        continue;
                    }
                    Clan clan = new Clan(data);
                    clans.put(data.getUniqueId(), clan);
                } catch (Exception e) {
                    addon.logError("Error al cargar clan " + data.getUniqueId() + ": " + e.getMessage());
                }
            }
        } catch (Exception e) {
            addon.logError("Error al cargar clanes desde la base de datos: " + e.getMessage());
        }
    }

    private Clan loadClan(String uniqueId) {
        if (clans.containsKey(uniqueId)) {
            return clans.get(uniqueId);
        }
        ClanData data = database.loadObject(uniqueId);
        if (data != null) {
            Clan clan = new Clan(data);
            clans.put(uniqueId, clan);
            return clan;
        }
        return null;
    }

    public void saveClans() {
        for (Clan clan : clans.values()) {
            try {
                ClanData data = new ClanData(
                        clan.getUniqueId(), clan.getCleanName(), clan.getDisplayName(), clan.getTag(), clan.getOwnerUUID(),
                        clan.getRanks(), clan.getMaxCoLeaders(), clan.getMaxCommanders(), clan.getMaxMembers(),
                        new ArrayList<>(clan.getBannedPlayers()), clan.getLastTagChangeTimestamp()
                );
                database.saveObject(data);
            } catch (Exception e) {
                addon.logError("Error saving clan " + clan.getUniqueId() + ": " + e.getMessage());
            }
        }
    }

    public Optional<Clan> getClanByName(String name) {
        String cleanInput = addon.stripColor(name);
        return clans.values().stream()
                .filter(clan -> clan.getCleanName().equalsIgnoreCase(cleanInput))
                .findFirst();
    }

    public List<Clan> getAllClans() {
        return new ArrayList<>(clans.values());
    }

    public boolean isClanNameTaken(String displayName) {
        String cleanName = PlainTextComponentSerializer.plainText().serialize(
                LegacyComponentSerializer.legacyAmpersand().deserialize(displayName)
        );
        List<ClanData> existingClans = database.loadObjects();
        return existingClans.stream().anyMatch(data -> {
            String existingCleanName = data.getCleanName();
            return existingCleanName != null && existingCleanName.equalsIgnoreCase(cleanName);
        });
    }

    public String createClan(String displayName, String tag, String ownerUUID, int maxCoLeaders, int maxCommanders, int maxMembers) {
        try {
            String cleanName = PlainTextComponentSerializer.plainText().serialize(
                    LegacyComponentSerializer.legacyAmpersand().deserialize(displayName)
            );

            if (isClanNameTaken(displayName)) {
                return null;
            }

            String uniqueId = UUID.randomUUID().toString();
            String finalTag = tag != null ? LegacyComponentSerializer.legacyAmpersand().serialize(
                    LegacyComponentSerializer.legacyAmpersand().deserialize(tag.replace("&&", "&"))
            ) : cleanName.substring(0, Math.min(3, cleanName.length()));
            Clan clan = new Clan(uniqueId, cleanName, displayName, finalTag, ownerUUID, maxCoLeaders, maxCommanders, maxMembers);
            clan.addMember(ownerUUID, Clan.Rank.LEADER);

            ClanData data = new ClanData(
                    uniqueId, cleanName, displayName, finalTag, ownerUUID,
                    clan.getRanks(), maxCoLeaders, maxCommanders, maxMembers, new ArrayList<>(), 0L
            );

            try {
                database.saveObject(data);
            } catch (Exception e) {
                addon.logError("Error saving clan to database: " + e.getMessage());
                return null;
            }

            clans.put(uniqueId, clan);
            return uniqueId;
        } catch (Exception e) {
            addon.logError("Error during clan creation: " + e.getMessage());
            return null;
        }
    }

    public CompletableFuture<Boolean> disbandClan(String uniqueId) {
        return CompletableFuture.supplyAsync(() -> {
            Clan clan = loadClan(uniqueId);
            if (clan != null) {
                clans.remove(uniqueId);
                database.deleteID(uniqueId);
                return true;
            }
            return false;
        });
    }

    public CompletableFuture<Boolean> setClanTag(String uniqueId, String tag) {
        return CompletableFuture.supplyAsync(() -> {
            Clan clan = loadClan(uniqueId);
            if (clan != null) {
                String cleanTag = PlainTextComponentSerializer.plainText().serialize(
                        LegacyComponentSerializer.legacyAmpersand().deserialize(tag)
                );
                if (database.loadObjects().stream().anyMatch(data ->
                        !data.getUniqueId().equals(uniqueId) &&
                                PlainTextComponentSerializer.plainText().serialize(
                                        LegacyComponentSerializer.legacyAmpersand().deserialize(data.getTag())
                                ).equalsIgnoreCase(cleanTag))) {
                    return false;
                }
                String formattedTag = LegacyComponentSerializer.legacyAmpersand().serialize(
                        LegacyComponentSerializer.legacyAmpersand().deserialize(tag.replace("&&", "&"))
                );
                clan.setTag(formattedTag);
                clan.setLastTagChangeTimestamp(System.currentTimeMillis());
                database.saveObject(new ClanData(uniqueId, clan.getCleanName(), clan.getDisplayName(), formattedTag, clan.getOwnerUUID(),
                        clan.getRanks(), clan.getMaxCoLeaders(), clan.getMaxCommanders(), clan.getMaxMembers(),
                        new ArrayList<>(clan.getBannedPlayers()), clan.getLastTagChangeTimestamp()));
                return true;
            }
            return false;
        });
    }

    public Optional<Clan> getClanByPlayer(String playerUUID) {
        return clans.values().stream()
                .filter(clan -> clan.getRanks().containsKey(playerUUID))
                .findFirst();
    }

    public String getClanNameByPlayer(String playerUUID) {
        return getClanByPlayer(playerUUID)
                .map(Clan::getDisplayName)
                .orElse(null);
    }

    public boolean playerHasClan(String playerUUID) {
        return getClanByPlayer(playerUUID).isPresent();
    }

    public boolean isValidClanName(String cleanName, int minLength, int maxLength) {
        if (cleanName == null || cleanName.length() < minLength || cleanName.length() > maxLength) {
            return false;
        }
        return cleanName.matches("^[a-zA-Z0-9]+$");
    }

    public boolean isValidTag(String cleanTag) {
        if (cleanTag == null || cleanTag.isEmpty() || cleanTag.length() > 3) {
            return false;
        }
        return cleanTag.matches("^[a-zA-Z0-9]+$");
    }

    public CompletableFuture<Boolean> addMember(String clanId, String playerUUID, Clan.Rank rank) {
        return CompletableFuture.supplyAsync(() -> {
            Clan clan = loadClan(clanId);
            if (clan == null) {
                return false;
            }
            User user = User.getInstance(UUID.fromString(playerUUID));
            if (addon.isUnderPenitence(user)) {
                return false;
            }
            if (clan.getTotalMemberCount() >= clan.getMaxMembers()) {
                return false;
            }
            if (rank == Clan.Rank.CO_LEADER && clan.getCoLeaderCount() >= clan.getMaxCoLeaders()) {
                return false;
            }
            if (rank == Clan.Rank.COMMANDER && clan.getCommanderCount() >= clan.getMaxCommanders()) {
                return false;
            }
            if (clan.getRanks().containsKey(playerUUID)) {
                return false;
            }
            if (clan.isBanned(playerUUID)) {
                return false;
            }
            clan.addMember(playerUUID, rank);
            database.saveObject(new ClanData(
                    clan.getUniqueId(), clan.getCleanName(), clan.getDisplayName(), clan.getTag(), clan.getOwnerUUID(),
                    clan.getRanks(), clan.getMaxCoLeaders(), clan.getMaxCommanders(), clan.getMaxMembers(),
                    new ArrayList<>(clan.getBannedPlayers()), clan.getLastTagChangeTimestamp()
            ));
            return true;
        });
    }

    public class Clan {
        private final String uniqueId;
        private String cleanName;
        private String displayName;
        private String tag;
        private String ownerUUID;
        private final Map<String, Integer> ranks;
        private final int maxCoLeaders;
        private final int maxCommanders;
        private final int maxMembers;
        private final Set<String> bannedPlayers;
        private long lastTagChangeTimestamp;

        public enum Rank {
            LEADER(3),
            CO_LEADER(2),
            COMMANDER(1),
            MEMBER(0);

            private final int value;

            Rank(int value) {
                this.value = value;
            }

            public int getValue() {
                return value;
            }
        }

        public Clan(String uniqueId, String cleanName, String displayName, String tag, String ownerUUID, int maxCoLeaders, int maxCommanders, int maxMembers) {
            this(uniqueId, cleanName, displayName, tag, ownerUUID, new HashMap<>(), maxCoLeaders, maxCommanders, maxMembers, new HashSet<>(), 0L);
        }

        public Clan(String uniqueId, String cleanName, String displayName, String tag, String ownerUUID, Map<String, Integer> ranks,
                    int maxCoLeaders, int maxCommanders, int maxMembers, Collection<String> bannedPlayers, long lastTagChangeTimestamp) {
            this.uniqueId = uniqueId;
            this.cleanName = cleanName;
            this.displayName = displayName;
            this.tag = tag;
            this.ownerUUID = ownerUUID;
            this.ranks = ranks != null ? new HashMap<>(ranks) : new HashMap<>();
            this.maxCoLeaders = maxCoLeaders;
            this.maxCommanders = maxCommanders;
            this.maxMembers = maxMembers;
            this.bannedPlayers = bannedPlayers != null ? new HashSet<>(bannedPlayers) : new HashSet<>();
            this.lastTagChangeTimestamp = lastTagChangeTimestamp;
        }

        public Clan(ClanData data) {
            this(data.getUniqueId(), data.getCleanName(), data.getDisplayName(), data.getTag(), data.getOwnerUUID(),
                    data.getRanks() != null ? new HashMap<>(data.getRanks()) : new HashMap<>(),
                    data.getMaxCoLeaders(), data.getMaxCommanders(), data.getMaxMembers(),
                    data.getBannedPlayers() != null ? new HashSet<>(data.getBannedPlayers()) : new HashSet<>(),
                    data.getLastTagChangeTimestamp());
        }

        public String getUniqueId() {
            return uniqueId;
        }

        public String getCleanName() {
            return cleanName;
        }

        public void setCleanName(String cleanName) {
            this.cleanName = cleanName;
        }

        public String getDisplayName() {
            return displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }

        public String getTag() {
            return tag;
        }

        public void setTag(String tag) {
            this.tag = tag;
        }

        public String getOwnerUUID() {
            return ownerUUID;
        }

        public void setOwnerUUID(String ownerUUID) {
            this.ownerUUID = ownerUUID;
            save();
        }

        public Map<String, Integer> getRanks() {
            return new HashMap<>(ranks);
        }

        public int getMaxCoLeaders() {
            return maxCoLeaders;
        }

        public int getMaxCommanders() {
            return maxCommanders;
        }

        public int getMaxMembers() {
            return maxMembers;
        }

        public long getLastTagChangeTimestamp() {
            return lastTagChangeTimestamp;
        }

        public void setLastTagChangeTimestamp(long timestamp) {
            this.lastTagChangeTimestamp = timestamp;
            save();
        }

        public void addMember(String playerUUID, Rank rank) {
            ranks.put(playerUUID, rank.getValue());
        }

        public void removeMember(String playerUUID) {
            ranks.remove(playerUUID);
        }

        public void setRank(String playerUUID, Rank rank) {
            if (ranks.containsKey(playerUUID)) {
                ranks.put(playerUUID, rank.getValue());
            }
        }

        public int getCoLeaderCount() {
            return (int) ranks.values().stream().filter(v -> v == Rank.CO_LEADER.getValue()).count();
        }

        public int getCommanderCount() {
            return (int) ranks.values().stream().filter(v -> v == Rank.COMMANDER.getValue()).count();
        }

        public int getMemberCount() {
            return getTotalMemberCount();
        }

        public int getTotalMemberCount() {
            return (int) ranks.entrySet().stream()
                    .filter(entry -> !entry.getKey().equals(ownerUUID))
                    .count();
        }

        public Set<String> getBannedPlayers() {
            return new HashSet<>(bannedPlayers);
        }

        public boolean isBanned(String playerUUID) {
            return bannedPlayers.contains(playerUUID);
        }

        public void banPlayer(String playerUUID) {
            if (playerUUID.equals(ownerUUID) || bannedPlayers.contains(playerUUID)) {
                return;
            }
            bannedPlayers.add(playerUUID);
            save();
        }

        public void unbanPlayer(String playerUUID) {
            if (!bannedPlayers.contains(playerUUID)) {
                return;
            }
            bannedPlayers.remove(playerUUID);
            save();
        }

        public void save() {
            ClanData data = new ClanData(
                    uniqueId, cleanName, displayName, tag, ownerUUID, new HashMap<>(ranks),
                    maxCoLeaders, maxCommanders, maxMembers, new ArrayList<>(bannedPlayers), lastTagChangeTimestamp
            );
            ClanManager.this.database.saveObject(data);
        }
    }
}