package ch.mabaka.mjpg.multiplier.server.input;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import ch.mabaka.mjpg.multiplier.server.rest.StreamController;
import jakarta.annotation.PostConstruct;

@Component
public class MjpegInputStreamReaderComponent {

	private static final int DATA_BUFFER_SIZE = 1024 * 1024;
	
	@Autowired
	private ImageQueueHolderComponent imageQueueHolder;

	@Autowired
	private HttpInputStreamProvider httpInputStreamProvider;

	private ByteArrayOutputStream baos = new ByteArrayOutputStream(DATA_BUFFER_SIZE);

	private final ExecutorService executorService = Executors.newSingleThreadExecutor();

	private volatile boolean keepReading = true;

	private static final Logger LOGGER = LoggerFactory.getLogger(StreamController.class);

	@PostConstruct
	public void init() {
		startReading(httpInputStreamProvider);
	}

	public void startReading(final IInputStreamProvider inputStreamProvider) {
		executorService.submit(() -> {
			while (keepReading) {
				try (InputStream inputStream = inputStreamProvider.getInputStream()) {
					final byte[] readBuffer = new byte[1024];
					int bytesRead;
					while ((bytesRead = inputStream.read(readBuffer)) != -1) {
						processData(readBuffer, bytesRead); // Custom method to handle the data
					}
				} catch (Exception e) {
					System.err.println("Error reading from InputStream: " + e.getMessage());
					try {
						// Optional: wait before retrying to avoid excessive retries
						Thread.sleep(1000);
					} catch (InterruptedException interruptedException) {
						Thread.currentThread().interrupt();
						System.err.println("Thread interrupted: " + interruptedException.getMessage());
					}
				}
			}
		});
	}

	private void processData(final byte[] readBuffer, final int bytesRead) {
		// Implement your logic to handle the data read from the InputStream
		baos.write(readBuffer, 0, bytesRead);
		String data = baos.toString(StandardCharsets.UTF_8);

		final String contentLengthPropery = "Content-Length:";
		final int frameStart = data.indexOf(contentLengthPropery);
		if (frameStart > 0) {
			final String contentLengthString = data.substring(frameStart + contentLengthPropery.length(), data.indexOf("\r\n", frameStart));
			int contentLength = -1;
			try {
				contentLength = Integer.parseInt(contentLengthString.trim());
			} catch (NumberFormatException nfe) {
				LOGGER.warn("Could not parse content length: " + contentLength);
			}
			if (contentLength > 0) {
				final int imageDataStart = frameStart + contentLengthPropery.length() + contentLengthString.length() + 4;
				if (baos.size() >= imageDataStart + contentLength) {
					// at least one image in the cache
					final byte[] bytes = baos.toByteArray();
					final byte[] imageBytes = Arrays.copyOfRange(bytes, imageDataStart, contentLength);
					for (final BlockingQueue<byte[]> imageQueue : new ArrayList<>(imageQueueHolder.getImageQueueList())) {
						// new image instance for every queue
						imageQueue.add(imageBytes);
						while (imageQueue.size() > 30) {
							imageQueue.poll();
						}
					}

					// reset baos
					baos = new ByteArrayOutputStream(DATA_BUFFER_SIZE);
					baos.write(bytes, imageDataStart + contentLength, bytes.length - (imageDataStart + contentLength));
				}
			}
		}
	}

	public void stopReading() {
		keepReading = false;
		executorService.shutdown();
	}
}
