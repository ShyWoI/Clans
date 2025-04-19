package world.bentobox.clans.commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import world.bentobox.bentobox.api.commands.CompositeCommand;
import world.bentobox.bentobox.api.panels.builders.PanelBuilder;
import world.bentobox.bentobox.api.panels.builders.PanelItemBuilder;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.clans.Clans;
import world.bentobox.clans.managers.ClanManager;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

public class ClanInviteCommand extends CompositeCommand {
    private final Clans clans;
    private final Map<UUID, List<InviteRequest>> inviteRequests = new HashMap<>();

    public static class InviteRequest {
        final String clanName;
        final UUID inviterId;
        final long timestamp;

        InviteRequest(String clanName, UUID inviterId, long timestamp) {
            this.clanName = clanName;
            this.inviterId = inviterId;
            this.timestamp = timestamp;
        }
    }

    public ClanInviteCommand(Clans addon, CompositeCommand parent) {
        super(addon, parent, "invite");
        this.clans = addon;
        if (addon != null) {
            Bukkit.getScheduler().runTask(addon.getPlugin(), this::registerSubCommands);
        } else {
            getLogger().warning("Advertencia: addon es null en ClanInviteCommand. Los subcomandos no se registrarán.");
        }
    }

    @Override
    public void setup() {
        setPermission("clans.invite");
        setDescription("Invite a player to your clan");
        setUsage("[player]");
    }

    private void registerSubCommands() {
        new ClanInviteAcceptCommand(clans, this);
        new ClanInviteDeclineCommand(clans, this);
        if (clans != null) {
            setDescription(clans.getTranslation(null, "clans.commands.clan.invite.description"));
        }
    }

    @Override
    public boolean execute(User user, String label, List<String> args) {
        String playerUUID = user.getUniqueId().toString();

        // Verificar si el jugador está en un clan
        Optional<ClanManager.Clan> clanOpt = clans.getClanManager().getClanByPlayer(playerUUID);
        if (clanOpt.isEmpty()) {
            user.sendMessage(clans.getTranslation(user, "clans.errors.not-in-clan"));
            return false;
        }

        ClanManager.Clan clan = clanOpt.get();
        String clanName = clan.getDisplayName();

        // Verificar permisos (líder, co-líder o comandante)
        Integer rankValue = clan.getRanks().get(playerUUID);
        if (rankValue == null ||
                (rankValue != ClanManager.Clan.Rank.LEADER.getValue() &&
                        rankValue != ClanManager.Clan.Rank.CO_LEADER.getValue() &&
                        rankValue != ClanManager.Clan.Rank.COMMANDER.getValue())) {
            user.sendMessage(clans.getTranslation(user, "clans.commands.clan.invite.no-permission"));
            return false;
        }

        // Verificar si el clan está lleno
        if (clan.getTotalMemberCount() >= clan.getMaxMembers()) {
            user.sendMessage(clans.getTranslation(user, "clans.commands.clan.invite.clan-full", "[max]", String.valueOf(clan.getMaxMembers())));
            return false;
        }

        // Sí se proporciona un nombre de jugador, intentar invitar directamente
        if (!args.isEmpty()) {
            String targetName = args.getFirst();
            Player targetPlayer = Bukkit.getPlayer(targetName);
            if (targetPlayer == null || !targetPlayer.isOnline()) {
                user.sendMessage(clans.getTranslation(user, "clans.errors.player-not-online", "[player]", targetName));
                return false;
            }
            User target = User.getInstance(targetPlayer);
            if (target.getUniqueId().toString().equals(playerUUID)) {
                user.sendMessage(clans.getTranslation(user, "clans.commands.clan.invite.cannot-invite-self"));
                return false;
            }
            if (target.getUniqueId().toString().equals(clan.getOwnerUUID())) {
                user.sendMessage(clans.getTranslation(user, "clans.commands.clan.invite.cannot-invite-leader"));
                return false;
            }
            if (clans.getClanManager().getClanNameByPlayer(target.getUniqueId().toString()) != null) {
                user.sendMessage(clans.getTranslation(user, "clans.errors.player-in-clan", "[player]", target.getName()));
                return false;
            }
            if (clan.isBanned(target.getUniqueId().toString())) {
                user.sendMessage(clans.getTranslation(user, "clans.commands.clan.invite.player-banned", "[player]", target.getName()));
                return false;
            }
            if (clans.isUnderPenitence(target, user, true)) {
                return false; // El mensaje ya se envía dentro de isUnderPenitence
            }
            sendInviteConfirmation(user, target, clanName);
            return true;
        }

        // Cargar el archivo de configuración del panel
        File panelFile = new File(getAddon().getDataFolder(), "panels/clan_invite.yml");
        if (!panelFile.exists()) {
            user.sendMessage(clans.getTranslation(user, "clans.commands.clan.list.error-panel"));
            return false;
        }

        YamlConfiguration panelConfig = YamlConfiguration.loadConfiguration(panelFile);

        // Obtener lista de jugadores en línea, excluyendo al líder y jugadores ya en un clan
        List<Player> onlinePlayers = new ArrayList<>(Bukkit.getOnlinePlayers());
        onlinePlayers.removeIf(p -> p.getUniqueId().toString().equals(playerUUID));
        onlinePlayers.removeIf(p -> p.getUniqueId().toString().equals(clan.getOwnerUUID()));
        onlinePlayers.removeIf(p -> clans.getClanManager().getClanNameByPlayer(p.getUniqueId().toString()) != null);
        onlinePlayers.removeIf(p -> clan.isBanned(p.getUniqueId().toString()));
        onlinePlayers.removeIf(p -> clans.isUnderPenitence(User.getInstance(p), user, false));

        int playerCount = onlinePlayers.size();
        if (playerCount == 0) {
            user.sendMessage(clans.getTranslation(user, "clans.commands.clan.invite.no-players-available"));
            return false;
        }

        // Verificar si hay argumentos para la paginación
        int page = 1;
        if (!args.isEmpty()) {
            try {
                page = Integer.parseInt(args.getFirst());
                if (page < 1) {
                    user.sendMessage(clans.getTranslation(user, "clans.commands.clan.invite.usage", "[label]", "/" + getTopLabel()));
                    return false;
                }
            } catch (NumberFormatException e) {
                user.sendMessage(clans.getTranslation(user, "clans.commands.clan.invite.usage", "[label]", "/" + getTopLabel()));
                return false;
            }
        }

        // Configurar el panel: 5 filas fijas (45 slots)
        int rows = 5;
        int slots = rows * 9;
        int itemsPerPage = 21;
        int startIndex = (page - 1) * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, playerCount);

        PanelBuilder panel = new PanelBuilder()
                .name(clans.getTranslation(user, panelConfig.getString("clan_invite.title", "clans.panels.clan_invite.title")))
                .user(user)
                .size(slots);

        // Añadir fondo y bordes
        String backgroundIcon = panelConfig.getString("clan_invite.background.icon", "BLACK_STAINED_GLASS_PANE");
        String backgroundTitle = panelConfig.getString("clan_invite.background.title", "&b&r");
        String borderIcon = panelConfig.getString("clan_invite.border.icon", "GRAY_STAINED_GLASS_PANE");
        String borderTitle = panelConfig.getString("clan_invite.border.title", "&b&r");
        for (int row = 1; row <= rows; row++) {
            for (int col = 1; col <= 9; col++) {
                int slot = (row - 1) * 9 + (col - 1);
                if (row == 1 || row == rows || col == 1 || col == 9) {
                    panel.item(slot, new PanelItemBuilder()
                            .icon(new ItemStack(Material.valueOf(borderIcon)))
                            .name(borderTitle)
                            .build());
                } else {
                    panel.item(slot, new PanelItemBuilder()
                            .icon(new ItemStack(Material.valueOf(backgroundIcon)))
                            .name(backgroundTitle)
                            .build());
                }
            }
        }

        // Leer posiciones de player_button desde clan_invite.yml (filas 2 a 4)
        List<int[]> playerSlots = new ArrayList<>();
        for (int row = 2; row <= 4; row++) {
            String rowKey = "clan_invite.content." + row;
            if (panelConfig.contains(rowKey)) {
                ConfigurationSection rowSection = panelConfig.getConfigurationSection(rowKey);
                if (rowSection != null) {
                    for (String colKey : rowSection.getKeys(false)) {
                        int col = Integer.parseInt(colKey);
                        String value = rowSection.getString(colKey);
                        if (Objects.equals(value, "player_button")) {
                            int slot = (row - 1) * 9 + (col - 1);
                            playerSlots.add(new int[]{row, col, slot});
                        }
                    }
                }
            }
        }

        // Añadir jugadores en las posiciones de player_button
        int playerIndex = startIndex;
        for (int i = 0; i < playerSlots.size() && playerIndex < endIndex; i++) {
            int[] slotInfo = playerSlots.get(i);
            int slot = slotInfo[2];
            Player targetPlayer = onlinePlayers.get(playerIndex);
            User target = User.getInstance(targetPlayer);

            List<String> description = clans.getTranslationList(user, "clans.panels.clan_invite.buttons.player.description");
            description = description.stream()
                    .map(line -> line.replace("[player]", target.getName())
                            .replace("&", "§"))
                    .collect(Collectors.toList());

            panel.item(slot, new PanelItemBuilder()
                    .icon(new ItemStack(Material.PLAYER_HEAD))
                    .name(clans.getTranslation(user, "clans.panels.clan_invite.buttons.player.name", "[player]", target.getName()))
                    .description(description)
                    .clickHandler((clickedPanel, panelUser, clickType, slotNum) -> {
                        if (clickType.equals(ClickType.LEFT)) {
                            panelUser.getPlayer().playSound(panelUser.getPlayer().getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
                            sendInviteConfirmation(panelUser, target, clanName);
                            panelUser.closeInventory();
                            return true;
                        }
                        return false;
                    })
                    .build());
            playerIndex++;
        }

        // Añadir botones de paginación en la primera fila (columnas 2 y 8)
        if (startIndex > 0) {
            int finalPage = page;
            panel.item(1, new PanelItemBuilder()
                    .icon(new ItemStack(Material.ARROW))
                    .name(clans.getTranslation(user, "clans.panels.clan_invite.buttons.previous.name"))
                    .description(clans.getTranslationList(user, "clans.panels.clan_invite.buttons.previous.description"))
                    .clickHandler((clickedPanel, panelUser, clickType, slot) -> {
                        if (clickType.equals(ClickType.LEFT)) {
                            panelUser.performCommand("clan invite " + (finalPage - 1));
                            return true;
                        }
                        return false;
                    })
                    .build());
        }

        if (endIndex < playerCount) {
            int finalPage1 = page;
            panel.item(7, new PanelItemBuilder()
                    .icon(new ItemStack(Material.ARROW))
                    .name(clans.getTranslation(user, "clans.panels.clan_invite.buttons.next.name"))
                    .description(clans.getTranslationList(user, "clans.panels.clan_invite.buttons.next.description"))
                    .clickHandler((clickedPanel, panelUser, clickType, slot) -> {
                        if (clickType.equals(ClickType.LEFT)) {
                            panelUser.performCommand("clan invite " + (finalPage1 + 1));
                            return true;
                        }
                        return false;
                    })
                    .build());
        }

        // Añadir botón de regresar en la última fila (columna 9)
        panel.item(slots - 1, new PanelItemBuilder()
                .icon(new ItemStack(Material.OAK_DOOR))
                .name(clans.getTranslation(user, "clans.panels.clan_invite.buttons.back.name"))
                .description(clans.getTranslationList(user, "clans.panels.clan_invite.buttons.back.description"))
                .clickHandler((clickedPanel, panelUser, clickType, slot) -> {
                    if (clickType.equals(ClickType.LEFT)) {
                        panelUser.getPlayer().playSound(panelUser.getPlayer().getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
                        panelUser.closeInventory();
                        return true;
                    }
                    return false;
                })
                .build());

        panel.build();
        return true;
    }

    private void sendInviteConfirmation(User inviter, User target, String clanName) {
        UUID inviterId = inviter.getUniqueId();
        UUID targetId = target.getUniqueId();

        inviter.getPlayer().playSound(inviter.getPlayer().getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.5f);
        target.getPlayer().playSound(target.getPlayer().getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.5f);

        YamlConfiguration locale = clans.getLocaleConfig(inviter);
        String inviterMessageTemplate = locale.getString("clans.commands.clan.invite.inviter-message",
                "&e&lInvitaste a [player] al clan:\n&eClan: &6[name]\n&eEsperando respuesta...");
        String targetMessageTemplate = locale.getString("clans.commands.clan.invite.target-message",
                "&e&l¡Fuiste invitado al clan [name]!\n&ePor: &6[inviter]\n&eHaz clic para aceptar o rechazar:");
        String acceptButton = locale.getString("clans.commands.clan.invite.accept-button", "[Aceptar]");
        String acceptTooltip = locale.getString("clans.commands.clan.invite.accept-tooltip", "Unirte al clan [name]");
        String rejectButton = locale.getString("clans.commands.clan.invite.reject-button", "[Rechazar]");
        String rejectTooltip = locale.getString("clans.commands.clan.invite.reject-tooltip", "Rechazar la invitación");

        String inviterMessageText = inviterMessageTemplate
                .replace("[player]", target.getName())
                .replace("[name]", clanName);
        String targetMessageTemplateText = targetMessageTemplate
                .replace("[name]", clanName)
                .replace("[inviter]", inviter.getName());
        acceptTooltip = acceptTooltip.replace("[name]", clanName);
        rejectTooltip = rejectTooltip.replace("[name]", clanName);

        Component inviterMessage = Component.empty();
        for (String line : inviterMessageText.split("\n")) {
            inviterMessage = inviterMessage.append(LegacyComponentSerializer.legacyAmpersand().deserialize(line)).append(Component.newline());
        }

        Component targetMessage = Component.empty();
        for (String line : targetMessageTemplateText.split("\n")) {
            targetMessage = targetMessage.append(LegacyComponentSerializer.legacyAmpersand().deserialize(line)).append(Component.newline());
        }
        targetMessage = targetMessage
                .append(Component.text(acceptButton + " ", NamedTextColor.GREEN, TextDecoration.BOLD)
                        .clickEvent(ClickEvent.runCommand("/clan invite accept " + clanName))
                        .hoverEvent(HoverEvent.showText(LegacyComponentSerializer.legacyAmpersand().deserialize(acceptTooltip))))
                .append(Component.text(rejectButton + " ", NamedTextColor.RED, TextDecoration.BOLD)
                        .clickEvent(ClickEvent.runCommand("/clan invite decline " + clanName))
                        .hoverEvent(HoverEvent.showText(LegacyComponentSerializer.legacyAmpersand().deserialize(rejectTooltip))))
                .append(Component.newline());

        inviter.getPlayer().sendMessage(inviterMessage);
        target.getPlayer().sendMessage(targetMessage);

        List<InviteRequest> targetInvites = inviteRequests.computeIfAbsent(targetId, k -> new ArrayList<>());
        targetInvites.add(new InviteRequest(clanName, inviterId, System.currentTimeMillis()));
    }

    public Map<UUID, List<InviteRequest>> getInviteRequests() {
        return inviteRequests;
    }

    private Optional<List<String>> getStrings(User user, List<String> args) {
        if (args.size() == 1) {
            List<String> suggestions = getInviteRequests()
                    .getOrDefault(user.getUniqueId(), new ArrayList<>())
                    .stream()
                    .map(req -> req.clanName)
                    .toList();
            String input = args.getFirst().toLowerCase();
            return Optional.of(suggestions.stream()
                    .filter(s -> s.toLowerCase().startsWith(input))
                    .collect(Collectors.toList()));
        }
        return Optional.of(List.of());
    }

    @Override
    public Optional<List<String>> tabComplete(User user, String alias, List<String> args) {
        if (user == null || !user.hasPermission("clans.invite")) {
            return Optional.of(List.of());
        }

        if (args.size() == 1) {
            List<String> suggestions = new ArrayList<>();
            String playerUUID = user.getUniqueId().toString();
            Optional<ClanManager.Clan> clanOpt = clans.getClanManager().getClanByPlayer(playerUUID);
            if (clanOpt.isPresent()) {
                Integer rankValue = clanOpt.get().getRanks().get(playerUUID);
                if (rankValue != null &&
                        (rankValue == ClanManager.Clan.Rank.LEADER.getValue() ||
                                rankValue == ClanManager.Clan.Rank.CO_LEADER.getValue() ||
                                rankValue == ClanManager.Clan.Rank.COMMANDER.getValue())) {
                    suggestions.addAll(Bukkit.getOnlinePlayers().stream()
                            .filter(p -> !p.getUniqueId().toString().equals(playerUUID))
                            .filter(p -> clans.getClanManager().getClanNameByPlayer(p.getUniqueId().toString()) == null)
                            .map(Player::getName)
                            .toList());
                }
            }
            String input = args.getFirst().toLowerCase();
            return Optional.of(suggestions.stream()
                    .filter(s -> s.toLowerCase().startsWith(input))
                    .collect(Collectors.toList()));
        }

        return Optional.of(List.of());
    }

    public static class ClanInviteAcceptCommand extends CompositeCommand {
        private final Clans clans;

        public ClanInviteAcceptCommand(Clans addon, CompositeCommand parent) {
            super(addon, parent, "accept");
            this.clans = addon;
            if (addon != null) {
                Bukkit.getScheduler().runTask(addon.getPlugin(), this::configure);
            } else {
                getLogger().warning("Advertencia: addon es null en ClanInviteAcceptCommand. La configuración no se ejecutará.");
            }
        }

        @Override
        public void setup() {
            setPermission("clans.invite.accept");
            setDescription("Accept a clan invitation");
            setUsage("<clan>");
        }

        private void configure() {
            if (clans != null) {
                setDescription(clans.getTranslation(null, "clans.commands.clan.invite.accept.description"));
            }
        }

        @Override
        public boolean execute(User user, String label, List<String> args) {
            if (args.isEmpty()) {
                user.sendMessage(clans.getTranslation(user, "clans.commands.clan.invite.accept.usage", "[label]", "/" + getTopLabel()));
                return false;
            }

            String clanName = args.getFirst();
            return handleAccept(user, clanName);
        }

        private boolean handleAccept(User user, String clanName) {
            UUID userId = user.getUniqueId();
            ClanInviteCommand parentCommand = (ClanInviteCommand) getParent();
            Map<UUID, List<InviteRequest>> inviteRequests = parentCommand.getInviteRequests();
            List<InviteRequest> invites = inviteRequests.getOrDefault(userId, new ArrayList<>());
            InviteRequest request = invites.stream()
                    .filter(req -> req.clanName.equalsIgnoreCase(clanName))
                    .findFirst()
                    .orElse(null);

            if (request == null) {
                user.sendMessage(clans.getTranslation(user, "clans.commands.clan.invite.no-pending"));
                return false;
            }

            long inviteTimeout = 120_000;
            if (System.currentTimeMillis() - request.timestamp > inviteTimeout) {
                invites.remove(request);
                if (invites.isEmpty()) inviteRequests.remove(userId);
                user.sendMessage(clans.getTranslation(user, "clans.commands.clan.invite.timeout"));
                return false;
            }

            invites.remove(request);
            if (invites.isEmpty()) inviteRequests.remove(userId);

            Optional<ClanManager.Clan> clanOpt = clans.getClanManager().getClanByName(clanName);
            if (clanOpt.isEmpty()) {
                user.sendMessage(clans.getTranslation(user, "clans.errors.clan-not-found", "[name]", clanName));
                return false;
            }

            ClanManager.Clan clan = clanOpt.get();
            String playerUUID = user.getUniqueId().toString();
            if (clans.getClanManager().getClanNameByPlayer(playerUUID) != null) {
                user.sendMessage(clans.getTranslation(user, "clans.errors.already-in-clan"));
                return false;
            }

            clans.getClanManager().addMember(clan.getUniqueId(), playerUUID, ClanManager.Clan.Rank.MEMBER)
                    .thenAccept(success -> {
                        if (success) {
                            user.getPlayer().playSound(user.getPlayer().getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.0f);
                            user.sendMessage(clans.getTranslation(user, "clans.commands.clan.invite.accept-success", "[name]", clanName));
                            clan.getRanks().keySet().stream()
                                    .map(uuid -> User.getInstance(UUID.fromString(uuid)))
                                    .filter(u -> !u.getUniqueId().equals(userId))
                                    .forEach(member -> member.sendMessage(clans.getTranslation(member, "clans.commands.clan.invite.clan-notify",
                                            "[player]", user.getName(), "[name]", clanName)));
                        } else {
                            user.sendMessage(clans.getTranslation(user, "clans.commands.clan.invite.error"));
                        }
                    });

            return true;
        }

        @Override
        public Optional<List<String>> tabComplete(User user, String alias, List<String> args) {
            if (user == null || !user.hasPermission("clans.invite.accept")) {
                return Optional.of(List.of());
            }

            return ((ClanInviteCommand) getParent()).getStrings(user, args);
        }
    }

    public static class ClanInviteDeclineCommand extends CompositeCommand {
        private final Clans clans;

        public ClanInviteDeclineCommand(Clans addon, CompositeCommand parent) {
            super(addon, parent, "decline");
            this.clans = addon;
            if (addon != null) {
                Bukkit.getScheduler().runTask(addon.getPlugin(), this::configure);
            } else {
                getLogger().warning("Advertencia: addon es null en ClanInviteDeclineCommand. La configuración no se ejecutará.");
            }
        }

        @Override
        public void setup() {
            setPermission("clans.invite.decline");
            setDescription("Decline a clan invitation");
            setUsage("<clan>");
        }

        private void configure() {
            if (clans != null) {
                setDescription(clans.getTranslation(null, "clans.commands.clan.invite.decline.description"));
            }
        }

        @Override
        public boolean execute(User user, String label, List<String> args) {
            if (args.isEmpty()) {
                user.sendMessage(clans.getTranslation(user, "clans.commands.clan.invite.decline.usage", "[label]", "/" + getTopLabel()));
                return false;
            }

            String clanName = args.getFirst();
            return handleDecline(user, clanName);
        }

        private boolean handleDecline(User user, String clanName) {
            UUID userId = user.getUniqueId();
            ClanInviteCommand parentCommand = (ClanInviteCommand) getParent();
            Map<UUID, List<InviteRequest>> inviteRequests = parentCommand.getInviteRequests();
            List<InviteRequest> invites = inviteRequests.getOrDefault(userId, new ArrayList<>());
            InviteRequest request = invites.stream()
                    .filter(req -> req.clanName.equalsIgnoreCase(clanName))
                    .findFirst()
                    .orElse(null);

            if (request == null) {
                user.sendMessage(clans.getTranslation(user, "clans.commands.clan.invite.no-pending"));
                return false;
            }

            long inviteTimeout = 120_000;
            if (System.currentTimeMillis() - request.timestamp > inviteTimeout) {
                invites.remove(request);
                if (invites.isEmpty()) inviteRequests.remove(userId);
                user.sendMessage(clans.getTranslation(user, "clans.commands.clan.invite.timeout"));
                return false;
            }

            invites.remove(request);
            if (invites.isEmpty()) inviteRequests.remove(userId);

            user.getPlayer().playSound(user.getPlayer().getLocation(), Sound.ITEM_SHIELD_BREAK, 0.5f, 1.0f);
            user.sendMessage(clans.getTranslation(user, "clans.commands.clan.invite.decline-success", "[name]", clanName));
            User inviter = User.getInstance(request.inviterId);
            inviter.sendMessage(clans.getTranslation(inviter, "clans.commands.clan.invite.decline-notify",
                    "[player]", user.getName(), "[name]", clanName));

            return true;
        }

        @Override
        public Optional<List<String>> tabComplete(User user, String alias, List<String> args) {
            if (user == null || !user.hasPermission("clans.invite.decline")) {
                return Optional.of(List.of());
            }

            return ((ClanInviteCommand) getParent()).getStrings(user, args);
        }
    }
}