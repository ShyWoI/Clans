package world.bentobox.clans.commands;

import org.bukkit.Bukkit;
import world.bentobox.bentobox.api.commands.CompositeCommand;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.clans.Clans;

import java.util.List;

public class ClanCommand extends CompositeCommand {
    private final Clans clans;

    public ClanCommand(Clans addon) {
        super(addon, "clan");
        this.clans = addon;
        if (addon != null) {
            Bukkit.getScheduler().runTask(addon.getPlugin(), this::registerSubCommands);
        }
    }

    @Override
    public void setup() {
        setPermission("clans.use");
        setDescription("clans.commands.clan.description");
        setUsage("/clan <subcomando>");
    }

    private void registerSubCommands() {
        // Registrar subcomandos
        new ClanCreateCommand(clans, this);
        new ClanDisbandCommand(clans, this);
        new ClanTagCommand(clans, this);
        new ClanInfoCommand(clans, this);
        new ClanListCommand(clans, this);
        new ClanInviteCommand(clans, this);
        new ClanLeaveCommand(clans, this);
        new ClanKickCommand(clans, this);
        new ClanBanCommand(clans, this);
        new ClanRankCommand(clans, this);

        // Actualizar la descripción con la traducción
        if (clans != null) {
            setDescription(clans.getTranslation(null, "clans.commands.clan.description"));
        }
    }

    @Override
    public boolean execute(User user, String label, List<String> args) {
        if (!user.isPlayer()) {
            user.sendMessage(clans.getTranslation(user, "clans.errors.player-only"));
            return true;
        }

        if (!clans.getSettings().isEnabled()) {
            user.sendMessage(clans.getTranslation(user, "clans.errors.disabled"));
            return true;
        }

        if (args.isEmpty()) {
            showHelp(this, user);
            return true;
        }

        // Verificar si el subcomando existe
        String subCommand = args.getFirst().toLowerCase();
        if (getSubCommand(subCommand).isEmpty()) {
            // Asegurarse de que el reemplazo de [label] funcione correctamente
            String commandLabel = "/" + getTopLabel();
            user.sendMessage(clans.getTranslation(user, "clans.commands.clan.unknown-command", "[label]", commandLabel));
            return true;
        }

        // Dejar que CompositeCommand maneje el subcomando válido
        return false;
    }
}