package io.datawire.tpapidemo.consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class Listener {
	private static final Logger log = LoggerFactory.getLogger(Listener.class);

	@KafkaListener(topics = "demoTopic")
	public void listenDemoTopic(String message) {
		log.info("Received Message: {}", message);
	}
}
