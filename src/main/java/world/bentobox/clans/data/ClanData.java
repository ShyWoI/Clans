package world.bentobox.clans.data;

import com.google.gson.annotations.Expose;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import world.bentobox.bentobox.database.objects.DataObject;
import world.bentobox.bentobox.database.objects.Table;

@Table(name = "ClanData")
public class ClanData implements DataObject {
    @Expose
    private String uniqueId;
    @Expose
    private String cleanName;
    @Expose
    private String displayName;
    @Expose
    private String tag;
    @Expose
    private String ownerUUID;
    @Expose
    private Map<String, Integer> ranks;
    @Expose
    private int maxCoLeaders;
    @Expose
    private int maxCommanders;
    @Expose
    private int maxMembers;
    @Expose
    private List<String> bannedPlayers;
    @Expose
    private long lastTagChangeTimestamp;


    // Constructor por defecto requerido por BentoBox/Gson
    public ClanData() {;
    }

    public ClanData(String uniqueId, String cleanName, String displayName, String tag, String ownerUUID,
                    Map<String, Integer> ranks, int maxCoLeaders, int maxCommanders, int maxMembers,
                    List<String> bannedPlayers, long lastTagChangeTimestamp) {
        this.uniqueId = uniqueId;
        this.cleanName = cleanName;
        this.displayName = displayName;
        this.tag = tag;
        this.ownerUUID = ownerUUID;
        this.ranks = ranks != null ? new HashMap<>(ranks) : new HashMap<>();
        this.maxCoLeaders = maxCoLeaders;
        this.maxCommanders = maxCommanders;
        this.maxMembers = maxMembers;
        this.bannedPlayers = bannedPlayers != null ? new ArrayList<>(bannedPlayers) : new ArrayList<>();
        this.lastTagChangeTimestamp = lastTagChangeTimestamp;
    }

    @Override
    public String getUniqueId() {
        return uniqueId;
    }

    @Override
    public void setUniqueId(String uniqueId) {
        this.uniqueId = uniqueId;
    }

    public String getCleanName() {
        return cleanName;
    }

    public void setCleanName(String cleanName) {
        this.cleanName = cleanName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    public String getOwnerUUID() {
        return ownerUUID;
    }

    public void setOwnerUUID(String ownerUUID) {
        this.ownerUUID = ownerUUID;
    }

    public Map<String, Integer> getRanks() {
        return ranks != null ? new HashMap<>(ranks) : new HashMap<>();
    }

    public void setRanks(Map<String, Integer> ranks) {
        this.ranks = ranks != null ? new HashMap<>(ranks) : new HashMap<>();
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

    public List<String> getBannedPlayers() {
        return bannedPlayers != null ? new ArrayList<>(bannedPlayers) : new ArrayList<>();
    }

    public void setBannedPlayers(List<String> bannedPlayers) {
        this.bannedPlayers = bannedPlayers != null ? new ArrayList<>(bannedPlayers) : new ArrayList<>();
    }

    public long getLastTagChangeTimestamp() {
        return lastTagChangeTimestamp;
    }

    public void setLastTagChangeTimestamp(long lastTagChangeTimestamp) {
        this.lastTagChangeTimestamp = lastTagChangeTimestamp;
    }

    @Override
    public String toString() {
        return "ClanData{" +
                "uniqueId='" + uniqueId + '\'' +
                ", cleanName='" + cleanName + '\'' +
                ", displayName='" + displayName + '\'' +
                ", tag='" + tag + '\'' +
                ", ownerUUID='" + ownerUUID + '\'' +
                ", ranks=" + ranks +
                ", maxCoLeaders=" + maxCoLeaders +
                ", maxCommanders=" + maxCommanders +
                ", maxMembers=" + maxMembers +
                ", bannedPlayers=" + bannedPlayers +
                ", lastTagChangeTimestamp=" + lastTagChangeTimestamp +
                '}';
    }
}