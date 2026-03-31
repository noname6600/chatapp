package com.example.common.kafka.observability;

import com.example.common.kafka.api.KafkaEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class KafkaEventLogger {

    public void logPublish(String topic, String key, KafkaEvent event) {
        log.info("Publishing KafkaEvent topic={} key={} eventId={} eventType={} sourceService={}",
                topic, key, event.getEventId(), event.getEventType(), event.getSourceService());
    }

    public void logError(String topic, String key, KafkaEvent event, Throwable ex) {
        log.error("KafkaEvent publish error topic={} key={} eventId={} eventType={} sourceService={}",
                topic,
                key,
                event != null ? event.getEventId() : null,
                event != null ? event.getEventType() : null,
                event != null ? event.getSourceService() : null,
                ex
        );
    }
}
