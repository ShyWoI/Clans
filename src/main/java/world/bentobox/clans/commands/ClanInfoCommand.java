package world.bentobox.clans.commands;

import org.bukkit.Bukkit;
import world.bentobox.bentobox.api.commands.CompositeCommand;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.clans.Clans;

import java.util.List;

public class ClanInfoCommand extends CompositeCommand {
    private final Clans clans;

    public ClanInfoCommand(Clans addon, CompositeCommand parent) {
        super(addon, parent, "info");
        this.clans = addon;
        Bukkit.getScheduler().runTask(addon.getPlugin(), this::configure);
    }

    @Override
    public void setup() {
        setPermission("clans.info");
        setDescription("clans.commands.clan.info.description");
        setUsage("");
    }

    private void configure() {
        setDescription(clans.getTranslation(null, "clans.commands.clan.info.description"));
        setUsage("<nombre> [tag]");
    }

    @Override
    public boolean canExecute(User user, String label, List<String> args) {
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