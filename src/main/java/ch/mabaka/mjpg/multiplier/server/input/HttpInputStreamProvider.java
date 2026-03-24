package ch.mabaka.mjpg.multiplier.server.input;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.InetAddress;
import java.net.Socket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import ch.mabaka.mjpg.multiplier.server.rest.StreamController;

@Component
public class HttpInputStreamProvider implements IInputStreamProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(StreamController.class);
    
    private final String url;

    public HttpInputStreamProvider(@Value("${stream.url}") String url) {
        this.url = url;
    }

    @Override
    public InputStream getInputStream() throws Exception {
        final URI uri = new URI(url);
        String host = uri.getHost();
        int port = uri.getPort();
        if (port == -1 && "https".equalsIgnoreCase(uri.getScheme())) {
			port = 443;
		} else if (port == -1) {
			port = 80;
        	
        }
        LOGGER.info("Resolving host: {}", host);
        try {
            InetAddress address = InetAddress.getByName(host);
            LOGGER.info("Host {} resolved to {}", host, address.getHostAddress());
            try (final Socket socket = new Socket()) {
                socket.connect(new java.net.InetSocketAddress(address, port), 5000);
                LOGGER.info("Successfully connected to {}:{} via raw socket.", host, port);
            }
        } catch (Exception e) {
            LOGGER.error("DNS or socket connection failed for {}:{} - {}", host, port, e.toString(), e);
            throw e;
        }

        final HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(5000); // 15 seconds
        connection.setReadTimeout(5000);    // 60 seconds
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (compatible; mjpg-multiplier/1.0)");
        // Force connection close and avoid caching/reuse of sockets
        connection.setRequestProperty("Connection", "close");
        connection.setUseCaches(false);
        connection.setDoInput(true);

        LOGGER.info("Connecting to stream URL: {}", url);

        try {
            // Check response code
            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                connection.disconnect();
                throw new Exception("Failed to connect. HTTP response code: " + responseCode);
            }
            LOGGER.info("Connected to stream. Response code: {}", responseCode);
            InputStream is = connection.getInputStream();
            return new DisconnectingInputStream(is, connection);
        } catch (Exception e) {
            LOGGER.error("Exception while connecting to stream URL: {} - {}", url, e.toString(), e);
            try {
                connection.disconnect();
            } catch (Exception ex) {
                // ignore
            }
            throw e;
        }
    }

    // Wrapper that disconnects the HttpURLConnection when the stream is closed
    private static class DisconnectingInputStream extends FilterInputStream {
        private final HttpURLConnection connection;
        private volatile boolean closed = false;

        protected DisconnectingInputStream(InputStream in, HttpURLConnection connection) {
            super(in);
            this.connection = connection;
        }

        @Override
        public int read() throws IOException {
            try {
                return super.read();
            } catch (IOException e) {
                disconnect();
                throw e;
            }
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            try {
                return super.read(b, off, len);
            } catch (IOException e) {
                disconnect();
                throw e;
            }
        }

        @Override
        public void close() throws IOException {
            if (closed) return;
            closed = true;
            try {
                super.close();
            } finally {
                disconnect();
            }
        }

        private void disconnect() {
            try {
                connection.disconnect();
            } catch (Exception ignored) {
            }
        }
    }
}