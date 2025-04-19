package world.bentobox.clans;

import java.util.UUID;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.clans.managers.ClanManager;
import world.bentobox.clans.managers.ClanManager.Clan;

public class ClansPlaceholders {

    private final Clans addon;
    private final ClanManager clanManager;

    public ClansPlaceholders(Clans addon, world.bentobox.bentobox.managers.PlaceholdersManager placeholdersManager) {
        this.addon = addon;
        this.clanManager = addon.getClanManager();
        placeholdersManager.registerPlaceholder(addon, "my_clan_name", this::getClanName);
        placeholdersManager.registerPlaceholder(addon, "my_clan_tag", this::getClanTag);
        placeholdersManager.registerPlaceholder(addon, "my_clan_rank", this::getClanRank);
        placeholdersManager.registerPlaceholder(addon, "my_clan_members", this::getClanMembers);
        placeholdersManager.registerPlaceholder(addon, "my_clan_max_members", this::getClanMaxMembers);
        placeholdersManager.registerPlaceholder(addon, "my_clan_leader", this::getClanLeader);
        placeholdersManager.registerPlaceholder(addon, "my_clan_co_leaders", this::getClanCoLeaders);
        placeholdersManager.registerPlaceholder(addon, "my_clan_max_co_leaders", this::getClanMaxCoLeaders);
        placeholdersManager.registerPlaceholder(addon, "my_clan_commanders", this::getClanCommanders);
        placeholdersManager.registerPlaceholder(addon, "my_clan_max_commanders", this::getClanMaxCommanders);
        placeholdersManager.registerPlaceholder(addon, "my_clan_banned", this::getClanBanned);
        placeholdersManager.registerPlaceholder(addon, "total_clans", this::getTotalClans);
        placeholdersManager.registerPlaceholder(addon, "player_clan_status", this::getPlayerClanStatus);
        placeholdersManager.registerPlaceholder(addon, "penitence_time", this::getPenitenceTime);
        placeholdersManager.registerPlaceholder(addon, "penitence_status", this::getPenitenceStatus);
    }

    /**
     * Get the player's clan name
     * @param user - user
     * @return Clan name or empty string if none
     */
    public String getClanName(User user) {
        if (user == null || user.getUniqueId() == null) return "";
        return clanManager.getClanByPlayer(user.getUniqueId().toString())
                .map(Clan::getDisplayName)
                .orElse("");
    }

    /**
     * Get the player's clan tag
     * @param user - user
     * @return Clan tag or empty string if none
     */
    public String getClanTag(User user) {
        if (user == null || user.getUniqueId() == null) return "";
        return clanManager.getClanByPlayer(user.getUniqueId().toString())
                .map(Clan::getTag)
                .orElse("");
    }

    /**
     * Get the player's rank in their clan
     * @param user - user
     * @return Rank name or empty string if none
     */
    public String getClanRank(User user) {
        if (user == null || user.getUniqueId() == null) return "";
        return clanManager.getClanByPlayer(user.getUniqueId().toString())
                .map(clan -> {
                    Integer rankValue = clan.getRanks().get(user.getUniqueId().toString());
                    if (rankValue == null) return "";
                    for (Clan.Rank rank : Clan.Rank.values()) {
                        if (rank.getValue() == rankValue) {
                            return user.getTranslation("clans.ranks." + rank.name().toLowerCase());
                        }
                    }
                    return "";
                })
                .orElse("");
    }

    /**
     * Get the number of members in the player's clan (excluding leader)
     * @param user - user
     * @return Number of members or empty string if none
     */
    public String getClanMembers(User user) {
        if (user == null || user.getUniqueId() == null) return "";
        return clanManager.getClanByPlayer(user.getUniqueId().toString())
                .map(clan -> String.valueOf(clan.getTotalMemberCount()))
                .orElse("");
    }

    /**
     * Get the maximum number of members allowed in the player's clan
     * @param user - user
     * @return Max members or empty string if none
     */
    public String getClanMaxMembers(User user) {
        if (user == null || user.getUniqueId() == null) return "";
        return clanManager.getClanByPlayer(user.getUniqueId().toString())
                .map(clan -> String.valueOf(clan.getMaxMembers()))
                .orElse("");
    }

    /**
     * Get the name of the leader of the player's clan
     * @param user - user
     * @return Leader name or empty string if none
     */
    public String getClanLeader(User user) {
        if (user == null || user.getUniqueId() == null) return "";
        return clanManager.getClanByPlayer(user.getUniqueId().toString())
                .map(clan -> {
                    User leader = User.getInstance(UUID.fromString(clan.getOwnerUUID()));
                    return leader.getName();
                })
                .orElse("");
    }

    /**
     * Get the number of co-leaders in the player's clan
     * @param user - user
     * @return Number of co-leaders or empty string if none
     */
    public String getClanCoLeaders(User user) {
        if (user == null || user.getUniqueId() == null) return "";
        return clanManager.getClanByPlayer(user.getUniqueId().toString())
                .map(clan -> String.valueOf(clan.getCoLeaderCount()))
                .orElse("");
    }

    /**
     * Get the maximum number of co-leaders allowed in the player's clan
     * @param user - user
     * @return Max co-leaders or empty string if none
     */
    public String getClanMaxCoLeaders(User user) {
        if (user == null || user.getUniqueId() == null) return "";
        return clanManager.getClanByPlayer(user.getUniqueId().toString())
                .map(clan -> String.valueOf(clan.getMaxCoLeaders()))
                .orElse("");
    }

    /**
     * Get the number of commanders in the player's clan
     * @param user - user
     * @return Number of commanders or empty string if none
     */
    public String getClanCommanders(User user) {
        if (user == null || user.getUniqueId() == null) return "";
        return clanManager.getClanByPlayer(user.getUniqueId().toString())
                .map(clan -> String.valueOf(clan.getCommanderCount()))
                .orElse("");
    }

    /**
     * Get the maximum number of commanders allowed in the player's clan
     * @param user - user
     * @return Max commanders or empty string if none
     */
    public String getClanMaxCommanders(User user) {
        if (user == null || user.getUniqueId() == null) return "";
        return clanManager.getClanByPlayer(user.getUniqueId().toString())
                .map(clan -> String.valueOf(clan.getMaxCommanders()))
                .orElse("");
    }

    /**
     * Get the number of banned players in the player's clan
     * @param user - user
     * @return Number of banned players or empty string if none
     */
    public String getClanBanned(User user) {
        if (user == null || user.getUniqueId() == null) return "";
        return clanManager.getClanByPlayer(user.getUniqueId().toString())
                .map(clan -> String.valueOf(clan.getBannedPlayers().size()))
                .orElse("");
    }

    /**
     * Get the total number of clans on the server
     * @param user - user
     * @return Total number of clans
     */
    public String getTotalClans(User user) {
        return String.valueOf(clanManager.getAllClans().size());
    }

    /**
     * Get the player's clan status (in clan or not)
     * @param user - user
     * @return "In clan" or "No clan" translated
     */
    public String getPlayerClanStatus(User user) {
        if (user == null || user.getUniqueId() == null) return "";
        boolean hasClan = clanManager.playerHasClan(user.getUniqueId().toString());
        return user.getTranslation(hasClan ? "clans.status.in-clan" : "clans.status.no-clan");
    }

    /**
     * Get the remaining penitence time for the player
     * @param user - user
     * @return Remaining time in a readable format or empty string if none
     */
    public String getPenitenceTime(User user) {
        if (user == null || user.getUniqueId() == null) return "";
        if (!addon.isUnderPenitence(user, null, false)) return "";
        return addon.formatTime(addon.getPenitenceRemainingTime(user));
    }

    /**
     * Get the penitence status of the player
     * @param user - user
     * @return "In penitence" or "No penitence" translated
     */
    public String getPenitenceStatus(User user) {
        if (user == null || user.getUniqueId() == null) return "";
        boolean inPenitence = addon.isUnderPenitence(user, null, false);
        return user.getTranslation(inPenitence ? "clans.penitence.status.in-penitence" : "clans.penitence.status.no-penitence");
    }
}