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

public class ClanDisbandCommand extends CompositeCommand {
    private final Clans clans;

    public ClanDisbandCommand(Clans addon, CompositeCommand parent) {
        super(addon, parent, "disband");
        this.clans = addon;
    }

    @Override
    public void setup() {
        setPermission("clans.disband");
        setParametersHelp("clans.commands.clan.disband.parameters");
        setDescription("clans.commands.clan.disband.description");
    }


    @Override
    public boolean execute(User user, String label, List<String> args) {
        String playerUUID = user.getUniqueId().toString();
        UUID userId = user.getUniqueId();

        if (clans.getClanManager().getClanNameByPlayer(playerUUID) == null) {
            user.sendMessage(clans.getTranslation(user, "clans.errors.not-in-clan"));
            return false;
        }
        Optional<ClanManager.Clan> clanOpt = clans.getClanManager().getClanByPlayer(playerUUID);
        if (clanOpt.isEmpty() || !clanOpt.get().getOwnerUUID().equals(playerUUID)) {
            user.sendMessage(clans.getTranslation(user, "clans.commands.clan.disband.only-leader"));
            return false;
        }

        // Verificar si el líder está bajo penitencia
        // isUnderPenitence muestra un mensaje al usuario si está bajo penitencia y retorna true
        if (clans.isUnderPenitence(user, null, true)) {
            return false;
        }

        ClanManager.Clan clan = clanOpt.get();
        String clanName = clan.getCleanName();

        // Manejar cancelación
        if (!args.isEmpty() && args.getFirst().equalsIgnoreCase("cancel")) {
            Clans.DisbandRequest request = clans.disbandRequests.remove(userId);
            if (request == null) {
                user.sendMessage(clans.getTranslation(user, "clans.commands.clan.disband.no-pending"));
                return false;
            }
            user.sendMessage(clans.getTranslation(user, "clans.commands.clan.disband.cancelled"));
            return true;
        }

        // Verificar si ya hay una solicitud pendiente
        Clans.DisbandRequest existingRequest = clans.disbandRequests.get(userId);
        if (existingRequest != null) {
            // Confirmar si el comando coincide con la solicitud
            if (args.isEmpty() || clans.stripColor(args.getFirst()).equalsIgnoreCase(clans.stripColor(existingRequest.clanName))) {
                clans.disbandRequests.remove(userId);
                clans.getClanManager().disbandClan(clan.getUniqueId()).thenAccept(success -> {
                    if (success) {
                        // Reembolsar al líder si está configurado
                        if (clans.getEconomy() != null && clans.getSettings().getDisbandRefunded() > 0) {
                            Player player = user.getPlayer();
                            clans.getEconomy().depositPlayer(player, clans.getSettings().getDisbandRefunded());
                        }

                        // Aplicar penitencia a todos los miembros del clan
                        clan.getRanks().keySet().forEach(memberUUID -> {
                            User member = User.getInstance(UUID.fromString(memberUUID));
                            clans.startPenitence(member);
                        });

                        // Reproducir sonido de confirmación
                        Player player = user.getPlayer();
                        player.playSound(player.getLocation(), Sound.ITEM_SHIELD_BREAK, 0.5f, 1.0f);
                        // Notificar al líder
                        user.sendMessage(clans.getTranslation(user, "clans.commands.clan.disband.success"));
                        // Notificar a los demás miembros
                        // User.getInstance maneja usuarios fuera de línea si es necesario
                        clan.getRanks().keySet().stream()
                                .map(uuid -> User.getInstance(UUID.fromString(uuid)))
                                .filter(u -> !u.getUniqueId().equals(userId))
                                .forEach(member -> member.sendMessage(clans.getTranslation(member, "clans.commands.clan.disband.notify",
                                        "[clan]", clan.getDisplayName())));
                    } else {
                        user.sendMessage(clans.getTranslation(user, "clans.commands.clan.disband.error"));
                    }
                });
                return true;
            } else {
                user.sendMessage(clans.getTranslation(user, "clans.commands.clan.disband.pending"));
                return false;
            }
        }

        // Enviar mensaje de confirmación
        sendDisbandConfirmation(user, clanName);
        return true;
    }

    private void sendDisbandConfirmation(User user, String clanName) {
        UUID userId = user.getUniqueId();
        String command = "/clan disband " + clanName;

        // Reproducir sonido de pregunta tensa
        Player player = user.getPlayer();
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.5f);

        // Cargar mensajes desde es-ES.yml
        YamlConfiguration locale = clans.getLocaleConfig(user);
        String messageTemplate = locale.getString("clans.commands.clan.disband.confirm-message",
                "&e&lConfirma la disolución del clan:\n&eClan: &6[name]\n\n&eHaz clic para confirmar o cancelar.");
        String acceptButton = locale.getString("clans.commands.clan.disband.accept-button", "[Aceptar]");
        String acceptTooltip = locale.getString("clans.commands.clan.disband.accept-tooltip", "Disolver clan [name]");
        String rejectButton = locale.getString("clans.commands.clan.disband.reject-button", "[Rechazar]");
        String rejectTooltip = locale.getString("clans.commands.clan.disband.reject-tooltip", "Cancelar disolución");

        // Reemplazar marcadores en el mensaje
        String messageText = messageTemplate.replace("[name]", clanName);
        // Reemplazar marcadores en los tooltips
        acceptTooltip = acceptTooltip.replace("[name]", clanName);
        rejectTooltip = rejectTooltip.replace("[name]", clanName);

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
                        .clickEvent(ClickEvent.runCommand("/clan disband cancel"))
                        .hoverEvent(HoverEvent.showText(LegacyComponentSerializer.legacyAmpersand().deserialize(rejectTooltip))))
                .append(Component.newline());

        Player playerForMessage = user.getPlayer();
        playerForMessage.sendMessage(message);

        // Almacenar solicitud
        clans.disbandRequests.put(userId, new Clans.DisbandRequest(clanName, System.currentTimeMillis()));
    }

    @Override
    public Optional<List<String>> tabComplete(User user, String alias, List<String> args) {
        if (user == null || !user.hasPermission("clans.disband")) {
            return Optional.of(List.of());
        }
        String playerUUID = user.getUniqueId().toString();
        Optional<ClanManager.Clan> clanOpt = clans.getClanManager().getClanByPlayer(playerUUID);
        if (clanOpt.isEmpty() || !clanOpt.get().getOwnerUUID().equals(playerUUID)) {
            return Optional.of(List.of());
        }
        List<String> suggestions = List.of(clanOpt.get().getCleanName());
        String input = args.isEmpty() ? "" : args.getFirst().toLowerCase();
        return Optional.of(suggestions.stream()
                .filter(s -> s.toLowerCase().startsWith(input))
                .collect(Collectors.toList()));
    }
}