package world.bentobox.clans.commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import world.bentobox.bentobox.api.commands.CompositeCommand;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.clans.Clans;
import world.bentobox.clans.managers.ClanManager;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class ClanLeaveCommand extends CompositeCommand {
    private final Clans clans;

    public ClanLeaveCommand(Clans addon, CompositeCommand parent) {
        super(addon, parent, "leave");
        this.clans = addon;
        Bukkit.getScheduler().runTask(addon.getPlugin(), this::configure);
    }

    @Override
    public void setup() {
        setPermission("clans.leave");
        setDescription("clans.commands.clan.leave.description");
        setUsage("");
    }

    private void configure() {
        setDescription(clans.getTranslation(null, "clans.commands.clan.leave.description"));
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

        // Verificar si el jugador es el líder
        if (clan.getOwnerUUID().equals(playerUUID)) {
            user.sendMessage(clans.getTranslation(user, "clans.commands.clan.leave.leader-cannot-leave"));
            return false;
        }

        // Verificar si el jugador ya está bajo penitencia
        if (clans.isUnderPenitence(user, null, true)) {
            return false;
        }

        // Manejar cancelación
        if (!args.isEmpty() && args.getFirst().equalsIgnoreCase("cancel")) {
            Clans.LeaveRequest request = clans.leaveRequests.remove(userId);
            if (request == null) {
                user.sendMessage(clans.getTranslation(user, "clans.commands.clan.leave.no-pending"));
                return false;
            }
            user.sendMessage(clans.getTranslation(user, "clans.commands.clan.leave.cancelled"));
            return true;
        }

        // Verificar si ya hay una solicitud pendiente
        Clans.LeaveRequest existingRequest = clans.leaveRequests.get(userId);
        if (existingRequest != null) {
            // Confirmar si el comando coincide con la solicitud
            if (!args.isEmpty() && clans.stripColor(args.getFirst()).equalsIgnoreCase(clans.stripColor(existingRequest.clanName))) {
                clans.leaveRequests.remove(userId);
                // Eliminar al jugador del clan
                clan.removeMember(playerUUID);
                // Guardar el clan actualizado
                clans.getClanManager().saveClans();
                // Iniciar penitencia para el jugador
                clans.startPenitence(user);
                // Reproducir sonido de confirmación
                user.getPlayer().playSound(user.getPlayer().getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.0f);
                // Notificar al jugador
                user.sendMessage(clans.getTranslation(user, "clans.commands.clan.leave.success", "[name]", clan.getDisplayName()));
                // Notificar a los demás miembros
                clan.getRanks().keySet().stream()
                        .map(uuid -> User.getInstance(UUID.fromString(uuid)))
                        .filter(u -> !u.getUniqueId().equals(userId))
                        .forEach(member -> member.sendMessage(clans.getTranslation(member, "clans.commands.clan.leave.notify",
                                "[player]", user.getName(), "[name]", clan.getDisplayName())));
                return true;
            } else {
                user.sendMessage(clans.getTranslation(user, "clans.commands.clan.leave.pending"));
                return false;
            }
        }

        // Enviar mensaje de confirmación
        sendLeaveConfirmation(user, clan.getDisplayName());
        return true;
    }

    private void sendLeaveConfirmation(User user, String clanName) {
        UUID userId = user.getUniqueId();
        String command = "/clan leave " + clans.stripColor(clanName);

        // Reproducir sonido de pregunta tensa
        user.getPlayer().playSound(user.getPlayer().getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.5f);

        // Cargar mensajes desde es-ES.yml
        YamlConfiguration locale = clans.getLocaleConfig(user);
        String messageTemplate = locale.getString("clans.commands.clan.leave.confirm-message",
                "&e&lConfirma que deseas abandonar el clan:\n&eClan: &6[name]\n\n&eHaz clic para confirmar o cancelar.");
        String acceptButton = locale.getString("clans.commands.clan.leave.accept-button", "[Aceptar]");
        String acceptTooltip = locale.getString("clans.commands.clan.leave.accept-tooltip", "Abandonar clan [name]");
        String rejectButton = locale.getString("clans.commands.clan.leave.reject-button", "[Rechazar]");
        String rejectTooltip = locale.getString("clans.commands.clan.leave.reject-tooltip", "Cancelar abandono");

        // Reemplazar marcadores en el mensaje
        String messageText = messageTemplate.replace("[name]", clanName);
        // Reemplazar marcadores en los tooltips
        acceptTooltip = acceptTooltip.replace("[name]", clans.stripColor(clanName));
        rejectTooltip = rejectTooltip.replace("[name]", clans.stripColor(clanName));

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
                        .clickEvent(ClickEvent.runCommand("/clan leave cancel"))
                        .hoverEvent(HoverEvent.showText(LegacyComponentSerializer.legacyAmpersand().deserialize(rejectTooltip))))
                .append(Component.newline());

        user.getPlayer().sendMessage(message);

        // Almacenar solicitud
        clans.leaveRequests.put(userId, new Clans.LeaveRequest(clanName, System.currentTimeMillis()));
    }
}