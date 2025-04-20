package world.bentobox.clans.commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
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

public class ClanTransferCommand extends CompositeCommand {
    private final Clans clans;

    public ClanTransferCommand(Clans addon, CompositeCommand parent) {
        super(addon, parent, "transfer");
        this.clans = addon;
    }

    @Override
    public void setup() {
        setPermission("clans.transfer");
        setParametersHelp("clans.commands.clan.transfer.parameters");
        setDescription("clans.commands.clan.transfer.description");
    }

    @Override
    public boolean execute(User user, String label, List<String> args) {
        UUID userId = user.getUniqueId();
        String playerUUID = userId.toString();

        // Verificar si el jugador está bajo penitencia
        if (clans.isUnderPenitence(user, null, true)) {
            return false;
        }

        // Verificar si el jugador está en un clan
        ClanManager.Clan clan = clans.getClanManager().getClanByPlayer(playerUUID).orElse(null);
        if (clan == null) {
            user.sendMessage(clans.getTranslation(user, "clans.errors.not-in-clan"));
            return false;
        }

        // Verificar si el jugador es el líder
        if (!clan.getOwnerUUID().equals(playerUUID)) {
            user.sendMessage(clans.getTranslation(user, "clans.commands.clan.transfer.not-leader"));
            return false;
        }

        // Manejar subcomandos: cancel y confirm
        if (!args.isEmpty()) {
            if (args.getFirst().equalsIgnoreCase("cancel")) {
                Clans.TransferRequest request = clans.transferRequests.remove(userId);
                if (request == null) {
                    user.sendMessage(clans.getTranslation(user, "clans.commands.clan.transfer.no-pending"));
                    return false;
                }
                user.sendMessage(clans.getTranslation(user, "clans.commands.clan.transfer.cancelled"));
                return true;
            }

            if (args.getFirst().equalsIgnoreCase("confirm")) {
                Clans.TransferRequest request = clans.transferRequests.get(userId);
                if (request == null) {
                    user.sendMessage(clans.getTranslation(user, "clans.commands.clan.transfer.no-pending"));
                    return false;
                }
                clans.transferRequests.remove(userId);
                return handleTransferLeadership(user, request.clan, request.targetUUID);
            }
        }

        // Validar argumentos
        if (args.isEmpty()) {
            String commandLabel = "/" + getTopLabel();
            user.sendMessage(clans.getTranslation(user, "clans.commands.clan.transfer.usage", "[label]", commandLabel));
            return false;
        }

        // Verificar si ya hay una solicitud pendiente
        Clans.TransferRequest existingRequest = clans.transferRequests.get(userId);
        if (existingRequest != null) {
            user.sendMessage(clans.getTranslation(user, "clans.commands.clan.transfer.pending").replace("[label]", "/" + getTopLabel() + " transfer"));
            return false;
        }

        // Obtener el jugador objetivo
        String targetName = args.getFirst();
        Player targetPlayer = getPlugin().getServer().getPlayer(targetName);
        if (targetPlayer == null) {
            user.sendMessage(clans.getTranslation(user, "clans.errors.player-not-found", "[player]", targetName));
            return false;
        }
        String targetUUID = targetPlayer.getUniqueId().toString();

        // Verificar si el objetivo está en el clan
        if (!clan.getRanks().containsKey(targetUUID)) {
            user.sendMessage(clans.getTranslation(user, "clans.commands.clan.transfer.not-in-clan", "[player]", targetName));
            return false;
        }

        // Verificar si el objetivo es el mismo líder
        if (targetUUID.equals(playerUUID)) {
            user.sendMessage(clans.getTranslation(user, "clans.commands.clan.transfer.same-player"));
            return false;
        }

        // Enviar mensaje de confirmación
        sendTransferConfirmation(user, clan, targetUUID, targetName);
        return true;
    }

    private void sendTransferConfirmation(User user, ClanManager.Clan clan, String targetUUID, String targetName) {
        UUID userId = user.getUniqueId();
        String command = "/clan transfer confirm";

        // Reproducir sonido de pregunta tensa
        Player player = user.getPlayer();
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 1.0f);

        // Cargar mensajes desde es-ES.yml
        YamlConfiguration locale = clans.getLocaleConfig(user);
        String messageTemplate = locale.getString("clans.commands.clan.transfer.confirm-message",
                "&e&lConfirma la transferencia de liderazgo:\n&eClan: &6[name]\n&eNuevo Líder: &6[target]\n&eHaz clic para confirmar o cancelar.");
        String acceptButton = locale.getString("clans.commands.clan.transfer.accept-button", "[Aceptar]");
        String acceptTooltip = locale.getString("clans.commands.clan.transfer.accept-tooltip", "Transferir liderazgo a [target]");
        String rejectButton = locale.getString("clans.commands.clan.transfer.reject-button", "[Rechazar]");
        String rejectTooltip = locale.getString("clans.commands.clan.transfer.reject-tooltip", "Cancelar transferencia");

        // Reemplazar marcadores en el mensaje
        String messageText = messageTemplate
                .replace("[name]", clan.getDisplayName())
                .replace("[target]", targetName);
        acceptTooltip = acceptTooltip.replace("[target]", targetName);
        rejectTooltip = rejectTooltip.replace("[target]", targetName);

        // Convertir el mensaje a Component con colores
        Component message = Component.empty();
        for (String line : messageText.split("\n")) {
            message = message.append(LegacyComponentSerializer.legacyAmpersand().deserialize(line)).append(Component.newline());
        }

        // Añadir botones
        message = message
                .append(Component.text(acceptButton + " ", NamedTextColor.GREEN, TextDecoration.BOLD)
                        .clickEvent(ClickEvent.runCommand(command))
                        .hoverEvent(HoverEvent.showText(LegacyComponentSerializer.legacyAmpersand().deserialize(acceptTooltip))))
                .append(Component.text(rejectButton + " ", NamedTextColor.RED, TextDecoration.BOLD)
                        .clickEvent(ClickEvent.runCommand("/clan transfer cancel"))
                        .hoverEvent(HoverEvent.showText(LegacyComponentSerializer.legacyAmpersand().deserialize(rejectTooltip))))
                .append(Component.newline());

        Player playerForMessage = user.getPlayer();
        playerForMessage.sendMessage(message);

        // Almacenar solicitud
        clans.transferRequests.put(userId, new Clans.TransferRequest(clan, targetUUID, System.currentTimeMillis()));
    }

    private boolean handleTransferLeadership(User user, ClanManager.Clan clan, String targetUUID) {
        // Determinar el nuevo rango del líder actual
        ClanManager.Clan.Rank newRank;
        if (clan.getCoLeaderCount() < clan.getMaxCoLeaders()) {
            newRank = ClanManager.Clan.Rank.CO_LEADER;
        } else if (clan.getCommanderCount() < clan.getMaxCommanders()) {
            newRank = ClanManager.Clan.Rank.COMMANDER;
        } else {
            newRank = ClanManager.Clan.Rank.MEMBER;
        }

        // Obtener el nombre del objetivo
        Player targetPlayer = getPlugin().getServer().getPlayer(UUID.fromString(targetUUID));
        String targetName = targetPlayer != null ? targetPlayer.getName() : "Desconocido";

        // Transferir liderazgo
        clan.setRank(user.getUniqueId().toString(), newRank);
        clan.setRank(targetUUID, ClanManager.Clan.Rank.LEADER);
        clan.setOwnerUUID(targetUUID); // Actualizar el ownerUUID
        clan.save();

        // Notificar al líder actual
        Player player = user.getPlayer();
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.0f);
        user.sendMessage(clans.getTranslation(user, "clans.commands.clan.transfer.success",
                "[target]", targetName,
                "[new_rank]", clans.getSettings().getRanks().getOrDefault(newRank.name().toLowerCase(), newRank.name())));

        // Notificar al nuevo líder
        User newLeader = User.getInstance(UUID.fromString(targetUUID));
        if (newLeader.isOnline()) {
            Player newLeaderPlayer = newLeader.getPlayer();
            newLeaderPlayer.playSound(newLeaderPlayer.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.0f);
            newLeader.sendMessage(clans.getTranslation(newLeader, "clans.commands.clan.transfer.new-leader",
                    "[clan]", clan.getDisplayName()));
        }

        return true;
    }

    @Override
    public Optional<List<String>> tabComplete(User user, String alias, List<String> args) {
        if (user == null || !user.hasPermission("clans.transfer")) {
            return Optional.of(List.of());
        }
        String playerUUID = user.getUniqueId().toString();
        ClanManager.Clan clan = clans.getClanManager().getClanByPlayer(playerUUID).orElse(null);
        if (clan == null || !clan.getOwnerUUID().equals(playerUUID)) {
            return Optional.of(List.of());
        }

        List<String> suggestions;
        if (args.size() <= 1) {
            suggestions = clan.getRanks().keySet().stream()
                    .filter(uuid -> !uuid.equals(playerUUID))
                    .map(uuid -> {
                        Player player = getPlugin().getServer().getPlayer(UUID.fromString(uuid));
                        return player != null ? player.getName() : "";
                    })
                    .filter(name -> !name.isEmpty())
                    .collect(Collectors.toList());
            suggestions.addAll(List.of("confirm", "cancel"));
        } else {
            suggestions = List.of();
        }

        String input = args.isEmpty() ? "" : args.getFirst().toLowerCase();
        return Optional.of(suggestions.stream()
                .filter(s -> s.toLowerCase().startsWith(input))
                .collect(Collectors.toList()));
    }
}