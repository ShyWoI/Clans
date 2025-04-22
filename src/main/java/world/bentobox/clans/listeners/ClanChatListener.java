package world.bentobox.clans.listeners;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.clans.Clans;
import world.bentobox.clans.managers.ClanManager;

import java.util.UUID;

public class ClanChatListener implements Listener {

    private final Clans clans;

    public ClanChatListener(Clans clans) {
        this.clans = clans;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        User user = User.getInstance(event.getPlayer());
        UUID userId = user.getUniqueId();
        String message = event.getMessage();

        // Verificar si el mensaje comienza con el prefijo "-"
        if (!message.startsWith("-")) {
            return;
        }

        // Obtener el clan del jugador
        ClanManager.Clan clan = clans.getClanManager().getClanByPlayer(userId.toString()).orElse(null);
        if (clan == null) {
            user.sendMessage(clans.getTranslation(user, "clans.errors.not-in-clan"));
            return;
        }

        // Cancelar el evento para evitar que se envíe al chat público
        event.setCancelled(true);

        // Eliminar el prefijo "-" y enviar el mensaje al clan
        String clanMessage = message.substring(1).trim();
        if (clanMessage.isEmpty()) {
            return; // Ignorar mensajes vacíos después de quitar el prefijo
        }
        Component messageComponent = LegacyComponentSerializer.legacyAmpersand().deserialize(clanMessage);
        clans.sendClanMessage(user, clan, messageComponent);
    }
}