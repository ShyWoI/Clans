package world.bentobox.clans;

import world.bentobox.bentobox.api.configuration.ConfigComment;
import world.bentobox.bentobox.api.configuration.ConfigEntry;
import world.bentobox.bentobox.api.configuration.ConfigObject;
import world.bentobox.bentobox.api.configuration.StoreAt;

import java.util.HashMap;
import java.util.Map;

@StoreAt(filename="config.yml", path="addons/Clans")
public class Settings implements ConfigObject {

    @ConfigComment("Clans Configuration [1.0.0]")
    @ConfigComment("Addon creado por ShyWol y AI Grok")
    @ConfigComment("")
    @ConfigComment("No cambiar!")
    @ConfigEntry(path = "uniqueId")
    private String uniqueId = "clans_config";

    @ConfigComment("")
    @ConfigComment("Habilita o no el sistema de clanes.")
    @ConfigEntry(path = "enabled")
    private boolean enabled = true;

    @ConfigComment("")
    @ConfigComment("Lenguaje por defecto que debe usar el plugin")
    @ConfigEntry(path = "locale")
    private String locale = "es-ES";

    @ConfigComment("Si el jugador tiene un lenguaje que no existe dentro de BentoBox/locales/Clans, el plugin debe usar el lenguaje:")
    @ConfigEntry(path = "default-language")
    private String defaultLanguage = "es-ES";

    @ConfigComment("")
    @ConfigComment("Mínimo de carácteres que debe contener el nombre de un clan")
    @ConfigEntry(path = "minNameLength")
    private int minNameLength = 3;

    @ConfigComment("Máximo de carácteres que debe contener el nombre de un clan")
    @ConfigEntry(path = "maxNameLength")
    private int maxNameLength = 20;

    @ConfigComment("")
    @ConfigComment("Co-Lideres máximos que deben haber por clan")
    @ConfigEntry(path = "maxCoLeaders")
    private int maxCoLeaders = 2;

    @ConfigComment("Comandantes máximos por clan")
    @ConfigEntry(path = "maxCommanders")
    private int maxCommanders = 5;

    @ConfigComment("Miembros máximos inicialmente por clan")
    @ConfigEntry(path = "maxMembers")
    private int maxMembers = 10;

    @ConfigComment("")
    @ConfigComment("Vault Integración")
    @ConfigComment("Costo para crear un clan")
    @ConfigEntry(path = "creationCost")
    private int creationCost = 1000;

    @ConfigComment("Reembolso al disolver un clan")
    @ConfigEntry(path = "disbandRefunded")
    private int disbandRefunded = 500;

    @ConfigComment("")
    @ConfigComment("Tiempo de penitencia tras abandonar un clan (en segundos)")
    @ConfigComment("Establece enabled a false o time-seconds a 0 para desactivar")
    @ConfigEntry(path = "penitence.enabled")
    private boolean penitenceEnabled = true;

    @ConfigEntry(path = "penitence.time-seconds")
    private int penitenceTimeSeconds = 3600; // 1 hora

    @ConfigComment("")
    @ConfigComment("Nombres de los rangos, editables para personalizar su visualización")
    @ConfigEntry(path = "ranks")
    private Map<String, String> ranks = new HashMap<>();

    @ConfigComment("")
    @ConfigComment("Tiempo de espera para cambiar el tag del clan (en minutos)")
    @ConfigComment("Establece a 0 para desactivar el tiempo de espera")
    @ConfigEntry(path = "tag-change-cooldown")
    private long tagChangeCooldown = 1440; // 24 horas en minutos

    public Settings() {
        // Valores por defecto para los rangos
        ranks.put("leader", "Líder");
        ranks.put("co_leaders", "Co-líder");
        ranks.put("commanders", "Comandante");
        ranks.put("members", "Miembro");
    }

    public String getUniqueId() {
        return uniqueId;
    }

    public void setUniqueId(String uniqueId) {
        this.uniqueId = uniqueId;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getLocale() {
        return locale;
    }

    public void setLocale(String locale) {
        this.locale = locale;
    }

    public String getDefaultLanguage() {
        return defaultLanguage;
    }

    public void setDefaultLanguage(String defaultLanguage) {
        this.defaultLanguage = defaultLanguage;
    }

    public int getMinNameLength() {
        return minNameLength;
    }

    public void setMinNameLength(int minNameLength) {
        this.minNameLength = minNameLength;
    }

    public int getMaxNameLength() {
        return maxNameLength;
    }

    public void setMaxNameLength(int maxNameLength) {
        this.maxNameLength = maxNameLength;
    }

    public int getMaxCoLeaders() {
        return maxCoLeaders;
    }

    public void setMaxCoLeaders(int maxCoLeaders) {
        this.maxCoLeaders = maxCoLeaders;
    }

    public int getMaxCommanders() {
        return maxCommanders;
    }

    public void setMaxCommanders(int maxCommanders) {
        this.maxCommanders = maxCommanders;
    }

    public int getMaxMembers() {
        return maxMembers;
    }

    public void setMaxMembers(int maxMembers) {
        this.maxMembers = maxMembers;
    }

    public int getCreationCost() {
        return creationCost;
    }

    public void setCreationCost(int creationCost) {
        this.creationCost = creationCost;
    }

    public int getDisbandRefunded() {
        return disbandRefunded;
    }

    public void setDisbandRefunded(int disbandRefunded) {
        this.disbandRefunded = disbandRefunded;
    }

    public boolean isPenitenceEnabled() {
        return penitenceEnabled;
    }

    public void setPenitenceEnabled(boolean penitenceEnabled) {
        this.penitenceEnabled = penitenceEnabled;
    }

    public int getPenitenceTimeSeconds() {
        return penitenceTimeSeconds;
    }

    public void setPenitenceTimeSeconds(int penitenceTimeSeconds) {
        this.penitenceTimeSeconds = penitenceTimeSeconds;
    }

    public Map<String, String> getRanks() {
        return ranks;
    }

    public void setRanks(Map<String, String> ranks) {
        this.ranks = ranks;
    }

    public long getTagChangeCooldown() {
        return tagChangeCooldown;
    }

    public void setTagChangeCooldown(long tagChangeCooldown) {
        this.tagChangeCooldown = tagChangeCooldown;
    }
}