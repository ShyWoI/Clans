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
import world.bentobox.bentobox.api.commands.CompositeCommand;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.clans.Clans;
import world.bentobox.clans.managers.ClanManager;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

public class ClanKickCommand extends CompositeCommand {
    private final Clans clans;

    public ClanKickCommand(Clans addon, CompositeCommand parent) {
        super(addon, parent, "kick");
        this.clans = addon;
        Bukkit.getScheduler().runTask(addon.getPlugin(), this::configure);
    }

    @Override
    public void setup() {
        setPermission("clans.kick");
        setDescription("clans.commands.clan.kick.description");
        setUsage("<jugador>");
    }

    private void configure() {
        setDescription(clans.getTranslation(null, "clans.commands.clan.kick.description"));
    }

    @Override
    public boolean execute(User user, String label, List<String> args) {
        String playerUUID = user.getUniqueId().toString();
        UUID userId = user.getUniqueId();

        // Verificar si el jugador está en un clan
        Optional<ClanManager.Clan> clanOpt = clans.getClanManager().getClanByPlayer(playerUUID);
        if (clanOpt.isEmpty()) {
            user.sendMessage(clans.getTranslation(user, "clans.errors.not-in-clan"));
            return false;
        }

        ClanManager.Clan clan = clanOpt.get();

        // Obtener el rango del ejecutor
        int executorRankValue = clan.getRanks().getOrDefault(playerUUID, ClanManager.Clan.Rank.MEMBER.getValue());
        ClanManager.Clan.Rank executorRank = getRankFromValue(executorRankValue);

        // Verificar si el ejecutor tiene permiso para expulsar
        if (executorRank != ClanManager.Clan.Rank.LEADER && executorRank != ClanManager.Clan.Rank.CO_LEADER) {
            user.sendMessage(clans.getTranslation(user, "clans.commands.clan.kick.no-permission"));
            return false;
        }

        // Manejar cancelación
        if (!args.isEmpty() && args.getFirst().equalsIgnoreCase("cancel")) {
            Clans.KickRequest request = clans.kickRequests.remove(userId);
            if (request == null) {
                user.sendMessage(clans.getTranslation(user, "clans.commands.clan.kick.no-pending"));
                return false;
            }
            user.sendMessage(clans.getTranslation(user, "clans.commands.clan.kick.cancelled"));
            return true;
        }

        // Verificar si hay argumentos
        if (args.size() != 1) {
            user.sendMessage(clans.getTranslation(user, "clans.commands.clan.kick.usage"));
            return false;
        }

        // Obtener el jugador objetivo
        String targetName = args.getFirst();
        OfflinePlayer targetPlayer = Bukkit.getOfflinePlayerIfCached(targetName);
        if (targetPlayer == null || !targetPlayer.hasPlayedBefore()) {
            user.sendMessage(clans.getTranslation(user, "clans.commands.clan.kick.player-not-found", "[player]", targetName));
            return false;
        }

        String targetUUID = targetPlayer.getUniqueId().toString();
        if (!clan.getRanks().containsKey(targetUUID)) {
            user.sendMessage(clans.getTranslation(user, "clans.commands.clan.kick.not-in-clan", "[player]", targetName));
            return false;
        }

        // Verificar si el objetivo es el líder
        if (targetUUID.equals(clan.getOwnerUUID())) {
            user.sendMessage(clans.getTranslation(user, "clans.commands.clan.kick.cannot-kick-leader", "[player]", targetName));
            return false;
        }

        // Verificar si el ejecutor tiene autoridad para expulsar al objetivo
        int targetRankValue = clan.getRanks().get(targetUUID);
        ClanManager.Clan.Rank targetRank = getRankFromValue(targetRankValue);
        if (executorRank != ClanManager.Clan.Rank.LEADER && targetRank.getValue() >= executorRank.getValue()) {
            user.sendMessage(clans.getTranslation(user, "clans.commands.clan.kick.cannot-kick-equal-or-higher", "[player]", targetName));
            return false;
        }

        // Verificar si el jugador objetivo está bajo penitencia
        User targetUser = User.getInstance(targetPlayer.getUniqueId());
        if (clans.isUnderPenitence(targetUser)) {
            user.sendMessage(clans.getTranslation(user, "clans.commands.clan.penitence.blocked",
                    "[player]", targetName,
                    "[time]", clans.formatTime(clans.getPenitenceRemainingTime(targetUser))));
            return false;
        }

        // Verificar si ya hay una solicitud pendiente
        Clans.KickRequest existingRequest = clans.kickRequests.get(userId);
        if (existingRequest != null) {
            // Confirmar si el comando coincide con la solicitud
            if (args.getFirst().equalsIgnoreCase(existingRequest.targetName)) {
                clans.kickRequests.remove(userId);
                // Expulsar al jugador del clan
                clan.removeMember(targetUUID);
                // Guardar el clan actualizado
                clans.getClanManager().saveClans();
                // Iniciar penitencia para el jugador expulsado
                clans.startPenitence(targetUser);
                // Reproducir sonido de confirmación
                user.getPlayer().playSound(user.getPlayer().getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.0f);
                // Notificar al ejecutor
                user.sendMessage(clans.getTranslation(user, "clans.commands.clan.kick.success",
                        "[player]", targetName, "[clan]", clan.getDisplayName()));
                // Notificar al expulsado
                if (targetUser.isOnline()) {
                    targetUser.sendMessage(clans.getTranslation(targetUser, "clans.commands.clan.kick.notify",
                            "[clan]", clan.getDisplayName()));
                }
                // Notificar al resto del clan
                clan.getRanks().keySet().stream()
                        .map(uuid -> User.getInstance(UUID.fromString(uuid)))
                        .filter(u -> !u.getUniqueId().equals(userId))
                        .forEach(member -> member.sendMessage(clans.getTranslation(member, "clans.commands.clan.kick.clan-notify",
                                "[player]", targetName, "[clan]", clan.getDisplayName())));
                return true;
            } else {
                user.sendMessage(clans.getTranslation(user, "clans.commands.clan.kick.pending"));
                return false;
            }
        }

        // Enviar mensaje de confirmación
        sendKickConfirmation(user, clan.getDisplayName(), targetName);
        return true;
    }

    private void sendKickConfirmation(User user, String clanName, String targetName) {
        UUID userId = user.getUniqueId();
        String command = "/clan kick " + targetName;

        // Reproducir sonido de pregunta tensa
        user.getPlayer().playSound(user.getPlayer().getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.5f);

        // Cargar mensajes desde es-ES.yml
        YamlConfiguration locale = clans.getLocaleConfig(user);
        String messageTemplate = locale.getString("clans.commands.clan.kick.confirm-message",
                "&e&lConfirma que deseas expulsar a &6[player]&e&l del clan:\n&eClan: &6[clan]\n\n&eHaz clic para confirmar o cancelar.");
        String acceptButton = locale.getString("clans.commands.clan.kick.accept-button", "[Aceptar]");
        String acceptTooltip = locale.getString("clans.commands.clan.kick.accept-tooltip", "Expulsar a [player] del clan [clan]");
        String rejectButton = locale.getString("clans.commands.clan.kick.reject-button", "[Rechazar]");
        String rejectTooltip = locale.getString("clans.commands.clan.kick.reject-tooltip", "Cancelar expulsión");

        // Reemplazar marcadores en el mensaje
        String messageText = messageTemplate.replace("[player]", targetName).replace("[clan]", clanName);
        // Reemplazar marcadores en los tooltips
        acceptTooltip = acceptTooltip.replace("[player]", targetName).replace("[clan]", clanName);
        rejectTooltip = rejectTooltip.replace("[clan]", clanName);

        // Convertir el mensaje a Component con colores
        Component message = Component.empty();
        for (String line : messageText.split("\n")) {
            message = message.append(LegacyComponentSerializer.legacyAmpersand().deserialize(line)).append(Component.newline());
        }

        // Añadir botones
        message = message
                .append(Component.text(acceptButton + " ", NamedTextColor.GREEN, TextDecoration.BOLD)
                        .clickEvent(ClickEvent.runCommand(command))
                        .hoverEvent(HoverEvent.showText(Component.text(acceptTooltip, NamedTextColor.GRAY))))
                .append(Component.text(rejectButton + " ", NamedTextColor.RED, TextDecoration.BOLD)
                        .clickEvent(ClickEvent.runCommand("/clan kick cancel"))
                        .hoverEvent(HoverEvent.showText(Component.text(rejectTooltip, NamedTextColor.GRAY))))
                .append(Component.newline());

        user.getPlayer().sendMessage(message);

        // Almacenar solicitud
        clans.kickRequests.put(userId, new Clans.KickRequest(clanName, targetName, System.currentTimeMillis()));
    }

    private ClanManager.Clan.Rank getRankFromValue(int value) {
        for (ClanManager.Clan.Rank rank : ClanManager.Clan.Rank.values()) {
            if (rank.getValue() == value) {
                return rank;
            }
        }
        return ClanManager.Clan.Rank.MEMBER; // Por defecto
    }

    @Override
    public Optional<List<String>> tabComplete(User user, String alias, List<String> args) {
        if (args.size() > 1 || !user.hasPermission("clans.kick")) {
            return Optional.of(List.of());
        }

        String playerUUID = user.getUniqueId().toString();
        Optional<ClanManager.Clan> clanOpt = clans.getClanManager().getClanByPlayer(playerUUID);
        if (clanOpt.isEmpty()) {
            return Optional.of(List.of());
        }

        ClanManager.Clan clan = clanOpt.get();
        int executorRankValue = clan.getRanks().getOrDefault(playerUUID, ClanManager.Clan.Rank.MEMBER.getValue());
        if (executorRankValue != ClanManager.Clan.Rank.LEADER.getValue() && executorRankValue != ClanManager.Clan.Rank.CO_LEADER.getValue()) {
            return Optional.of(List.of());
        }

        String input = args.isEmpty() ? "" : args.getFirst().toLowerCase();
        List<String> suggestions = clan.getRanks().entrySet().stream()
                .filter(entry -> !entry.getKey().equals(playerUUID)) // Excluir al ejecutor
                .filter(entry -> !entry.getKey().equals(clan.getOwnerUUID())) // Excluir al líder
                .filter(entry -> executorRankValue == ClanManager.Clan.Rank.LEADER.getValue() ||
                        entry.getValue() < executorRankValue) // Solo rangos menores
                .map(entry -> {
                    OfflinePlayer player = Bukkit.getOfflinePlayer(UUID.fromString(entry.getKey()));
                    return player.getName();
                })
                .filter(name -> name != null && name.toLowerCase().startsWith(input))
                .collect(Collectors.toList());

        return Optional.of(suggestions);
    }
}