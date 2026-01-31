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

import ch.mabaka.mjpg.multiplier.server.input.data.FrameHeader;
import ch.mabaka.mjpg.multiplier.server.rest.StreamController;
import jakarta.annotation.PostConstruct;

@Component
@Endpoint(id = "backendStream")
public class MjpegInputStreamReaderComponent {

	private static final Logger LOGGER = LoggerFactory.getLogger(StreamController.class);

	private static final int DATA_BUFFER_SIZE = 1024 * 1024;
	
	private static final String CONTENT_LENGTH_PROPERTY = "Content-Length:";
	
	@Autowired
	private ImageQueueHolderComponent imageQueueHolder;

	@Autowired
	private HttpInputStreamProvider httpInputStreamProvider;

	private ByteArrayOutputStream baos = new ByteArrayOutputStream(DATA_BUFFER_SIZE);

	private final ExecutorService executorService = Executors.newSingleThreadExecutor();

	private volatile boolean keepReading = true;

	
	private boolean isBackendStreamAvailable = false;

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
					LOGGER.error("Error reading from InputStream: " + e.getClass().getName() + " " + e.getMessage());
					LOGGER.debug("Stack trace: ", e);
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
		String data = baos.toString(StandardCharsets.UTF_8);

		
		final int frameStart = data.indexOf(CONTENT_LENGTH_PROPERTY);
		if (frameStart > 0) {
			FrameHeader frameHeader = getContentLengthString(data, frameStart);
			if (frameHeader.contentLength() > 0) {
				final int imageDataStart = frameHeader.indexFrameContentStart();
				if (baos.size() >= imageDataStart + frameHeader.contentLength()) {
					final byte[] bytes = baos.toByteArray();
					final byte[] imageBytes = Arrays.copyOfRange(bytes, imageDataStart, imageDataStart + frameHeader.contentLength());
					for (final BlockingQueue<byte[]> imageQueue : new ArrayList<>(imageQueueHolder.getImageQueueList())) {
						imageQueue.add(imageBytes);
						while (imageQueue.size() > 30) {
							imageQueue.poll();
						}
					}
					// reset baos
					baos = new ByteArrayOutputStream(DATA_BUFFER_SIZE);
					baos.write(bytes, imageDataStart + frameHeader.contentLength(), bytes.length - (imageDataStart + frameHeader.contentLength()));
				}
			}
		}
	}

	private FrameHeader getContentLengthString(String data, final int frameStart) {
		StringBuffer contentLength =  new StringBuffer();
		// loop over data starting from frameStart until we find a non-digit character
		int index = frameStart + CONTENT_LENGTH_PROPERTY.length();
		while (index < data.length()) {
			char c = data.charAt(index);
			if (c == ' ') {
				index++;
				continue;
			}
			if (!Character.isDigit(c)) {
				break;
			}
			contentLength.append(c);
			index++;
		}
		
		while (index < data.length()) {
			char c = data.charAt(index);
			if (c != '\r' && c != '\n') {
				break;
			} 
			index++;
		}
		try	{
			return new FrameHeader(Integer.parseInt(contentLength.toString()), index);
		} catch (NumberFormatException nfe) {
			LOGGER.warn("Could not parse content length: " + contentLength.toString());
			throw nfe;
		}
	}

	public void stopReading() {
		keepReading = false;
		executorService.shutdown();
	}
}