package ch.mabaka.mjpg.multiplier.server.input;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class HttpInputStreamProvider implements IInputStreamProvider {

	private final String url;

	public HttpInputStreamProvider(@Value("${stream.url}") String url) {
		this.url = url;
	}

	@Override
	public InputStream getInputStream() throws Exception {
		final URI uri = new URI(url);
		final HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
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
