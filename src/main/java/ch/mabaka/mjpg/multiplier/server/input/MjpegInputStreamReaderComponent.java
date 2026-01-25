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
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.stereotype.Component;

import ch.mabaka.mjpg.multiplier.server.rest.StreamController;
import jakarta.annotation.PostConstruct;

@Component
@Endpoint(id = "backendStream")
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
	
	private boolean isBackendStreamAvailable = false;

	private static final int MAX_FRAMES_PER_CALL = 10;

	@PostConstruct
	public void init() {
		startReading(httpInputStreamProvider);
	}

	@ReadOperation
	public boolean backendStreamAvailable() {
		return isBackendStreamAvailable();
	}

	public void startReading(final IInputStreamProvider inputStreamProvider) {
		executorService.submit(() -> {
			while (keepReading) {
				try (InputStream inputStream = inputStreamProvider.getInputStream()) {
					final byte[] readBuffer = new byte[1024];
					int bytesRead;
					isBackendStreamAvailable = true;
					while ((bytesRead = inputStream.read(readBuffer)) != -1) {
						processData(readBuffer, bytesRead); // Custom method to handle the data
					}
				} catch (Exception e) {
					isBackendStreamAvailable = false;
					LOGGER.error("Error reading from InputStream: " + e.getMessage());
					try {
						// Optional: wait before retrying to avoid excessive retries
						Thread.sleep(1000);
					} catch (InterruptedException interruptedException) {
						Thread.currentThread().interrupt();
						LOGGER.error("Thread interrupted: " + interruptedException.getMessage());
					}
				}
			}
		});
	}
	
	public boolean isBackendStreamAvailable() {
		return isBackendStreamAvailable;
	}

	private void processData(final byte[] readBuffer, final int bytesRead) {
		baos.write(readBuffer, 0, bytesRead);
		byte[] buffer = baos.toByteArray();
		int searchPos = 0;
		final String boundary = "--FRAME";
		final String contentLengthHeader = "Content-Length:";
		int frameCount = 0;

		while (true) {
			if (++frameCount > MAX_FRAMES_PER_CALL) {
				LOGGER.warn("processData: Exceeded max frames per call ({}), breaking to avoid infinite loop.", MAX_FRAMES_PER_CALL);
				break;
			}
			// Find boundary
			int boundaryIndex = indexOf(buffer, boundary.getBytes(StandardCharsets.UTF_8), searchPos);
			if (boundaryIndex == -1) {
				break; // No complete frame boundary found
			}
			// Find header end (\r\n\r\n)
			int headerEnd = indexOf(buffer, "\r\n\r\n".getBytes(StandardCharsets.UTF_8), boundaryIndex);
			if (headerEnd == -1) {
				break; // Incomplete header
			}
			// Extract header
			String header = new String(buffer, boundaryIndex, headerEnd - boundaryIndex, StandardCharsets.UTF_8);
			// Find Content-Length
			int clIndex = header.indexOf(contentLengthHeader);
			if (clIndex == -1) {
				searchPos = headerEnd + 4;
				continue; // No Content-Length, skip to next
			}
			int clLineEnd = header.indexOf("\r\n", clIndex);
			if (clLineEnd == -1) {
				break; // Incomplete header line
			}
			String contentLengthString = header.substring(clIndex + contentLengthHeader.length(), clLineEnd).trim();
			int contentLength;
			try {
				contentLength = Integer.parseInt(contentLengthString);
			} catch (NumberFormatException nfe) {
				LOGGER.warn("Could not parse content length: {}", contentLengthString);
				searchPos = headerEnd + 4;
				continue; // Skip this frame
			}
			// Image data starts after header end
			int imageDataStart = headerEnd + 4;
			if (buffer.length < imageDataStart + contentLength) {
				break; // Not enough data yet
			}
			// Extract image
			byte[] imageBytes = Arrays.copyOfRange(buffer, imageDataStart, imageDataStart + contentLength);
			for (final BlockingQueue<byte[]> imageQueue : new ArrayList<>(imageQueueHolder.getImageQueueList())) {
				imageQueue.add(imageBytes);
				while (imageQueue.size() > 30) {
					imageQueue.poll();
				}
			}
			// Move search position forward
			searchPos = imageDataStart + contentLength;
		}
		// Remove processed data from baos
		if (searchPos > 0) {
			byte[] remaining = Arrays.copyOfRange(buffer, searchPos, buffer.length);
			baos = new ByteArrayOutputStream(DATA_BUFFER_SIZE);
			baos.write(remaining, 0, remaining.length);
		}
	}

	// Helper: find index of byte pattern in byte array
	private int indexOf(byte[] data, byte[] pattern, int start) {
		outer: for (int i = start; i <= data.length - pattern.length; i++) {
			for (int j = 0; j < pattern.length; j++) {
				if (data[i + j] != pattern[j]) {
					continue outer;
				}
			}
			return i;
		}
		return -1;
	}

	public void stopReading() {
		keepReading = false;
		executorService.shutdown();
	}
}
