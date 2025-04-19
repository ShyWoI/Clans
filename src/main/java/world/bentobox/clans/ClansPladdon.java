package world.bentobox.clans;

import world.bentobox.bentobox.api.addons.Addon;
import world.bentobox.bentobox.api.addons.Pladdon;

public class ClansPladdon extends Pladdon {
    private Addon addon;

    @Override
    public Addon getAddon() {
        if (addon == null) {
            addon = new Clans();
        }
        return addon;
    }
}