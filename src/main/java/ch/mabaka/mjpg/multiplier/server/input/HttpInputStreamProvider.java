package ch.mabaka.mjpg.multiplier.server.input;

import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

@Component
public class HttpInputStreamProvider implements IInputStreamProvider {

    private final String url;

    public HttpInputStreamProvider() {
        this.url = "http://birdbox:7123/stream.mjpg";
    	//this.url = "http://77.222.181.11:8080/mjpg/video.mjpg";
    }

    @Override
    public InputStream getInputStream() throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);

        // Check response code
        int responseCode = connection.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
            throw new Exception("Failed to connect. HTTP response code: " + responseCode);
        }

        return connection.getInputStream();
    }
}
