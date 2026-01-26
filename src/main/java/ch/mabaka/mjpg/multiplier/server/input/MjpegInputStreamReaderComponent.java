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
		LOGGER.debug("processData called: bytesRead={}, bufferSizeBeforeWrite={}", bytesRead, baos.size());
		baos.write(readBuffer, 0, bytesRead);
		byte[] buffer = baos.toByteArray();
		LOGGER.debug("Buffer size after write: {}", buffer.length);
		int searchPos = 0;
		final String boundary = "--FRAME";
		final String contentLengthHeader = "Content-Length:";
		int frameCount = 0;

		while (true) {
			if (++frameCount > MAX_FRAMES_PER_CALL) {
				LOGGER.warn("processData: Exceeded max frames per call ({}), breaking to avoid infinite loop.", MAX_FRAMES_PER_CALL);
				break;
			}
			int boundaryIndex = indexOf(buffer, boundary.getBytes(StandardCharsets.UTF_8), searchPos);
			if (boundaryIndex == -1) {
				LOGGER.info("No boundary found from searchPos {}. Breaking.", searchPos);
				break;
			}
			LOGGER.debug("Boundary found at {}", boundaryIndex);
			int headerEnd = indexOf(buffer, "\r\n\r\n".getBytes(StandardCharsets.UTF_8), boundaryIndex);
			if (headerEnd == -1) {
				LOGGER.info("Incomplete header after boundary at {}. Breaking.", boundaryIndex);
				break;
			}
			String header = new String(buffer, boundaryIndex, headerEnd - boundaryIndex, StandardCharsets.UTF_8);
			LOGGER.debug("Header found: {}", header.replaceAll("\r\n", "|"));
			int clIndex = header.indexOf(contentLengthHeader);
			if (clIndex == -1) {
				LOGGER.info("No Content-Length found in header. Skipping to next.");
				searchPos = headerEnd + 4;
				continue;
			}
			int clLineEnd = header.indexOf("\r\n", clIndex);
			if (clLineEnd == -1) {
				LOGGER.info("Incomplete Content-Length line in header. Breaking.");
				break;
			}
			String contentLengthString = header.substring(clIndex + contentLengthHeader.length(), clLineEnd).trim();
			int contentLength;
			try {
				contentLength = Integer.parseInt(contentLengthString);
				LOGGER.info("Parsed Content-Length: {}", contentLength);
			} catch (NumberFormatException nfe) {
				LOGGER.warn("Could not parse content length: {}", contentLengthString);
				searchPos = headerEnd + 4;
				continue;
			}
			int imageDataStart = headerEnd + 4;
			if (buffer.length < imageDataStart + contentLength) {
				LOGGER.info("Not enough data for image: buffer.length={}, needed={}. Breaking.", buffer.length, imageDataStart + contentLength);
				break;
			}
			byte[] imageBytes = Arrays.copyOfRange(buffer, imageDataStart, imageDataStart + contentLength);
			LOGGER.debug("Extracted imageBytes: {} bytes, adding to queues.", imageBytes.length);
			for (final BlockingQueue<byte[]> imageQueue : new ArrayList<>(imageQueueHolder.getImageQueueList())) {
				imageQueue.add(imageBytes);
				while (imageQueue.size() > 30) {
					imageQueue.poll();
				}
			}
			searchPos = imageDataStart + contentLength;
		}
		if (searchPos > 0) {
			LOGGER.debug("Processed up to searchPos {}. Trimming buffer.", searchPos);
			byte[] remaining = Arrays.copyOfRange(buffer, searchPos, buffer.length);
			baos = new ByteArrayOutputStream(DATA_BUFFER_SIZE);
			baos.write(remaining, 0, remaining.length);
		} else {
			LOGGER.info("No data processed in this call.");
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