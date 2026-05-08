package ch.mabaka.mjpg.multiplier.server.mystrom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

class MyStromClientTest {

    @Test
    void fallsBackToManualRelayCycleWhenPowerCycleEndpointIsMissing() throws Exception {
        List<String> requests = new ArrayList<>();
        try (TestServer server = new TestServer(requests, true, 404)) {
            MyStromClient client = new MyStromClient(
                    HttpClient.newHttpClient(),
                    server.baseUri(),
                    "/report",
                    "/power_cycle?time=10",
                    "/relay",
                    Duration.ZERO);

            assertTrue(client.tryPowerCycleIfAllowed());
        }

        assertEquals(List.of(
                "/report",
                "/power_cycle?time=10",
                "/relay?state=0",
                "/relay?state=1"), requests);
    }

    @Test
    void usesNativePowerCycleEndpointWhenAvailable() throws Exception {
        List<String> requests = new ArrayList<>();
        try (TestServer server = new TestServer(requests, true, 200)) {
            MyStromClient client = new MyStromClient(
                    HttpClient.newHttpClient(),
                    server.baseUri(),
                    "/report",
                    "/power_cycle?time=10",
                    "/relay",
                    Duration.ZERO);

            assertTrue(client.tryPowerCycleIfAllowed());
        }

        assertEquals(List.of(
                "/report",
                "/power_cycle?time=10"), requests);
    }

    private static final class TestServer implements AutoCloseable {
        private final HttpServer server;

        private TestServer(List<String> requests, boolean relayState, int powerCycleStatusCode) throws IOException {
            server = HttpServer.create(new InetSocketAddress(0), 0);
            server.createContext("/", exchange -> handle(exchange, requests, relayState, powerCycleStatusCode));
            server.start();
        }

        private URI baseUri() {
            return URI.create("http://127.0.0.1:" + server.getAddress().getPort());
        }

        @Override
        public void close() {
            server.stop(0);
        }

        private static void handle(
                HttpExchange exchange,
                List<String> requests,
                boolean relayState,
                int powerCycleStatusCode) throws IOException {
            String requestTarget = exchange.getRequestURI().toString();
            requests.add(requestTarget);

            int statusCode;
            String body;
            switch (requestTarget) {
                case "/report":
                    statusCode = 200;
                    body = "{\"relay\":" + relayState + "}";
                    break;
                case "/power_cycle?time=10":
                    statusCode = powerCycleStatusCode;
                    body = "{}";
                    break;
                case "/relay?state=0":
                case "/relay?state=1":
                    statusCode = 200;
                    body = "{}";
                    break;
                default:
                    statusCode = 404;
                    body = "{}";
                    break;
            }

            byte[] response = body.getBytes();
            exchange.sendResponseHeaders(statusCode, response.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(response);
            } finally {
                exchange.close();
            }
        }
    }
}
