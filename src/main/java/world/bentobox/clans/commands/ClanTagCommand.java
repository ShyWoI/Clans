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
import java.util.stream.Collectors;

public class ClanTagCommand extends CompositeCommand {
    private final Clans clans;

    public ClanTagCommand(Clans addon, CompositeCommand parent) {
        super(addon, parent, "tag");
        this.clans = addon;
        Bukkit.getScheduler().runTask(addon.getPlugin(), this::configure);
    }

    @Override
    public void setup() {
        setPermission("clans.tag");
        setDescription("clans.commands.clan.tag.description");
        setUsage("<tag>");
    }

    private void configure() {
        setDescription(clans.getTranslation(null, "clans.commands.clan.tag.description"));
        setUsage("<tag>");
    }

    @Override
    public boolean execute(User user, String label, List<String> argsIID) {
        String playerUUID = user.getUniqueId().toString();
        UUID userId = user.getUniqueId();

        // Verificar si el jugador está en un clan
        if (clans.getClanManager().getClanNameByPlayer(playerUUID) == null) {
            user.sendMessage(clans.getTranslation(user, "clans.errors.not-in-clan"));
            return false;
        }

        // Verificar si el jugador es el líder
        Optional<ClanManager.Clan> clanOpt = clans.getClanManager().getClanByPlayer(playerUUID);
        if (clanOpt.isEmpty() || !clanOpt.get().getOwnerUUID().equals(playerUUID)) {
            user.sendMessage(clans.getTranslation(user, "clans.commands.clan.tag.only-leader"));
            return false;
        }

        ClanManager.Clan clan = clanOpt.get();

        // Verificar tiempo de espera del clan
        long currentTime = System.currentTimeMillis();
        long lastChange = clan.getLastTagChangeTimestamp();
        long timeSinceLastChange = currentTime - lastChange;
        long cooldownMinutes = clans.getSettings().getTagChangeCooldown();
        if (cooldownMinutes > 0) { // Solo verificar si el cooldown está activado
            long cooldownMillis = cooldownMinutes * 60 * 1000; // Convertir minutos a milisegundos
            if (lastChange != 0 && timeSinceLastChange < cooldownMillis) {
                long remainingTime = cooldownMillis - timeSinceLastChange;
                long remainingHours = remainingTime / (1000 * 60 * 60);
                long remainingMinutes = (remainingTime % (1000 * 60 * 60)) / (1000 * 60);
                String timeRemaining = String.format("%dh %dm", remainingHours, remainingMinutes);
                user.sendMessage(clans.getTranslation(user, "clans.commands.clan.tag.cooldown", "[time]", timeRemaining));
                return false;
            }
        }

        // Verificar si hay argumentos
        if (argsIID.isEmpty()) {
            user.sendMessage(clans.getTranslation(user, "clans.commands.clan.tag.usage", "[label]", "/" + getTopLabel()));
            return false;
        }

        // Manejar subcomandos: cancel y confirm
        if (argsIID.getFirst().equalsIgnoreCase("cancel")) {
            Clans.TagRequest request = clans.tagRequests.remove(userId);
            if (request == null) {
                user.sendMessage(clans.getTranslation(user, "clans.commands.clan.tag.no-pending"));
                return false;
            }
            user.sendMessage(clans.getTranslation(user, "clans.commands.clan.tag.cancelled"));
            return true;
        }

        if (argsIID.getFirst().equalsIgnoreCase("confirm")) {
            Clans.TagRequest request = clans.tagRequests.get(userId);
            if (request == null) {
                user.sendMessage(clans.getTranslation(user, "clans.commands.clan.tag.no-pending"));
                return false;
            }
            clans.tagRequests.remove(userId);
            handleSetTag(user, request.newTag);
            return true;
        }

        // Validar el tag
        String tag = argsIID.getFirst();
        if (tag.contains("&k") || tag.contains("§k")) {
            user.sendMessage(clans.getTranslation(user, "clans.commands.clan.invalid-format"));
            return false;
        }

        String cleanTag = clans.stripColor(tag);
        if (!clans.getClanManager().isValidTag(cleanTag)) {
            user.sendMessage(clans.getTranslation(user, "clans.commands.clan.tag.invalid"));
            return false;
        }

        // Verificar si ya hay una solicitud pendiente
        Clans.TagRequest existingRequest = clans.tagRequests.get(userId);
        if (existingRequest != null) {
            user.sendMessage(clans.getTranslation(user, "clans.commands.clan.tag.pending").replace("[label]", "/" + getTopLabel() + " tag"));
            return false;
        }

        // Enviar mensaje de confirmación
        sendTagConfirmation(user, clan.getDisplayName(), tag);
        return true;
    }

    private void sendTagConfirmation(User user, String clanName, String newTag) {
        UUID userId = user.getUniqueId();
        String command = "/clan tag confirm";
        String displayTag = clans.serializeWithReset(newTag.replace("&&", "&"));

        // Reproducir sonido de pregunta tensa
        user.getPlayer().playSound(user.getPlayer().getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 1.0f);

        // Cargar mensajes desde es-ES.yml
        YamlConfiguration locale = clans.getLocaleConfig(user);
        String messageTemplate = locale.getString("clans.commands.clan.tag.confirm-message",
                "&e&lConfirma el cambio de tag del clan:\n&eClan: &6[name]\n&eNuevo Tag: &6[tag]\n&eHaz clic para confirmar o cancelar.");
        String acceptButton = locale.getString("clans.commands.clan.tag.accept-button", "[Aceptar]");
        String acceptTooltip = locale.getString("clans.commands.clan.tag.accept-tooltip", "Cambiar tag a [tag]");
        String rejectButton = locale.getString("clans.commands.clan.tag.reject-button", "[Rechazar]");
        String rejectTooltip = locale.getString("clans.commands.clan.tag.reject-tooltip", "Cancelar cambio de tag");

        // Reemplazar marcadores en el mensaje
        String messageText = messageTemplate
                .replace("[name]", clanName)
                .replace("[tag]", displayTag);
        // Reemplazar marcadores en los tooltips
        acceptTooltip = acceptTooltip.replace("[tag]", displayTag);
        rejectTooltip = rejectTooltip.replace("[tag]", displayTag);

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
                        .clickEvent(ClickEvent.runCommand("/clan tag cancel"))
                        .hoverEvent(HoverEvent.showText(LegacyComponentSerializer.legacyAmpersand().deserialize(rejectTooltip))))
                .append(Component.newline());

        user.getPlayer().sendMessage(message);

        // Almacenar solicitud
        clans.tagRequests.put(userId, new Clans.TagRequest(clanName, newTag, System.currentTimeMillis()));
    }

    private void handleSetTag(User user, String tag) {
        String playerUUID = user.getUniqueId().toString();
        clans.getClanManager().getClanByPlayer(playerUUID).ifPresent(clan -> {
            String displayName = clan.getDisplayName();
            if (!clan.getOwnerUUID().equals(playerUUID)) {
                user.sendMessage(clans.getTranslation(user, "clans.commands.clan.tag.only-leader"));
                return;
            }

            String formattedTag = clans.serializeWithReset(tag.replace("&&", "&"));
            clans.getClanManager().setClanTag(clan.getUniqueId(), tag).thenAccept(success -> {
                if (success) {
                    user.getPlayer().playSound(user.getPlayer().getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.0f);
                    user.sendMessage(clans.getTranslation(user, "clans.commands.clan.tag.success",
                            "[tag]", formattedTag,
                            "[name]", displayName));
                } else {
                    user.sendMessage(clans.getTranslation(user, "clans.commands.clan.tag.taken", "[tag]", formattedTag));
                }
            });
        });
    }

    @Override
    public Optional<List<String>> tabComplete(User user, String alias, List<String> argsIID) {
        if (user == null || !user.hasPermission("clans.tag")) {
            return Optional.of(List.of());
        }
        String playerUUID = user.getUniqueId().toString();
        Optional<ClanManager.Clan> clanOpt = clans.getClanManager().getClanByPlayer(playerUUID);
        if (clanOpt.isEmpty() || !clanOpt.get().getOwnerUUID().equals(playerUUID)) {
            return Optional.of(List.of());
        }
        List<String> suggestions;
        if (argsIID.size() <= 1) {
            suggestions = List.of("<tag>", "confirm", "cancel");
        } else {
            suggestions = List.of();
        }
        String input = argsIID.isEmpty() ? "" : argsIID.getFirst().toLowerCase();
        return Optional.of(suggestions.stream()
                .filter(s -> s.toLowerCase().startsWith(input))
                .collect(Collectors.toList()));
    }
}