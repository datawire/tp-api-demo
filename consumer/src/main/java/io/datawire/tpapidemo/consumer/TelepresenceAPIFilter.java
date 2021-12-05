package io.datawire.tpapidemo.consumer;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;
import java.util.TreeSet;

@Service
public class TelepresenceAPIFilter {
    private static final Logger log = LoggerFactory.getLogger(TelepresenceAPIFilter.class);

    private final TreeSet<String> skipHeaders;
    private final URI consumeHereURI;
    private final HttpClient client;
    private final String callerId;

    public TelepresenceAPIFilter() {
        String port = System.getenv("TELEPRESENCE_API_PORT");
        if (port != null) {
            // Verify that the port number is in the range 1 - 0xffff
            try {
                int n = Integer.parseUnsignedInt(port, 10);
                if (n > 0xffff) {
                    throw new NumberFormatException("not unsigned short");
                }
                if (n == 0) {
                    // Silently ignore if "0"
                    port = null;
                }
            } catch (NumberFormatException e) {
                log.error("TELEPRESENCE_API_PORT \"{}\" is not a valid port number: {}", port,
                        e.getMessage());
                port = null;
            }
        }

        if (port == null) {
            log.info("Telepresence API not enabled because TELEPRESENCE_API_PORT is not set to a valid port number");
            consumeHereURI = null;
            client = null;
            callerId = null;
            skipHeaders = null;
        } else {
            log.info("Telepresence API enabled on port {}", port);
            consumeHereURI = URI.create("http://localhost:" + port + "/consume-here");
            client = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(100)).build();
            callerId = System.getenv("TELEPRESENCE_INTERCEPT_ID");
            skipHeaders = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

            // Copied from jdk.internal.net.http.common.DISALLOWED_HEADERS_SET and extended with
            // "content-type" and "user-agent"
            skipHeaders.addAll(Set.of("connection", "content-length", "content-type", "user-agent",
                    "date", "expect", "from", "host", "upgrade", "via", "warning"));
        }
    }


    /**
     * Extend the groupId with the current intercept-id when this process is running as
     * an interceptor.
     * <p>
     * The groupId must be unique during an intercept to ensure that that Kafka sends
     * the message to both the intercepted container and all interceptor processes. The
     * intercepted container's groupId is unaffected (returned verbatim).
     * </p>
     *
     * @param groupId the original groupId.
     * @return the argument possibly extended with the current intercept-id.
     */
    public String groupId(String groupId) {
        return callerId == null ? groupId : groupId + "-" + callerId;
    }

    /**
     * Set the record filter strategy of the factory to one that uses the TelepresenceAPI
     * endpoint "/consume-here" to determine if the message should be consumed or not given
     * its headers.
     *
     * @param factory
     */
    public void setRecordFilterStrategy(ConcurrentKafkaListenerContainerFactory<?, ?> factory) {
        if (consumeHereURI != null)
            factory.setRecordFilterStrategy(this::filter);
    }

    private boolean filter(ConsumerRecord<?, ?> record) {
        try {
            HttpRequest.Builder rq = HttpRequest.newBuilder().uri(consumeHereURI);
            record.headers().forEach(kafkaHeader -> {
                String key = kafkaHeader.key();
                if (!skipHeaders.contains(key)) {
                    rq.header(key, new String(kafkaHeader.value(), StandardCharsets.UTF_8));
                }
            });
            if (callerId != null) {
                rq.header("X-Telepresence-Caller-Intercept-Id", callerId);
            }
            HttpResponse<String> rsp = client.send(rq.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            String body = rsp.body();
            if (rsp.statusCode() == 200) {
                log.info("consume-here returned {}", body);
                return body == null || !body.equals("true");
            }
            log.error("consume-here returned status code {} {}", rsp.statusCode(), body);
        } catch (Exception e) {
            log.error("consume-here failed", e);
        }
        return true;
    }
}
