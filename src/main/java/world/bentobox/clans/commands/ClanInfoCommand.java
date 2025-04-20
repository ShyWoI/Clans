package world.bentobox.clans.commands;

import world.bentobox.bentobox.api.commands.CompositeCommand;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.clans.Clans;

import java.util.List;

public class ClanInfoCommand extends CompositeCommand {
    private final Clans clans;

    public ClanInfoCommand(Clans addon, CompositeCommand parent) {
        super(addon, parent, "info");
        this.clans = addon;
    }

    @Override
    public void setup() {
        setPermission("clans.info");
        setParametersHelp("clans.commands.clan.info.parameters");
        setDescription("clans.commands.clan.info.description");
    }

    @Override
    public boolean canExecute(User user, String label, List<String> args) {
        // Separamos la validación para mantener claridad en la lógica de ejecución
        if (!args.isEmpty()) {
            user.sendMessage(clans.getTranslation(user, "clans.commands.clan.info.usage"));
            return false;
        }
        return true;
    }

    @Override
    public boolean execute(User user, String label, List<String> args) {
        String playerUUID = user.getUniqueId().toString();
        if (clans.getClanManager().getClanNameByPlayer(playerUUID) == null) {
            user.sendMessage(clans.getTranslation(user, "clans.errors.not-in-clan"));
            return false;
        }
        clans.getClanManager().getClanByPlayer(playerUUID).ifPresent(clan -> {
            user.sendMessage(clans.getTranslation(user, "clans.commands.clan.info.header"));
            clans.sendClanInfo(user, clan, "clans.commands.clan.info.show");
        });
        return true;
    }
}