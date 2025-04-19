package world.bentobox.clans.commands;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.OfflinePlayer;
import world.bentobox.bentobox.api.commands.CompositeCommand;
import world.bentobox.bentobox.api.panels.builders.PanelBuilder;
import world.bentobox.bentobox.api.panels.builders.PanelItemBuilder;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.clans.Clans;
import world.bentobox.clans.managers.ClanManager;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

public class ClanListCommand extends CompositeCommand {
    private final Clans clans;

    public ClanListCommand(Clans addon, CompositeCommand parent) {
        super(addon, parent, "list");
        this.clans = addon;
        Bukkit.getScheduler().runTask(addon.getPlugin(), this::configure);
    }

    @Override
    public void setup() {
        setPermission("clans.list");
        setDescription("clans.commands.clan.list.description");
        setUsage("");
    }

    private void configure() {
        setDescription(clans.getTranslation(null, "clans.commands.clan.list.description"));
        setUsage("[page]");
    }

    @Override
    public boolean execute(User user, String label, List<String> args) {
        int page = 1;
        if (!args.isEmpty()) {
            try {
                page = Integer.parseInt(args.getFirst());
                if (page < 1) {
                    user.sendMessage(clans.getTranslation(user, "clans.commands.clan.list.usage"));
                    return false;
                }
            } catch (NumberFormatException e) {
                user.sendMessage(clans.getTranslation(user, "clans.commands.clan.list.usage"));
                return false;
            }
        }

        File panelFile = new File(getAddon().getDataFolder(), "panels/clan_list.yml");
        if (!panelFile.exists()) {
            user.sendMessage(clans.getTranslation(user, "clans.commands.clan.list.error-panel"));
            return false;
        }

        YamlConfiguration panelConfig = YamlConfiguration.loadConfiguration(panelFile);
        List<ClanManager.Clan> clansList = new ArrayList<>(clans.getClanManager().getAllClans());
        int clanCount = clansList.size();

        // Calcular filas necesarias: mínimo 3, máximo 6
        int rowsNeeded = Math.min(6, Math.max(3, (int) Math.ceil((double) clanCount / 7) + 2));
        int itemsPerPage = 7 * (rowsNeeded - 2); // 7 clanes por fila, desde fila 2 hasta penúltima
        int startIndex = (page - 1) * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, clanCount);
        int slots = rowsNeeded * 9;

        PanelBuilder panel = new PanelBuilder()
                .name(clans.getTranslation(user, panelConfig.getString("title", "clans.panels.clan_list.title")))
                .user(user)
                .size(slots);

        // Añadir fondo y bordes
        String backgroundIcon = panelConfig.getString("background.icon", "BLACK_STAINED_GLASS_PANE");
        String backgroundTitle = panelConfig.getString("background.title", "&b&r");
        String borderIcon = panelConfig.getString("border.icon", "BLACK_STAINED_GLASS_PANE");
        String borderTitle = panelConfig.getString("border.title", "&b&r");
        for (int row = 1; row <= rowsNeeded; row++) {
            for (int col = 1; col <= 9; col++) {
                int slot = (row - 1) * 9 + (col - 1);
                if (row == 1 || row == rowsNeeded || col == 1 || col == 9) {
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

        // Leer posiciones de clan_button desde clan_list.yml (filas 2 hasta penúltima)
        List<int[]> clanSlots = new ArrayList<>();
        for (int row = 2; row < rowsNeeded; row++) {
            String rowKey = "clan_list.content." + row;
            if (panelConfig.contains(rowKey)) {
                ConfigurationSection rowSection = panelConfig.getConfigurationSection(rowKey);
                if (rowSection != null) {
                    for (String colKey : rowSection.getKeys(false)) {
                        int col = Integer.parseInt(colKey);
                        if (Objects.equals(rowSection.getString(colKey), "clan_button")) {
                            int slot = (row - 1) * 9 + (col - 1);
                            clanSlots.add(new int[]{row, col, slot});
                        }
                    }
                }
            }
        }

        // Añadir clanes en las posiciones de clan_button
        int clanIndex = startIndex;
        for (int i = 0; i < clanSlots.size() && clanIndex < endIndex; i++) {
            int[] slotInfo = clanSlots.get(i);
            int slot = slotInfo[2];
            ClanManager.Clan clan = clansList.get(clanIndex);
            OfflinePlayer leader = Bukkit.getOfflinePlayer(UUID.fromString(clan.getOwnerUUID()));
            String leaderName = leader.getName() != null ? leader.getName() : "Desconocido";

            List<String> description = clans.getTranslationList(user, "clans.panels.clan_list.buttons.clan.description");
            if (description.isEmpty()) {
                // Fallback si la traducción no se carga
                description = List.of(
                        "&7Información:",
                        "&eLíder: " + leaderName,
                        "&eMiembros: " + clan.getMemberCount() + "/" + clan.getMaxMembers(),
                        "&eClic izquierdo: Ver información",
                        "&eClic derecho: Ir a base (en guerra)"
                );
            }
            description = description.stream()
                    .map(line -> line.replace("[leader]", leaderName)
                            .replace("[count_members]", String.valueOf(clan.getMemberCount()))
                            .replace("[max_members]", String.valueOf(clan.getMaxMembers()))
                            .replace("&", "§"))
                    .collect(Collectors.toList());

            panel.item(slot, new PanelItemBuilder()
                    .icon(new ItemStack(Material.PLAYER_HEAD))
                    .name(clans.getTranslation(user, "clans.panels.clan_list.buttons.clan.name", "[name]", clan.getDisplayName()))
                    .description(description)
                    .clickHandler((clickedPanel, panelUser, clickType, slotNum) -> {
                        panelUser.getPlayer().playSound(panelUser.getPlayer().getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
                        if (clickType.equals(ClickType.LEFT)) {
                            // Clic izquierdo: Mostrar información
                            List<String> messages = clans.getTranslationList(user, "clans.commands.clan.list.clan-info");
                            for (String message : messages) {
                                String formattedMessage = message
                                        .replace("[name]", clan.getDisplayName())
                                        .replace("[tag]", clan.getTag())
                                        .replace("[leader]", leaderName)
                                        .replace("[count_members]", String.valueOf(clan.getMemberCount()))
                                        .replace("[max_members]", String.valueOf(clan.getMaxMembers()))
                                        .replace("&", "§");
                                panelUser.sendMessage(formattedMessage);
                            }
                        } else if (clickType.equals(ClickType.RIGHT)) {
                            // Clic derecho: Intentar ir a la base
                            if (clans.warEventActive) {
                                panelUser.performCommand("clan base " + clan.getDisplayName());
                            } else {
                                panelUser.sendMessage(clans.getTranslation(user, "clans.commands.clan.list.war-not-active"));
                            }
                        }
                        panelUser.closeInventory();
                        return true;
                    })
                    .build());
            clanIndex++;
        }

        // Añadir botones de paginación en la última fila (columnas 1 y 9)
        int lastRowSlotBase = (rowsNeeded - 1) * 9;
        if (startIndex > 0) {
            int finalPage = page;
            panel.item(lastRowSlotBase, new PanelItemBuilder()
                    .icon(new ItemStack(Material.TIPPED_ARROW))
                    .name(clans.getTranslation(user, "clans.panels.clan_list.buttons.previous.name"))
                    .description(clans.getTranslationList(user, "clans.panels.clan_list.buttons.previous.description"))
                    .clickHandler((clickedPanel, panelUser, clickType, slot) -> {
                        if (clickType.equals(ClickType.LEFT)) {
                            panelUser.performCommand("clan list " + (finalPage - 1));
                            return true;
                        }
                        return false;
                    })
                    .build());
        }

        if (endIndex < clansList.size()) {
            int finalPage1 = page;
            panel.item(lastRowSlotBase + 8, new PanelItemBuilder()
                    .icon(new ItemStack(Material.TIPPED_ARROW))
                    .name(clans.getTranslation(user, "clans.panels.clan_list.buttons.next.name"))
                    .description(clans.getTranslationList(user, "clans.panels.clan_list.buttons.next.description"))
                    .clickHandler((clickedPanel, panelUser, clickType, slot) -> {
                        if (clickType.equals(ClickType.LEFT)) {
                            panelUser.performCommand("clan list " + (finalPage1 + 1));
                            return true;
                        }
                        return false;
                    })
                    .build());
        }

        panel.build();
        return true;
    }
}