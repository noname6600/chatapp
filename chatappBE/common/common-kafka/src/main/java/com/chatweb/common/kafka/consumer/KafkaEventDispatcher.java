package com.chatweb.common.kafka.consumer;

import com.chatweb.common.event.EventEnvelope;
import com.chatweb.common.kafka.exception.KafkaMessagingException;
import com.chatweb.common.kafka.flow.KafkaEventRoutingContext;
import com.chatweb.common.kafka.observability.KafkaEventObserver;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Dispatcher that routes incoming {@link EventEnvelope} instances to the
 * registered {@link KafkaEventHandler} by {@code eventType}.
 *
 * Unknown event types are handled by {@link UnknownKafkaEventPolicy}:
 *   FAIL — throws so the listener container's error handler routes to dead letter.
 *   DROP — logs a warning and silently discards.
 */
@Slf4j
public class KafkaEventDispatcher {

    private final Map<String, KafkaEventHandler<?>> handlerMap;
    private final KafkaEventObserver observer;
    private final UnknownKafkaEventPolicy unknownPolicy;

    public KafkaEventDispatcher(List<KafkaEventHandler<?>> handlers,
                                KafkaEventObserver observer,
                                UnknownKafkaEventPolicy unknownPolicy) {
        this.handlerMap = handlers.stream()
                .collect(Collectors.toMap(
                        KafkaEventHandler::eventType,
                        h -> h,
                        (a, b) -> {
                            throw new IllegalStateException(
                                    "Duplicate KafkaEventHandler for eventType=" + a.eventType()
                            );
                        }
                ));
        this.observer = observer;
        this.unknownPolicy = unknownPolicy != null ? unknownPolicy : UnknownKafkaEventPolicy.FAIL;
    }

    public KafkaEventDispatcher(List<KafkaEventHandler<?>> handlers, KafkaEventObserver observer) {
        this(handlers, observer, UnknownKafkaEventPolicy.FAIL);
    }

    public KafkaEventDispatcher(List<KafkaEventHandler<?>> handlers) {
        this(handlers, null, UnknownKafkaEventPolicy.FAIL);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public void dispatch(EventEnvelope<?> event) {
        String eventType = event == null || event.metadata() == null ? null : event.metadata().getEventType();

        KafkaEventHandler handler = handlerMap.get(eventType);

        if (handler == null) {
            if (unknownPolicy == UnknownKafkaEventPolicy.FAIL) {
                // Throw so DefaultErrorHandler routes this message to system.dead-letter.
                throw new KafkaMessagingException(null,
                        "No KafkaEventHandler registered for eventType=" + eventType
                                + " — routing to dead letter (policy=FAIL)");
            }
            log.warn("[KAFKA-DISPATCH] No handler for eventType={}, dropping (policy=DROP)", eventType);
            return;
        }

        KafkaEventRoutingContext context = KafkaEventRoutingContext.of(null, null, event);
        try {
            handler.handle(event);
            if (observer != null) {
                observer.logDispatch(context, event);
            }
        } catch (Exception ex) {
            if (observer != null) {
                observer.logDispatchError(context, event, ex);
            }
            throw ex;
        }
    }
}
