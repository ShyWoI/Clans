package world.bentobox.clans.commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import world.bentobox.bentobox.api.commands.CompositeCommand;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.clans.Clans;
import world.bentobox.clans.managers.ClanManager;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ClanBanCommand extends CompositeCommand {
    private final Clans clans;

    public ClanBanCommand(Clans addon, CompositeCommand parent) {
        super(addon, parent, "ban", "unban", "banlist");
        this.clans = addon;
    }

    @Override
    public void setup() {
        setPermission("clans.ban");
        setParametersHelp("clans.commands.clan.ban.parameters");
        setDescription("clans.commands.clan.ban.description");

        getSubCommand("unban").ifPresent(sub -> {
            sub.setPermission("clans.unban");
            sub.setParametersHelp("clans.commands.clan.unban.parameters");
            sub.setDescription("clans.commands.clan.unban.description");
        });

        getSubCommand("banlist").ifPresent(sub -> {
            sub.setPermission("clans.banlist");
            sub.setParametersHelp("clans.commands.clan.banlist.parameters");
            sub.setDescription("clans.commands.clan.banlist.description");
        });
    }

    @Override
    public boolean execute(User user, String label, List<String> args) {
        String subCommand = String.valueOf(getSubCommand(label));
        String playerUUID = user.getUniqueId().toString();
        UUID userId = user.getUniqueId();

        ClanManager.Clan clan = clans.getClanManager().getClanByPlayer(playerUUID).orElse(null);
        if (clan == null) {
            user.sendMessage(clans.getTranslation(user, "clans.errors.not-in-clan"));
            return false;
        }

        int executorRankValue = clan.getRanks().getOrDefault(playerUUID, ClanManager.Clan.Rank.MEMBER.getValue());
        ClanManager.Clan.Rank executorRank = getRankFromValue(executorRankValue);

        if (executorRank != ClanManager.Clan.Rank.LEADER && executorRank != ClanManager.Clan.Rank.CO_LEADER) {
            user.sendMessage(clans.getTranslation(user,
                    subCommand.equals("unban") ? "clans.commands.clan.unban.no-permission" :
                            subCommand.equals("banlist") ? "clans.commands.clan.banlist.no-permission" :
                                    "clans.commands.clan.ban.no-permission"));
            return false;
        }

        return switch (subCommand) {
            case "ban" -> handleBan(user, clan, args, userId, executorRank);
            case "unban" -> handleUnban(user, clan, args);
            case "banlist" -> handleBanList(user, clan, args);
            default -> false;
        };
    }

    private boolean handleBan(User user, ClanManager.Clan clan, List<String> args, UUID userId, ClanManager.Clan.Rank executorRank) {
        if (!args.isEmpty() && args.getFirst().equalsIgnoreCase("cancel")) {
            Clans.BanRequest request = clans.banRequests.remove(userId);
            if (request == null) {
                user.sendMessage(clans.getTranslation(user, "clans.commands.clan.ban.no-pending"));
                return false;
            }
            user.sendMessage(clans.getTranslation(user, "clans.commands.clan.ban.cancelled"));
            return true;
        }

        if (args.size() != 1) {
            user.sendMessage(clans.getTranslation(user, "clans.commands.clan.ban.usage"));
            return false;
        }

        String targetName = args.getFirst();
        OfflinePlayer targetPlayer = Bukkit.getOfflinePlayerIfCached(targetName);
        if (targetPlayer == null || !targetPlayer.hasPlayedBefore()) {
            user.sendMessage(clans.getTranslation(user, "clans.commands.clan.ban.player-not-found", "[player]", targetName));
            return false;
        }

        String targetUUID = targetPlayer.getUniqueId().toString();

        if (targetUUID.equals(clan.getOwnerUUID())) {
            user.sendMessage(clans.getTranslation(user, "clans.commands.clan.ban.cannot-ban-leader", "[player]", targetName));
            return false;
        }

        if (clan.isBanned(targetUUID)) {
            user.sendMessage(clans.getTranslation(user, "clans.commands.clan.ban.already-banned", "[player]", targetName));
            return false;
        }

        boolean isInClan = clan.getRanks().containsKey(targetUUID);
        if (isInClan) {
            int targetRankValue = clan.getRanks().getOrDefault(targetUUID, ClanManager.Clan.Rank.MEMBER.getValue());
            ClanManager.Clan.Rank targetRank = getRankFromValue(targetRankValue);
            if (executorRank != ClanManager.Clan.Rank.LEADER && targetRank.getValue() >= executorRank.getValue()) {
                user.sendMessage(clans.getTranslation(user, "clans.commands.clan.ban.cannot-ban-equal-or-higher", "[player]", targetName));
                return false;
            }
        }

        Clans.BanRequest existingRequest = clans.banRequests.get(userId);
        if (existingRequest != null) {
            if (args.getFirst().equalsIgnoreCase(existingRequest.targetName)) {
                clans.banRequests.remove(userId);
                if (isInClan) {
                    clan.removeMember(targetUUID);
                }
                clan.banPlayer(targetUUID);
                clans.getClanManager().saveClans();
                User targetUser = User.getInstance(targetPlayer.getUniqueId());
                boolean penitenceStarted = clans.getPenitenceRemainingTime(targetUser) == 0;
                if (penitenceStarted) {
                    clans.startPenitence(targetUser);
                }
                Player player = user.getPlayer();
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.0f);
                user.sendMessage(clans.getTranslation(user, "clans.commands.clan.ban.success",
                        "[player]", targetName, "[clan]", clan.getDisplayName()));
                if (targetUser.isOnline()) {
                    targetUser.sendMessage(clans.getTranslation(targetUser, "clans.commands.clan.ban.notify",
                            "[clan]", clan.getDisplayName()));
                }
                if (isInClan) {
                    clan.getRanks().keySet().stream()
                            .map(uuid -> User.getInstance(UUID.fromString(uuid)))
                            .filter(u -> !u.getUniqueId().equals(userId))
                            .forEach(member -> member.sendMessage(clans.getTranslation(member, "clans.commands.clan.ban.clan-notify",
                                    "[player]", targetName, "[clan]", clan.getDisplayName())));
                }
                if (!penitenceStarted) {
                    user.sendMessage(clans.getTranslation(user, "clans.commands.clan.ban.penitence-already-active",
                            "[player]", targetName));
                }
                return true;
            } else {
                user.sendMessage(clans.getTranslation(user, "clans.commands.clan.ban.pending"));
                return false;
            }
        }

        sendBanConfirmation(user, clan.getDisplayName(), targetName);
        return true;
    }

    private boolean handleUnban(User user, ClanManager.Clan clan, List<String> args) {
        if (args.size() != 1) {
            user.sendMessage(clans.getTranslation(user, "clans.commands.clan.unban.usage"));
            return false;
        }

        String targetName = args.getFirst();
        OfflinePlayer targetPlayer = Bukkit.getOfflinePlayerIfCached(targetName);
        if (targetPlayer == null || !targetPlayer.hasPlayedBefore()) {
            user.sendMessage(clans.getTranslation(user, "clans.commands.clan.unban.player-not-found", "[player]", targetName));
            return false;
        }

        String targetUUID = targetPlayer.getUniqueId().toString();

        if (!clan.isBanned(targetUUID)) {
            user.sendMessage(clans.getTranslation(user, "clans.commands.clan.unban.not-banned", "[player]", targetName));
            return false;
        }

        clan.unbanPlayer(targetUUID);
        clans.getClanManager().saveClans();

        Player player = user.getPlayer();
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.0f);
        user.sendMessage(clans.getTranslation(user, "clans.commands.clan.unban.success",
                "[player]", targetName, "[clan]", clan.getDisplayName()));
        User targetUser = User.getInstance(targetPlayer.getUniqueId());
        if (targetUser.isOnline()) {
            targetUser.sendMessage(clans.getTranslation(targetUser, "clans.commands.clan.unban.notify",
                    "[clan]", clan.getDisplayName()));
        }

        return true;
    }

    private boolean handleBanList(User user, ClanManager.Clan clan, List<String> args) {
        if (!args.isEmpty()) {
            user.sendMessage(clans.getTranslation(user, "clans.commands.clan.banlist.usage"));
            return false;
        }

        List<String> bannedNames = clan.getBannedPlayers().stream()
                .map(uuid -> {
                    OfflinePlayer player = Bukkit.getOfflinePlayer(UUID.fromString(uuid));
                    return player.getName() != null ? player.getName() : "Desconocido";
                })
                .sorted()
                .collect(Collectors.toList());

        List<String> messages = clans.getTranslationList(user, "clans.commands.clan.banlist.message");
        for (String message : messages) {
            String translated = message
                    .replace("[clan]", clan.getDisplayName())
                    .replace("[banned_players]", bannedNames.isEmpty() ?
                            clans.getTranslation(user, "clans.commands.clan.banlist.none") :
                            String.join(", ", bannedNames));
            user.sendMessage(translated);
        }

        Player player = user.getPlayer();
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.5f, 1.0f);
        return true;
    }

    private void sendBanConfirmation(User user, String clanName, String targetName) {
        UUID userId = user.getUniqueId();
        String command = "/clan ban " + targetName;

        Player player = user.getPlayer();
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.5f);

        YamlConfiguration locale = clans.getLocaleConfig(user);
        String messageTemplate = locale.getString("clans.commands.clan.ban.confirm-message",
                "&e&lConfirma que deseas banear a &6[player]&e&l del clan:\n&eClan: &6[clan]\n\n&eHaz clic para confirmar o cancelar.");
        String acceptButton = locale.getString("clans.commands.clan.ban.accept-button", "[Aceptar]");
        String acceptTooltip = locale.getString("clans.commands.clan.ban.accept-tooltip", "Banear a [player] del clan [clan]");
        String rejectButton = locale.getString("clans.commands.clan.ban.reject-button", "[Rechazar]");
        String rejectTooltip = locale.getString("clans.commands.clan.ban.reject-tooltip", "Cancelar baneo");

        String cleanClanName = clans.stripColor(clanName);
        String messageText = messageTemplate.replace("[player]", targetName).replace("[clan]", clanName);
        acceptTooltip = acceptTooltip.replace("[player]", targetName).replace("[clan]", cleanClanName);
        rejectTooltip = rejectTooltip.replace("[clan]", cleanClanName);

        Component message = Component.empty();
        for (String line : messageText.split("\n")) {
            message = message.append(LegacyComponentSerializer.legacyAmpersand().deserialize(line)).append(Component.newline());
        }

        message = message
                .append(Component.text(acceptButton + " ", NamedTextColor.GREEN, TextDecoration.BOLD)
                        .clickEvent(ClickEvent.runCommand(command))
                        .hoverEvent(HoverEvent.showText(LegacyComponentSerializer.legacyAmpersand().deserialize(acceptTooltip))))
                .append(Component.text(rejectButton + " ", NamedTextColor.RED, TextDecoration.BOLD)
                        .clickEvent(ClickEvent.runCommand("/clan ban cancel"))
                        .hoverEvent(HoverEvent.showText(LegacyComponentSerializer.legacyAmpersand().deserialize(rejectTooltip))))
                .append(Component.newline());

        user.getPlayer().sendMessage(message);

        clans.banRequests.put(userId, new Clans.BanRequest(clanName, targetName, System.currentTimeMillis()));
    }

    private ClanManager.Clan.Rank getRankFromValue(int value) {
        for (ClanManager.Clan.Rank rank : ClanManager.Clan.Rank.values()) {
            if (rank.getValue() == value) {
                return rank;
            }
        }
        return ClanManager.Clan.Rank.MEMBER;
    }

    @Override
    public Optional<List<String>> tabComplete(User user, String alias, List<String> args) {
        String playerUUID = user.getUniqueId().toString();
        ClanManager.Clan clan = clans.getClanManager().getClanByPlayer(playerUUID).orElse(null);
        if (clan == null) {
            return Optional.of(List.of());
        }

        int executorRankValue = clan.getRanks().getOrDefault(playerUUID, ClanManager.Clan.Rank.MEMBER.getValue());
        if (executorRankValue != ClanManager.Clan.Rank.LEADER.getValue() && executorRankValue != ClanManager.Clan.Rank.CO_LEADER.getValue()) {
            return Optional.of(List.of());
        }

        if (args.isEmpty()) {
            // Sugerir subcomandos: ban, unban, banlist
            return Optional.of(Stream.of("ban", "unban", "banlist")
                    .filter(sub -> user.hasPermission("clans." + sub))
                    .collect(Collectors.toList()));
        }

        String subCommand = args.getFirst().toLowerCase();
        if (args.size() == 1) {
            // Completar subcomandos parciales
            return Optional.of(Stream.of("ban", "unban", "banlist")
                    .filter(sub -> sub.startsWith(subCommand) && user.hasPermission("clans." + sub))
                    .collect(Collectors.toList()));
        }

        // Completar argumentos para ban o unban
        if (args.size() > 2 || (!subCommand.equals("ban") && !subCommand.equals("unban"))) {
            return Optional.of(List.of());
        }

        String input = args.get(1).toLowerCase();
        List<String> suggestions;
        if (subCommand.equals("unban")) {
            suggestions = clan.getBannedPlayers().stream()
                    .map(uuid -> Bukkit.getOfflinePlayer(UUID.fromString(uuid)).getName())
                    .filter(name -> name != null && name.toLowerCase().startsWith(input))
                    .collect(Collectors.toList());
        } else {
            suggestions = Bukkit.getOnlinePlayers().stream()
                    .filter(p -> !clan.isBanned(p.getUniqueId().toString()))
                    .filter(p -> !p.getUniqueId().toString().equals(clan.getOwnerUUID()))
                    .filter(p -> {
                        String uuid = p.getUniqueId().toString();
                        if (!clan.getRanks().containsKey(uuid)) return true;
                        int rankValue = clan.getRanks().getOrDefault(uuid, ClanManager.Clan.Rank.MEMBER.getValue());
                        return executorRankValue == ClanManager.Clan.Rank.LEADER.getValue() || rankValue < executorRankValue;
                    })
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(input))
                    .collect(Collectors.toList());
        }
        return Optional.of(suggestions);
    }
}