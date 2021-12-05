package io.datawire.tpapidemo.producer.restapi;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
public class Controller {
	private static final Logger log = LoggerFactory.getLogger(Controller.class);
	@Autowired
	private KafkaTemplate<String,String> kafkaTemplate;
	@Value("${app.topic.demo}")
	private String topicDemo;

	@PostMapping("/send/{name}")
	ResponseEntity<String> sendName(@RequestHeader Map<String,String> rqHeaders, @PathVariable String name) {
		List<Header> headers = new ArrayList<>(rqHeaders.size());
		rqHeaders.forEach((key, value) -> {
			log.info("{}: {}", key, value);
			headers.add(new RecordHeader(key, value.getBytes(StandardCharsets.UTF_8)));
		});
		kafkaTemplate.send(new ProducerRecord<>(topicDemo, null, "name", name, headers));
		return new ResponseEntity<String>(String.format("Copied %d headers", headers.size()), HttpStatus.OK);
	}
}
