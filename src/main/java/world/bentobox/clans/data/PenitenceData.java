package world.bentobox.clans.data;

import com.google.gson.annotations.Expose;
import world.bentobox.bentobox.database.objects.DataObject;
import world.bentobox.bentobox.database.objects.Table;

@Table(name = "PenitenceData")
public class PenitenceData implements DataObject {

    @Expose
    private String uniqueId; // UUID del jugador

    @Expose
    private long expirationTime; // Tiempo de expiración en milisegundos

    public PenitenceData() {
    }

    public PenitenceData(String uniqueId, long expirationTime) {
        this.uniqueId = uniqueId;
        this.expirationTime = expirationTime;
    }

    @Override
    public String getUniqueId() {
        return uniqueId;
    }

    @Override
    public void setUniqueId(String uniqueId) {
        this.uniqueId = uniqueId;
    }

    public long getExpirationTime() {
        return expirationTime;
    }

    public void setExpirationTime(long expirationTime) {
        this.expirationTime = expirationTime;
    }
}