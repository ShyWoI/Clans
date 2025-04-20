package world.bentobox.clans.commands;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import world.bentobox.bentobox.api.commands.CompositeCommand;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.clans.Clans;
import world.bentobox.clans.managers.ClanManager;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

public class ClanRankCommand extends CompositeCommand {
    private final Clans clans;

    public ClanRankCommand(Clans addon, CompositeCommand parent) {
        super(addon, parent, "rank");
        this.clans = addon;
        new PromoteSubCommand(addon, this);
        new DemoteSubCommand(addon, this);
    }

    @Override
    public void setup() {
        setPermission("clans.rank");
        setParametersHelp("clans.commands.clan.rank.parameters");
        setDescription("clans.commands.clan.rank.description");
        if (clans != null) {
            new PromoteSubCommand(clans, this);
            new DemoteSubCommand(clans, this);
        }
    }

    @Override
    public boolean execute(User user, String label, List<String> args) {
        user.sendMessage(clans.getTranslation(user, "clans.commands.clan.unknown-command"));
        return false;
    }

    private class PromoteSubCommand extends CompositeCommand {
        public PromoteSubCommand(Clans addon, CompositeCommand parent) {
            super(addon, parent, "promote");
            if (addon != null) {
                Bukkit.getScheduler().runTask(addon.getPlugin(), this::configure);
            } else {
                getLogger().warning("Advertencia: addon es null en PromoteSubCommand. La configuración no se ejecutará.");
            }
        }

        @Override
        public void setup() {
            setPermission("clans.promote");
            setParametersHelp("clans.commands.clan.promote.parameters");
            setDescription("clans.commands.clan.promote.description");
        }

        private void configure() {
            // Configuración ya manejada en setup
        }

        @Override
        public boolean execute(User user, String label, List<String> args) {
            String playerUUID = user.getUniqueId().toString();

            if (args.size() != 1) {
                user.sendMessage(clans.getTranslation(user, "clans.commands.clan.promote.usage"));
                return false;
            }

            Optional<ClanManager.Clan> clanOpt = clans.getClanManager().getClanByPlayer(playerUUID);
            if (clanOpt.isEmpty()) {
                user.sendMessage(clans.getTranslation(user, "clans.errors.not-in-clan"));
                return false;
            }

            ClanManager.Clan clan = clanOpt.get();
            int executorRankValue = clan.getRanks().getOrDefault(playerUUID, ClanManager.Clan.Rank.MEMBER.getValue());
            ClanManager.Clan.Rank executorRank = getRankFromValue(executorRankValue);

            if (executorRank == ClanManager.Clan.Rank.MEMBER) {
                user.sendMessage(clans.getTranslation(user, "clans.commands.clan.promote.no-permission"));
                return false;
            }

            String targetName = args.getFirst();
            OfflinePlayer targetPlayer = Bukkit.getOfflinePlayerIfCached(targetName);
            if (targetPlayer == null || !targetPlayer.hasPlayedBefore()) {
                user.sendMessage(clans.getTranslation(user, "clans.commands.clan.promote.player-not-found", "[player]", targetName));
                return false;
            }

            String targetUUID = targetPlayer.getUniqueId().toString();
            if (!clan.getRanks().containsKey(targetUUID)) {
                user.sendMessage(clans.getTranslation(user, "clans.commands.clan.promote.not-in-clan", "[player]", targetName));
                return false;
            }

            if (targetUUID.equals(clan.getOwnerUUID())) {
                user.sendMessage(clans.getTranslation(user, "clans.commands.clan.promote.cannot-promote-leader", "[player]", targetName));
                return false;
            }

            int targetRankValue = clan.getRanks().getOrDefault(targetUUID, ClanManager.Clan.Rank.MEMBER.getValue());
            ClanManager.Clan.Rank targetRank = getRankFromValue(targetRankValue);

            ClanManager.Clan.Rank newRank;
            if (executorRank == ClanManager.Clan.Rank.LEADER) {
                if (targetRank == ClanManager.Clan.Rank.CO_LEADER) {
                    user.sendMessage(clans.getTranslation(user, "clans.commands.clan.promote.already-max-rank", "[player]", targetName));
                    return false;
                }
                newRank = targetRank == ClanManager.Clan.Rank.COMMANDER ? ClanManager.Clan.Rank.CO_LEADER : ClanManager.Clan.Rank.COMMANDER;
            } else {
                if (targetRank.getValue() >= executorRank.getValue()) {
                    user.sendMessage(clans.getTranslation(user, "clans.commands.clan.promote.cannot-promote-higher", "[player]", targetName));
                    return false;
                }
                newRank = executorRank == ClanManager.Clan.Rank.CO_LEADER ? ClanManager.Clan.Rank.COMMANDER : ClanManager.Clan.Rank.MEMBER;
            }

            if (newRank == ClanManager.Clan.Rank.CO_LEADER && clan.getCoLeaderCount() >= clan.getMaxCoLeaders()) {
                user.sendMessage(clans.getTranslation(user, "clans.commands.clan.promote.co-leader-limit", "[max]", String.valueOf(clan.getMaxCoLeaders())));
                return false;
            }
            if (newRank == ClanManager.Clan.Rank.COMMANDER && clan.getCommanderCount() >= clan.getMaxCommanders()) {
                user.sendMessage(clans.getTranslation(user, "clans.commands.clan.promote.commander-limit", "[max]", String.valueOf(clan.getMaxCommanders())));
                return false;
            }

            String rankKey = newRank == ClanManager.Clan.Rank.CO_LEADER ? "co_leaders" : newRank == ClanManager.Clan.Rank.COMMANDER ? "commanders" : "members";
            String rankName = clans.getSettings().getRanks().getOrDefault(rankKey, newRank.name());

            clan.setRank(targetUUID, newRank);
            clans.getClanManager().saveClans();

            user.getPlayer();
            user.getPlayer().playSound(user.getPlayer().getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.0f);
            User targetUser = User.getInstance(targetPlayer.getUniqueId());
            if (targetUser.isOnline()) {
                targetUser.getPlayer();
                targetUser.getPlayer().playSound(targetUser.getPlayer().getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.0f);
            }

            user.sendMessage(clans.getTranslation(user, "clans.commands.clan.promote.success",
                    "[player]", targetName, "[rank]", rankName, "[clan]", clan.getDisplayName()));

            if (targetUser.isOnline()) {
                targetUser.sendMessage(clans.getTranslation(targetUser, "clans.commands.clan.promote.notify",
                        "[rank]", rankName, "[clan]", clan.getDisplayName()));
            }

            clan.getRanks().keySet().stream()
                    .map(uuid -> User.getInstance(UUID.fromString(uuid)))
                    .filter(u -> !u.getUniqueId().equals(user.getUniqueId()) && !u.getUniqueId().equals(targetPlayer.getUniqueId()))
                    .forEach(member -> member.sendMessage(clans.getTranslation(member, "clans.commands.clan.promote.clan-notify",
                            "[player]", targetName, "[rank]", rankName, "[clan]", clan.getDisplayName())));

            return true;
        }

        @Override
        public Optional<List<String>> tabComplete(User user, String alias, List<String> args) {
            if (args.size() > 1 || !user.hasPermission("clans.promote")) {
                return Optional.of(List.of());
            }

            String playerUUID = user.getUniqueId().toString();
            Optional<ClanManager.Clan> clanOpt = clans.getClanManager().getClanByPlayer(playerUUID);
            if (clanOpt.isEmpty()) {
                return Optional.of(List.of());
            }

            ClanManager.Clan clan = clanOpt.get();
            int executorRankValue = clan.getRanks().getOrDefault(playerUUID, ClanManager.Clan.Rank.MEMBER.getValue());
            if (executorRankValue == ClanManager.Clan.Rank.MEMBER.getValue()) {
                return Optional.of(List.of());
            }

            String input = args.isEmpty() ? "" : args.getFirst().toLowerCase();
            List<String> suggestions = clan.getRanks().entrySet().stream()
                    .filter(entry -> !entry.getKey().equals(playerUUID))
                    .filter(entry -> !entry.getKey().equals(clan.getOwnerUUID()))
                    .filter(entry -> executorRankValue == ClanManager.Clan.Rank.LEADER.getValue() ||
                            entry.getValue() < executorRankValue)
                    .map(entry -> {
                        OfflinePlayer player = Bukkit.getOfflinePlayer(UUID.fromString(entry.getKey()));
                        return player.getName();
                    })
                    .filter(name -> name != null && name.toLowerCase().startsWith(input))
                    .collect(Collectors.toList());

            return Optional.of(suggestions);
        }
    }

    private class DemoteSubCommand extends CompositeCommand {
        public DemoteSubCommand(Clans addon, CompositeCommand parent) {
            super(addon, parent, "demote");
        }

        @Override
        public void setup() {
            setPermission("clans.demote");
            setParametersHelp("clans.commands.clan.demote.parameters");
            setDescription("clans.commands.clan.demote.description");
        }

        @Override
        public boolean execute(User user, String label, List<String> args) {
            String playerUUID = user.getUniqueId().toString();

            if (args.size() != 1) {
                user.sendMessage(clans.getTranslation(user, "clans.commands.clan.demote.usage"));
                return false;
            }

            Optional<ClanManager.Clan> clanOpt = clans.getClanManager().getClanByPlayer(playerUUID);
            if (clanOpt.isEmpty()) {
                user.sendMessage(clans.getTranslation(user, "clans.errors.not-in-clan"));
                return false;
            }

            ClanManager.Clan clan = clanOpt.get();
            int executorRankValue = clan.getRanks().getOrDefault(playerUUID, ClanManager.Clan.Rank.MEMBER.getValue());
            ClanManager.Clan.Rank executorRank = getRankFromValue(executorRankValue);

            if (executorRank == ClanManager.Clan.Rank.MEMBER || executorRank == ClanManager.Clan.Rank.COMMANDER) {
                user.sendMessage(clans.getTranslation(user, "clans.commands.clan.demote.no-permission"));
                return false;
            }

            String targetName = args.getFirst();
            OfflinePlayer targetPlayer = Bukkit.getOfflinePlayerIfCached(targetName);
            if (targetPlayer == null || !targetPlayer.hasPlayedBefore()) {
                user.sendMessage(clans.getTranslation(user, "clans.commands.clan.demote.player-not-found", "[player]", targetName));
                return false;
            }

            String targetUUID = targetPlayer.getUniqueId().toString();
            if (!clan.getRanks().containsKey(targetUUID)) {
                user.sendMessage(clans.getTranslation(user, "clans.commands.clan.demote.not-in-clan", "[player]", targetName));
                return false;
            }

            if (targetUUID.equals(clan.getOwnerUUID())) {
                user.sendMessage(clans.getTranslation(user, "clans.commands.clan.demote.cannot-demote-leader", "[player]", targetName));
                return false;
            }

            int targetRankValue = clan.getRanks().getOrDefault(targetUUID, ClanManager.Clan.Rank.MEMBER.getValue());
            ClanManager.Clan.Rank targetRank = getRankFromValue(targetRankValue);

            if (targetRank == ClanManager.Clan.Rank.MEMBER) {
                user.sendMessage(clans.getTranslation(user, "clans.commands.clan.demote.already-min-rank", "[player]", targetName));
                return false;
            }

            if (executorRank != ClanManager.Clan.Rank.LEADER && targetRank.getValue() >= executorRank.getValue()) {
                user.sendMessage(clans.getTranslation(user, "clans.commands.clan.demote.cannot-demote-equal-or-higher", "[player]", targetName));
                return false;
            }

            ClanManager.Clan.Rank newRank = targetRank == ClanManager.Clan.Rank.CO_LEADER ? ClanManager.Clan.Rank.COMMANDER : ClanManager.Clan.Rank.MEMBER;

            String rankKey = newRank == ClanManager.Clan.Rank.COMMANDER ? "commanders" : "members";
            String rankName = clans.getSettings().getRanks().getOrDefault(rankKey, newRank.name());

            clan.setRank(targetUUID, newRank);
            clans.getClanManager().saveClans();

            user.getPlayer();
            user.getPlayer().playSound(user.getPlayer().getLocation(), Sound.ENTITY_VILLAGER_NO, 0.5f, 1.0f);
            User targetUser = User.getInstance(targetPlayer.getUniqueId());
            if (targetUser.isOnline()) {
                targetUser.getPlayer();
                targetUser.getPlayer().playSound(targetUser.getPlayer().getLocation(), Sound.ENTITY_VILLAGER_NO, 0.5f, 1.0f);
            }

            user.sendMessage(clans.getTranslation(user, "clans.commands.clan.demote.success",
                    "[player]", targetName, "[rank]", rankName, "[clan]", clan.getDisplayName()));

            if (targetUser.isOnline()) {
                targetUser.sendMessage(clans.getTranslation(targetUser, "clans.commands.clan.demote.notify",
                        "[rank]", rankName, "[clan]", clan.getDisplayName()));
            }

            clan.getRanks().keySet().stream()
                    .map(uuid -> User.getInstance(UUID.fromString(uuid)))
                    .filter(u -> !u.getUniqueId().equals(user.getUniqueId()) && !u.getUniqueId().equals(targetPlayer.getUniqueId()))
                    .forEach(member -> member.sendMessage(clans.getTranslation(member, "clans.commands.clan.demote.clan-notify",
                            "[player]", targetName, "[rank]", rankName, "[clan]", clan.getDisplayName())));

            return true;
        }

        @Override
        public Optional<List<String>> tabComplete(User user, String alias, List<String> args) {
            if (args.size() > 1 || !user.hasPermission("clans.demote")) {
                return Optional.of(List.of());
            }

            String playerUUID = user.getUniqueId().toString();
            Optional<ClanManager.Clan> clanOpt = clans.getClanManager().getClanByPlayer(playerUUID);
            if (clanOpt.isEmpty()) {
                return Optional.of(List.of());
            }

            ClanManager.Clan clan = clanOpt.get();
            int executorRankValue = clan.getRanks().getOrDefault(playerUUID, ClanManager.Clan.Rank.MEMBER.getValue());
            if (executorRankValue <= ClanManager.Clan.Rank.COMMANDER.getValue()) {
                return Optional.of(List.of());
            }

            String input = args.isEmpty() ? "" : args.getFirst().toLowerCase();
            List<String> suggestions = clan.getRanks().entrySet().stream()
                    .filter(entry -> !entry.getKey().equals(playerUUID))
                    .filter(entry -> !entry.getKey().equals(clan.getOwnerUUID()))
                    .filter(entry -> entry.getValue() > ClanManager.Clan.Rank.MEMBER.getValue())
                    .filter(entry -> executorRankValue == ClanManager.Clan.Rank.LEADER.getValue() ||
                            entry.getValue() < executorRankValue)
                    .map(entry -> {
                        OfflinePlayer player = Bukkit.getOfflinePlayer(UUID.fromString(entry.getKey()));
                        return player.getName();
                    })
                    .filter(name -> name != null && name.toLowerCase().startsWith(input))
                    .collect(Collectors.toList());

            return Optional.of(suggestions);
        }
    }

    private ClanManager.Clan.Rank getRankFromValue(int value) {
        for (ClanManager.Clan.Rank rank : ClanManager.Clan.Rank.values()) {
            if (rank.getValue() == value) {
                return rank;
            }
        }
        return ClanManager.Clan.Rank.MEMBER;
    }
}