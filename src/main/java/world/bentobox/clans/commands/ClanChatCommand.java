package world.bentobox.clans.commands;

import net.kyori.adventure.text.Component;
import world.bentobox.bentobox.api.commands.CompositeCommand;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.clans.Clans;
import world.bentobox.clans.managers.ClanManager;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class ClanChatCommand extends CompositeCommand {
    private final Clans clans;

    public ClanChatCommand(Clans addon, CompositeCommand parent) {
        super(addon, parent, "chat");
        this.clans = addon;
    }

    @Override
    public void setup() {
        setPermission("clans.chat");
        setParametersHelp("clans.commands.clan.chat.parameters");
        setDescription("clans.commands.clan.chat.description");
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

        // Sí hay argumentos, enviar el mensaje directamente al clan
        if (!args.isEmpty()) {
            String messageText = String.join(" ", args);
            Component message = Component.text(messageText);
            clans.sendClanMessage(user, clan, message);
            return true;
        }

        // Si no hay argumentos, alternar el modo de chat del clan
        boolean isChatEnabled = clans.clanChatEnabled.getOrDefault(userId, false);
        if (isChatEnabled) {
            clans.clanChatEnabled.remove(userId);
            user.sendMessage(clans.getTranslation(user, "clans.commands.clan.chat.disabled"));
        } else {
            clans.clanChatEnabled.put(userId, true);
            user.sendMessage(clans.getTranslation(user, "clans.commands.clan.chat.enabled"));
        }

        return true;
    }

    @Override
    public Optional<List<String>> tabComplete(User user, String alias, List<String> args) {
        return Optional.of(List.of());
    }
}