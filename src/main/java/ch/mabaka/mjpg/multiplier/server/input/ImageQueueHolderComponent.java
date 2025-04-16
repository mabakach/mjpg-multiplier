package ch.mabaka.mjpg.multiplier.server.input;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.BlockingQueue;

import org.springframework.stereotype.Component;

@Component
public class ImageQueueHolderComponent {

	final List<BlockingQueue<BufferedImage>> imageQueueList = Collections.synchronizedList(new ArrayList<>());
	
	public void addImageQueueList(BlockingQueue<BufferedImage> imageQueue) {
		this.imageQueueList.add(imageQueue);
	}
	
	public void removeImageQueueList(BlockingQueue<BufferedImage> imageQueue) {
		this.imageQueueList.remove(imageQueue);
	}

	public List<BlockingQueue<BufferedImage>> getImageQueueList(){
		return imageQueueList;
	}
}
