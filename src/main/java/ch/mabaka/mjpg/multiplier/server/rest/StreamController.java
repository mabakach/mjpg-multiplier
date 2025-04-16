package ch.mabaka.mjpg.multiplier.server.rest;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;

import javax.imageio.ImageIO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import ch.mabaka.mjpg.multiplier.server.input.ImageQueueHolderComponent;
import ch.mabaka.mjpg.multiplier.server.input.MjpegInputStreamReaderComponent;

@RestController
@RequestMapping("/api")
public class StreamController {

	public static final MediaType MULTIPART_X_MIXED_REPLACE = new MediaType("multipart", "x-mixed-replace", Map.of("boundary", "FRAME"));

	
	@Autowired
	ImageQueueHolderComponent imageQueueHolder;

	@Autowired
	MjpegInputStreamReaderComponent mjpegInputStreamReader;

	private static final Logger LOGGER = LoggerFactory.getLogger(StreamController.class);

	@GetMapping(value = "/stream.mjpg", produces = "multipart/x-mixed-replace; boundary=FRAME")
	public ResponseEntity<StreamingResponseBody> sendStream() {
		final BlockingQueue<byte[]> imageQueue = new LinkedBlockingDeque<>();
		imageQueueHolder.addImageQueueList(imageQueue);

		final StreamingResponseBody bodyStream =  new StreamingResponseBody() {
			@Override
			public void writeTo(OutputStream outputStream) throws IOException {
				while (true) {
					try {
						final byte[] imageData = imageQueue.take();
						if (imageData != null) {
							outputStream.write("--FRAME\r\n".getBytes());
							try {
								outputStream.write("Content-Type: image/jpeg\r\n".getBytes());
								outputStream.write(String.format("Content-Length: %d\r\n\r\n", imageData.length).getBytes());
								outputStream.write(imageData);
							} finally {
								outputStream.write("\r\n".getBytes());
								outputStream.flush();
							}
						} else {
							LOGGER.warn("Image is null");
						}
					} catch (InterruptedException e) {
						LOGGER.warn("Waiting for image interrupted. Stop sending data.");
						outputStream.flush();
						break;
					}
					
				}
			}
		};
		
		return ResponseEntity.ok()
		        .contentType(MULTIPART_X_MIXED_REPLACE)
		        .body(bodyStream);
	}
}
