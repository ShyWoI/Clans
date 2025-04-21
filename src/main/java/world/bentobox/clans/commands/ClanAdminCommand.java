package world.bentobox.clans.commands;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import world.bentobox.bentobox.api.commands.CompositeCommand;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.clans.Clans;
import world.bentobox.clans.managers.ClanManager;

import java.io.File;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

public class ClanAdminCommand extends CompositeCommand {
    private final Clans clans;

    public ClanAdminCommand(Clans addon) {
        super(addon, "clanadmin");
        this.clans = addon;
        registerSubCommands();
    }

    @Override
    public void setup() {
        setPermission("clans.admin");
        setParametersHelp("clans.commands.admin.clan.parameters");
        setDescription("clans.commands.admin.clan.description");
    }

    private void registerSubCommands() {
        // Registrar subcomandos
        new ClanReloadCommand(clans, this);
        new PenitenceCommand(clans, this);
        new ClanAdminTransferCommand(clans, this); // Nuevo subcomando
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
        }

        @Override
        public void setup() {
            setPermission("clans.admin.reload");
            setParametersHelp("clans.commands.admin.clan.reload.parameters");
            setDescription("clans.commands.admin.clan.reload.description");
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
            setParametersHelp("clans.commands.admin.penitence.parameters");
            setDescription("clans.commands.admin.penitence.description");
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
        }

        @Override
        public void setup() {
            setPermission("clans.admin.penitence");
            setParametersHelp("clans.commands.admin.penitence.clear.parameters");
            setDescription("clans.commands.admin.penitence.clear.description");
        }

        @Override
        public boolean execute(User user, String label, List<String> args) {
            if (args.size() != 1) {
                user.sendMessage(clans.getTranslation(user, "clans.commands.admin.penitence.clear.usage"));
                return false;
            }

            String targetName = args.getFirst();
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
                String input = args.isEmpty() ? "" : args.getFirst().toLowerCase();
                return Optional.of(Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(name -> name.toLowerCase().startsWith(input))
                        .collect(Collectors.toList()));
            }
            return Optional.of(List.of());
        }
    }

    public static class ClanAdminTransferCommand extends CompositeCommand {
        private final Clans clans;

        public ClanAdminTransferCommand(Clans addon, CompositeCommand parent) {
            super(addon, parent, "transfer");
            this.clans = addon;
        }

        @Override
        public void setup() {
            setPermission("clans.admin.transfer");
            setParametersHelp("clans.commands.admin.clan.transfer.parameters");
            setDescription("clans.commands.admin.clan.transfer.description");
        }

        @Override
        public boolean execute(User user, String label, List<String> args) {
            // Validar argumentos
            if (args.size() != 2) {
                user.sendMessage(clans.getTranslation(user, "clans.commands.admin.clan.transfer.usage"));
                return false;
            }

            // Obtener el clan
            String clanName = args.getFirst();
            ClanManager.Clan clan = clans.getClanManager().getClanByName(clanName).orElse(null);
            if (clan == null) {
                user.sendMessage(clans.getTranslation(user, "clans.commands.admin.clan.transfer.clan-not-found", "[clan]", clanName));
                return false;
            }

            // Obtener el jugador objetivo
            String targetName = args.get(1);
            OfflinePlayer targetPlayer = Bukkit.getOfflinePlayerIfCached(targetName);
            if (targetPlayer == null || !targetPlayer.hasPlayedBefore()) {
                user.sendMessage(clans.getTranslation(user, "clans.commands.admin.clan.transfer.player-not-found", "[player]", targetName));
                return false;
            }
            String targetUUID = targetPlayer.getUniqueId().toString();

            // Verificar si el objetivo está en el clan
            if (!clan.getRanks().containsKey(targetUUID)) {
                user.sendMessage(clans.getTranslation(user, "clans.commands.admin.clan.transfer.not-in-clan", "[player]", targetName));
                return false;
            }

            // Verificar si el objetivo ya es el líder
            if (clan.getOwnerUUID().equals(targetUUID)) {
                user.sendMessage(clans.getTranslation(user, "clans.commands.admin.clan.transfer.already-leader", "[player]", targetName));
                return false;
            }

            // Transferir liderazgo
            return handleAdminTransferLeadership(user, clan, targetUUID, targetName);
        }

        private boolean handleAdminTransferLeadership(User user, ClanManager.Clan clan, String targetUUID, String targetName) {
            // Determinar el nuevo rango del líder actual
            ClanManager.Clan.Rank newRank;
            if (clan.getCoLeaderCount() < clan.getMaxCoLeaders()) {
                newRank = ClanManager.Clan.Rank.CO_LEADER;
            } else if (clan.getCommanderCount() < clan.getMaxCommanders()) {
                newRank = ClanManager.Clan.Rank.COMMANDER;
            } else {
                newRank = ClanManager.Clan.Rank.MEMBER;
            }

            // Obtener el nombre del líder actual
            OfflinePlayer oldLeaderPlayer = Bukkit.getOfflinePlayer(UUID.fromString(clan.getOwnerUUID()));
            String oldLeaderName = oldLeaderPlayer.getName() != null ? oldLeaderPlayer.getName() : "Desconocido";

            // Transferir liderazgo
            clan.setRank(clan.getOwnerUUID(), newRank);
            clan.setRank(targetUUID, ClanManager.Clan.Rank.LEADER);
            clan.setOwnerUUID(targetUUID);
            clan.save();

            // Notificar al administrador
            user.sendMessage(clans.getTranslation(user, "clans.commands.admin.clan.transfer.success",
                    "[clan]", clan.getDisplayName(),
                    "[target]", targetName));

            // Notificar al antiguo líder
            User oldLeader = User.getInstance(UUID.fromString(clan.getOwnerUUID()));
            if (oldLeader.isOnline()) {
                oldLeader.sendMessage(clans.getTranslation(oldLeader, "clans.commands.admin.clan.transfer.notify-old-leader",
                        "[clan]", clan.getDisplayName(),
                        "[new_rank]", clans.getSettings().getRanks().getOrDefault(newRank.name().toLowerCase(), newRank.name()),
                        "[admin]", user.getName()));
            }

            // Notificar al nuevo líder
            User newLeader = User.getInstance(UUID.fromString(targetUUID));
            if (newLeader.isOnline()) {
                newLeader.sendMessage(clans.getTranslation(newLeader, "clans.commands.admin.clan.transfer.notify-new-leader",
                        "[clan]", clan.getDisplayName(),
                        "[admin]", user.getName()));
            }

            return true;
        }

        @Override
        public Optional<List<String>> tabComplete(User user, String alias, List<String> args) {
            if (args.size() == 1) {
                String input = args.getFirst().toLowerCase();
                return Optional.of(clans.getClanManager().getAllClans().stream()
                        .map(clan -> clans.stripColor(clan.getDisplayName()))
                        .filter(name -> name.toLowerCase().startsWith(input))
                        .collect(Collectors.toList()));
            } else if (args.size() == 2) {
                String clanName = args.getFirst();
                ClanManager.Clan clan = clans.getClanManager().getClanByName(clanName).orElse(null);
                if (clan == null) {
                    return Optional.of(List.of());
                }
                String input = args.get(1).toLowerCase();
                return Optional.of(clan.getRanks().keySet().stream()
                        .map(uuid -> {
                            OfflinePlayer player = Bukkit.getOfflinePlayer(UUID.fromString(uuid));
                            return player.getName() != null ? player.getName() : "";
                        })
                        .filter(name -> !name.isEmpty() && name.toLowerCase().startsWith(input))
                        .collect(Collectors.toList()));
            }
            return Optional.of(List.of());
        }
    }
}