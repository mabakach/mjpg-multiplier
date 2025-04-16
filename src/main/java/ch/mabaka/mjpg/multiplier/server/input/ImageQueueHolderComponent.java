package ch.mabaka.mjpg.multiplier.server.input;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.BlockingQueue;

import org.springframework.stereotype.Component;

@Component
public class ImageQueueHolderComponent {

	final List<BlockingQueue<byte[]>> imageQueueList = Collections.synchronizedList(new ArrayList<>());
	
	public void addImageQueueList(BlockingQueue<byte[]> imageQueue) {
		this.imageQueueList.add(imageQueue);
	}
	
	public void removeImageQueueList(BlockingQueue<byte[]> imageQueue) {
		this.imageQueueList.remove(imageQueue);
	}

	public List<BlockingQueue<byte[]>> getImageQueueList(){
		return imageQueueList;
	}
}
