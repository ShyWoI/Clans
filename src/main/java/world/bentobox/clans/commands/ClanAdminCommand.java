package world.bentobox.clans.commands;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import world.bentobox.bentobox.api.commands.CompositeCommand;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.clans.Clans;

import java.io.File;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class ClanAdminCommand extends CompositeCommand {
    private final Clans clans;

    public ClanAdminCommand(Clans addon) {
        super(addon, "clanadmin");
        this.clans = addon;
        // Programar el registro de subcomandos para el próximo tick
        if (addon != null) {
            Bukkit.getScheduler().runTask(addon.getPlugin(), this::registerSubCommands);
        } else {
            getLogger().warning("Advertencia: addon es null en ClanAdminCommand. Los subcomandos no se registrarán.");
        }
    }

    @Override
    public void setup() {
        setPermission("clans.admin");
        setDescription("Temporal description"); // Descripción temporal
        setUsage("<comando>");
        // Programar la configuración de traducciones
        if (getAddon() != null) {
            Bukkit.getScheduler().runTask(getAddon().getPlugin(), this::configure);
        }
    }

    private void configure() {
        // Actualizar con la traducción
        if (clans != null) {
            setDescription(clans.getTranslation(null, "clans.commands.admin.clan.description"));
        }
    }

    private void registerSubCommands() {
        // Registrar subcomandos
        new ClanReloadCommand(clans, this);
        new PenitenceCommand(clans, this);
    }

    @Override
    public boolean execute(User user, String label, List<String> args) {
        if (args.isEmpty()) {
            showHelp(this, user);
            return true;
        }
        return false;
    }

    public static class ClanReloadCommand extends CompositeCommand {
        private final Clans clans;

        public ClanReloadCommand(Clans addon, CompositeCommand parent) {
            super(addon, parent, "reload");
            this.clans = addon;
            // Programar la configuración para el próximo tick
            if (addon != null) {
                Bukkit.getScheduler().runTask(addon.getPlugin(), this::configure);
            } else {
                getLogger().warning("Advertencia: addon es null en ClanReloadCommand. La configuración no se ejecutará.");
            }
        }

        @Override
        public void setup() {
            setPermission("clans.admin.reload");
            setDescription("Temporal description"); // Descripción temporal
            setUsage("");
        }

        private void configure() {
            // Actualizar con la traducción
            if (clans != null) {
                setDescription(clans.getTranslation(null, "clans.commands.admin.clan.reload.description"));
            }
        }

        @Override
        public boolean execute(User user, String label, List<String> args) {
            // Recargar configuración
            if (clans.loadConfiguration()) {
                user.sendMessage(clans.getTranslation(user, "clans.commands.admin.clan.reload.error-config"));
                return false;
            }
            // Recargar localizaciones
            if (clans.loadLocalizations()) {
                user.sendMessage(clans.getTranslation(user, "clans.commands.admin.clan.reload.error-locale"));
                return false;
            }
            // Recargar lista de clanes si el archivo existe
            File clanListFile = new File(clans.getDataFolder(), "panels/clan_list.yml");
            if (clanListFile.exists()) {
                clans.getPlugin().getLogger().info("Reloading clan_list.yml");
                clans.getClanManager().loadAllClans();
            }
            user.sendMessage(clans.getTranslation(user, "clans.commands.admin.clan.reload.success"));
            return true;
        }
    }

    public static class PenitenceCommand extends CompositeCommand {
        private final Clans clans;

        public PenitenceCommand(Clans addon, CompositeCommand parent) {
            super(addon, parent, "penitence");
            this.clans = addon;
            // Programar el registro de subcomandos para el próximo tick
            if (addon != null) {
                Bukkit.getScheduler().runTask(addon.getPlugin(), this::registerSubCommands);
            } else {
                getLogger().warning("Advertencia: addon es null en PenitenceCommand. Los subcomandos no se registrarán.");
            }
        }

        @Override
        public void setup() {
            setPermission("clans.admin.penitence");
            setDescription("Temporal description"); // Descripción temporal
            setUsage("<comando>");
            // Programar la configuración de traducciones
            if (getAddon() != null) {
                Bukkit.getScheduler().runTask(getAddon().getPlugin(), this::configure);
            }
        }

        private void configure() {
            // Actualizar con la traducción
            if (clans != null) {
                setDescription(clans.getTranslation(null, "clans.commands.admin.penitence.description"));
            }
        }

        private void registerSubCommands() {
            // Registrar subcomandos de penitence
            new PenitenceClearCommand(clans, this);
        }

        @Override
        public boolean execute(User user, String label, List<String> args) {
            if (args.isEmpty()) {
                showHelp(this, user);
                return true;
            }
            return false;
        }
    }

    public static class PenitenceClearCommand extends CompositeCommand {
        private final Clans clans;

        public PenitenceClearCommand(Clans addon, CompositeCommand parent) {
            super(addon, parent, "clear");
            this.clans = addon;
            // Programar la configuración para el próximo tick
            if (addon != null) {
                Bukkit.getScheduler().runTask(addon.getPlugin(), this::configure);
            } else {
                getLogger().warning("Advertencia: addon es null en PenitenceClearCommand. La configuración no se ejecutará.");
            }
        }

        @Override
        public void setup() {
            setPermission("clans.admin.penitence");
            setDescription("Temporal description"); // Descripción temporal
            setUsage("Temporal usage");
        }

        private void configure() {
            // Actualizar con la traducción
            if (clans != null) {
                setDescription(clans.getTranslation(null, "clans.commands.admin.penitence.clear.description"));
                setUsage(clans.getTranslation(null, "clans.commands.admin.penitence.clear.usage"));
            }
        }

        @Override
        public boolean execute(User user, String label, List<String> args) {
            if (args.size() != 1) {
                user.sendMessage(clans.getTranslation(user, "clans.commands.admin.penitence.clear.usage"));
                return false;
            }

            String targetName = args.get(0);
            OfflinePlayer targetPlayer = Bukkit.getOfflinePlayerIfCached(targetName);
            if (targetPlayer == null || !targetPlayer.hasPlayedBefore()) {
                user.sendMessage(clans.getTranslation(user, "clans.commands.admin.penitence.clear.player-not-found",
                        "[player]", targetName));
                return false;
            }

            User targetUser = User.getInstance(targetPlayer.getUniqueId());
            // Verificar si el jugador está en penitencia
            if (clans.getPenitenceRemainingTime(targetUser) == 0) {
                user.sendMessage(clans.getTranslation(user, "clans.commands.admin.penitence.clear.not-in-penitence",
                        "[player]", targetName));
                return false;
            }
            clans.clearPenitence(targetUser);
            return true;
        }

        @Override
        public Optional<List<String>> tabComplete(User user, String alias, List<String> args) {
            if (args.size() <= 1) {
                String input = args.isEmpty() ? "" : args.get(0).toLowerCase();
                return Optional.of(Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(name -> name.toLowerCase().startsWith(input))
                        .collect(Collectors.toList()));
            }
            return Optional.of(List.of());
        }
    }
}