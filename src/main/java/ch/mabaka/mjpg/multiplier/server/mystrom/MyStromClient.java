package ch.mabaka.mjpg.multiplier.server.mystrom;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Simple wrapper for myStrom switch REST API used to read relay state and send power-cycle.
 * Polls the device every minute and logs state.
 */
@Component
public class MyStromClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(MyStromClient.class);

    private final HttpClient httpClient;
    private final URI baseUri;

    private volatile boolean lastRelayState = false;
    private volatile Instant lastPowerCycle = Instant.EPOCH;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String statePath;
    private final String actionPath;
    private final String setRelayPath;

    public MyStromClient(
            @Value("${mystrom.baseUrl:http://192.168.5.134}") String baseUrl,
            @Value("${mystrom.statePath:/report}") String statePath,
            @Value("${mystrom.actionPath:/power_cycle?time=10}") String actionPath,
            @Value("${mystrom.setRelayPath:/relay}") String setRelayPath) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.baseUri = URI.create(baseUrl);
        this.statePath = statePath;
        this.actionPath = actionPath;
        this.setRelayPath = setRelayPath;
        LOGGER.info("MyStromClient configured for {}", baseUrl);
    }

    // Poll every minute
    @Scheduled(fixedDelayString = "PT1M")
    public void pollRelayState() {
        try {
            boolean relay = getRelayStateInternal();
            if (relay != lastRelayState) {
                LOGGER.info("myStrom relay state changed: {} -> {}", lastRelayState, relay);
                lastRelayState = relay;
            } else {
                LOGGER.debug("myStrom relay state: {}", relay);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to poll myStrom relay state: {}", e.toString(), e);
        }
    }

    public boolean getRelayState() {
        try {
            boolean relay = getRelayStateInternal();
            lastRelayState = relay;
            return relay;
        } catch (Exception e) {
            LOGGER.error("getRelayState failed: {}", e.toString(), e);
            return false;
        }
    }

    private boolean getRelayStateInternal() throws Exception {
        URI uri = baseUri.resolve(statePath);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();
        LOGGER.debug("Requesting myStrom state from {}", uri);
        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        LOGGER.debug("myStrom state response status: {}", resp.statusCode());
        if (resp.statusCode() != 200) {
            throw new Exception("myStrom API returned " + resp.statusCode());
        }
        String body = resp.body();
        LOGGER.debug("myStrom state response body: {}", body);
        // First, try to map to a simple /report object: { "relay": true }
        try {
            MyStromReport report = objectMapper.readValue(body, MyStromReport.class);
            LOGGER.debug("Parsed MyStromReport with relay={}", report.isRelay());
            return report.isRelay();
        } catch (Exception e) {
            LOGGER.debug("Failed to parse myStrom JSON response into MyStromReport: {}", e.toString());
            throw e;
        }
    }

    public synchronized boolean tryPowerCycleIfAllowed() {
        Instant now = Instant.now();
        if (Duration.between(lastPowerCycle, now).toMinutes() < 5) {
            LOGGER.info("PowerCycle suppressed; last cycle at {} (less than 5 minutes ago)", lastPowerCycle);
            LOGGER.debug("Suppressing powerCycle request; cooldown still active.");
            return false;
        }
        try {
            powerCycle();
            lastPowerCycle = now;
            LOGGER.info("PowerCycle executed at {}", lastPowerCycle);
            LOGGER.debug("PowerCycle flow completed; lastPowerCycle updated.");
            return true;
        } catch (Exception e) {
            LOGGER.error("Failed to powerCycle myStrom device: {}", e.toString(), e);
            return false;
        }
    }

    private void powerCycle() throws Exception {
        // PowerCycle is only possible when relay state == 1.
        // If relay==true -> execute power_cycle. If relay==false -> try to set relay to true and do NOT call power_cycle.
        boolean current = getRelayState();
        if (!current) {
            LOGGER.info("Relay is false; attempting to set relay to true via {}?state=1. Will NOT call power_cycle afterwards.", setRelayPath);
            try {
                setRelayState(true);
                LOGGER.debug("Successfully set relay to true; not invoking power_cycle as it's not necessary.");
            } catch (Exception e) {
                LOGGER.error("Failed to set relay to true; power_cycle would not help. Error: {}", e.toString(), e);
            }
            return;
        }

        // relay is true -> we can request a power_cycle
        LOGGER.info("Relay is true; requesting power_cycle via {}", actionPath);
        URI uri = baseUri.resolve(actionPath);
        LOGGER.debug("Sending power_cycle request to {}", uri);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        LOGGER.debug("power_cycle response status: {}, body: {}", resp.statusCode(), resp.body());
        if (resp.statusCode() / 100 != 2) {
            throw new Exception("Failed to power cycle myStrom switch: " + resp.statusCode());
        }
        LOGGER.info("Requested power cycle via {} (response {})", uri, resp.statusCode());
    }

    private void setRelayState(boolean on) throws Exception {
        String path = setRelayPath + "?state=" + (on ? "1" : "0");
        URI uri = baseUri.resolve(path);
        LOGGER.debug("Setting relay state via {}", uri);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        LOGGER.debug("setRelayState response status: {}, body: {}", resp.statusCode(), resp.body());
        if (resp.statusCode() / 100 != 2) {
            throw new Exception("Failed to set relay state on myStrom switch: " + resp.statusCode());
        }
        LOGGER.info("Set relay state to {} (response {})", on, resp.statusCode());
    }
}
