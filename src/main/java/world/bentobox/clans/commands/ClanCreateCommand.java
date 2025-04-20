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

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

public class ClanCreateCommand extends CompositeCommand {
    private final Clans clans;

    public ClanCreateCommand(Clans addon, CompositeCommand parent) {
        super(addon, parent, "create");
        this.clans = addon;
    }

    @Override
    public void setup() {
        setPermission("clans.create");
        setParametersHelp("clans.commands.clan.create.parameters");
        setDescription("clans.commands.clan.create.description");
    }

    @Override
    public boolean execute(User user, String label, List<String> args) {
        String playerUUID = user.getUniqueId().toString();
        UUID userId = user.getUniqueId();

        // Verificar si el jugador está bajo penitencia
        // isUnderPenitence muestra un mensaje al usuario si está bajo penitencia y retorna true
        if (clans.isUnderPenitence(user, null, true)) {
            return false;
        }

        // Verificar si el jugador ya está en un clan
        if (clans.getClanManager().getClanNameByPlayer(playerUUID) != null) {
            user.sendMessage(clans.getTranslation(user, "clans.errors.already-in-clan"));
            return false;
        }

        if (args.isEmpty()) {
            String commandLabel = "/" + getTopLabel(); // Usamos getTopLabel() para obtener la etiqueta del comando (e.g., "clan")
            user.sendMessage(clans.getTranslation(user, "clans.commands.clan.create.usage", "[label]", commandLabel));
            return false;
        }

        // Manejar subcomandos: cancel y confirm
        if (args.getFirst().equalsIgnoreCase("cancel")) {
            Clans.CreateRequest request = clans.createRequests.remove(userId);
            if (request == null) {
                user.sendMessage(clans.getTranslation(user, "clans.commands.clan.create.no-pending"));
                return false;
            }
            user.sendMessage(clans.getTranslation(user, "clans.commands.clan.create.cancelled"));
            return true;
        }

        if (args.getFirst().equalsIgnoreCase("confirm")) {
            Clans.CreateRequest request = clans.createRequests.get(userId);
            if (request == null) {
                user.sendMessage(clans.getTranslation(user, "clans.commands.clan.create.no-pending"));
                return false;
            }
            clans.createRequests.remove(userId);
            return handleCreateClan(user, request.clanName, request.tag);
        }

        // Validar argumentos
        String displayName;
        String tag = null;

        // Parse arguments, handling quoted displayName
        StringBuilder displayNameBuilder = new StringBuilder();
        boolean inQuotes = false;
        int tagIndex = -1;

        for (int i = 0; i < args.size(); i++) {
            String arg = args.get(i);
            if (i == 0 && arg.startsWith("\"")) {
                inQuotes = true;
                displayNameBuilder.append(arg.substring(1));
                continue;
            }
            if (inQuotes) {
                if (arg.endsWith("\"")) {
                    inQuotes = false;
                    displayNameBuilder.append(" ").append(arg, 0, arg.length() - 1);
                    tagIndex = i + 1;
                    break;
                } else {
                    displayNameBuilder.append(" ").append(arg);
                }
            } else {
                displayNameBuilder.append(arg);
                tagIndex = i + 1;
                break;
            }
        }

        displayName = displayNameBuilder.toString();
        // Remove quotes if they exist (for consistency)
        if (displayName.startsWith("\"") && displayName.endsWith("\"")) {
            displayName = displayName.substring(1, displayName.length() - 1);
        }
        if (tagIndex != -1 && tagIndex < args.size()) {
            tag = String.join(" ", args.subList(tagIndex, args.size()));
        }
        if (tag != null && tag.isEmpty()) {
            tag = null;
        }

        // Verificar si ya hay una solicitud pendiente
        Clans.CreateRequest existingRequest = clans.createRequests.get(userId);
        if (existingRequest != null) {
            user.sendMessage(clans.getTranslation(user, "clans.commands.clan.create.pending").replace("[label]", "/" + getTopLabel() + " create"));
            return false;
        }

        // Validaciones trasladadas desde handleCreateClan
        // Verificar fondos
        if (clans.getEconomy() != null && !clans.getEconomy().has(user.getPlayer(), clans.getSettings().getCreationCost())) {
            user.sendMessage(clans.getTranslation(user, "clans.errors.insufficient-funds", "[amount]", String.valueOf(clans.getSettings().getCreationCost())));
            return false;
        }

        // Verificar formato de displayName
        if (displayName.toLowerCase().contains("&k") || displayName.contains("§k")) {
            user.sendMessage(clans.getTranslation(user, "clans.commands.clan.invalid-format"));
            return false;
        }

        // Verificar longitud y validez del nombre
        String cleanName = clans.stripColor(displayName);
        if (!clans.getClanManager().isValidClanName(cleanName, clans.getSettings().getMinNameLength(), clans.getSettings().getMaxNameLength())) {
            user.sendMessage(clans.getTranslation(user, "clans.errors.invalid-clan-name",
                    "[number]", String.valueOf(clans.getSettings().getMinNameLength()),
                    "[max]", String.valueOf(clans.getSettings().getMaxNameLength())));
            return false;
        }

        // Verificar si el nombre ya está tomado
        if (clans.getClanManager().isClanNameTaken(displayName)) {
            user.sendMessage(clans.getTranslation(user, "clans.commands.clan.create.name-taken", "[name]", displayName));
            return false;
        }

        // Verificar formato y validez del tag
        String cleanTag = tag != null ? clans.stripColor(tag) : null;
        if (tag != null) {
            if (tag.toLowerCase().contains("&k") || tag.contains("§k")) {
                user.sendMessage(clans.getTranslation(user, "clans.commands.clan.invalid-format"));
                return false;
            }
            if (!clans.getClanManager().isValidTag(cleanTag)) {
                user.sendMessage(clans.getTranslation(user, "clans.commands.clan.tag.invalid"));
                return false;
            }
        }

        // Generar tag por defecto si no se proporciona
        String defaultTag = tag != null ? tag : cleanName.substring(0, Math.min(3, cleanName.length()));
        if (tag == null && !clans.getClanManager().isValidTag(defaultTag)) {
            user.sendMessage(clans.getTranslation(user, "clans.commands.clan.tag.invalid"));
            return false;
        }

        // Enviar mensaje de confirmación
        sendCreateConfirmation(user, displayName, tag);
        return true;
    }

    private void sendCreateConfirmation(User user, String displayName, String tag) {
        UUID userId = user.getUniqueId();
        String command = "/clan create confirm";
        String cleanName = clans.stripColor(displayName);
        String displayTag = tag != null ? tag : cleanName.substring(0, Math.min(3, cleanName.length()));

        // Reproducir sonido de pregunta tensa
        Player player = user.getPlayer();
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 1.0f);

        // Cargar mensajes desde es-ES.yml
        YamlConfiguration locale = clans.getLocaleConfig(user);
        String messageTemplate = locale.getString("clans.commands.clan.create.confirm-message",
                "&e&lConfirma la creación del clan:\n&eClan: &6[name]\n&eTag: &6[tag]\n&eCosto: &6[cost]\n&eHaz clic para confirmar o cancelar.");
        String acceptButton = locale.getString("clans.commands.clan.create.accept-button", "[Aceptar]");
        String acceptTooltip = locale.getString("clans.commands.clan.create.accept-tooltip", "Crear clan [name]");
        String rejectButton = locale.getString("clans.commands.clan.create.reject-button", "[Rechazar]");
        String rejectTooltip = locale.getString("clans.commands.clan.create.reject-tooltip", "Cancelar creación");

        // Reemplazar marcadores en el mensaje
        String messageText = messageTemplate
                .replace("[name]", displayName)
                .replace("[tag]", displayTag)
                .replace("[cost]", String.valueOf(clans.getSettings().getCreationCost()));
        // Reemplazar marcadores en los tooltips
        acceptTooltip = acceptTooltip.replace("[name]", displayName);
        rejectTooltip = rejectTooltip.replace("[name]", displayName);

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
                        .clickEvent(ClickEvent.runCommand("/clan create cancel"))
                        .hoverEvent(HoverEvent.showText(LegacyComponentSerializer.legacyAmpersand().deserialize(rejectTooltip))))
                .append(Component.newline());

        Player playerForMessage = user.getPlayer();
        playerForMessage.sendMessage(message);

        // Almacenar solicitud
        clans.createRequests.put(userId, new Clans.CreateRequest(displayName, tag, System.currentTimeMillis()));
    }

    private boolean handleCreateClan(User user, String displayName, String tag) {
        // Formatear el tag con códigos de color correctamente
        String cleanName = clans.stripColor(displayName);
        String formattedTag = tag != null ? LegacyComponentSerializer.legacyAmpersand().serialize(
                LegacyComponentSerializer.legacyAmpersand().deserialize(tag.replace("&&", "&"))
        ) : clans.serializeWithReset(cleanName.substring(0, Math.min(3, cleanName.length())));
        // Formatear el displayName con códigos de color
        String formattedDisplayName = clans.serializeWithReset(displayName);

        String playerUUID = user.getUniqueId().toString();
        String uniqueId = clans.getClanManager().createClan(formattedDisplayName, formattedTag, playerUUID, clans.getSettings().getMaxCoLeaders(), clans.getSettings().getMaxCommanders(), clans.getSettings().getMaxMembers());
        if (uniqueId != null) {
            if (clans.getEconomy() != null) {
                Player player = user.getPlayer();
                clans.getEconomy().withdrawPlayer(player, clans.getSettings().getCreationCost());
            }
            Player player = user.getPlayer();
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.0f);
            user.sendMessage(clans.getTranslation(user, "clans.commands.clan.create.success",
                    "[name]", formattedDisplayName,
                    "[tag]", formattedTag,
                    "[rank]", clans.getSettings().getRanks().getOrDefault("leader", "Líder")));
            user.sendMessage(clans.getTranslation(user, "clans.commands.clan.create.island-created"));
            return true;
        } else {
            user.sendMessage(clans.getTranslation(user, "clans.commands.clan.create.error"));
            return false;
        }
    }

    @Override
    public Optional<List<String>> tabComplete(User user, String alias, List<String> args) {
        if (user == null || !user.hasPermission("clans.create")) {
            return Optional.of(List.of());
        }
        String playerUUID = user.getUniqueId().toString();
        if (clans.getClanManager().getClanNameByPlayer(playerUUID) != null) {
            return Optional.of(List.of());
        }
        List<String> suggestions;
        if (args.size() <= 1) {
            suggestions = List.of("<nombre>", "confirm", "cancel");
        } else {
            suggestions = List.of("<tag>");
        }
        String input = args.isEmpty() ? "" : args.getFirst().toLowerCase();
        return Optional.of(suggestions.stream()
                .filter(s -> s.toLowerCase().startsWith(input))
                .collect(Collectors.toList()));
    }
}