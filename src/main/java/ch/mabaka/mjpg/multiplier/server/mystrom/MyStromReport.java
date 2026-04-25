package ch.mabaka.mjpg.multiplier.server.mystrom;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class MyStromReport {
    private boolean relay;

    public MyStromReport() {
    }

    public boolean isRelay() {
        return relay;
    }

    public void setRelay(boolean relay) {
        this.relay = relay;
    }
}
