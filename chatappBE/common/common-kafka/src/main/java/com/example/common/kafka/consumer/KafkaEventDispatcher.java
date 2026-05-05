package com.example.common.kafka.consumer;

import com.example.common.event.EventEnvelope;
import com.example.common.kafka.flow.KafkaEventRoutingContext;
import com.example.common.kafka.observability.KafkaEventObserver;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Dispatcher that routes incoming {@link EventEnvelope} instances to the
 * registered {@link KafkaEventHandler} by {@code eventType}.
 *
 * <p>Services can inject this dispatcher into their existing {@code @KafkaListener}
 * methods to gain handler-registry-driven routing without replacing the Spring Kafka
 * listener infrastructure. Using the dispatcher is optional.
 *
 * <p>When a {@link KafkaEventObserver} is provided, dispatch success and failure
 * are reported via {@link KafkaEventObserver#logDispatch} and
 * {@link KafkaEventObserver#logDispatchError}. These callbacks use a routing context
 * with {@code null} topic and key because the dispatcher is decoupled from transport.
 */
@Slf4j
public class KafkaEventDispatcher {

    private final Map<String, KafkaEventHandler<?>> handlerMap;
    private final KafkaEventObserver observer;

    public KafkaEventDispatcher(List<KafkaEventHandler<?>> handlers, KafkaEventObserver observer) {
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
    }

    /**
     * Convenience constructor for services that do not need dispatcher-level observability.
     * Use {@link #KafkaEventDispatcher(List, KafkaEventObserver)} to wire observer callbacks.
     */
    public KafkaEventDispatcher(List<KafkaEventHandler<?>> handlers) {
        this(handlers, null);
    }

    /**
     * Dispatches the event to the handler registered for its {@code eventType}.
     * Logs a warning and returns without throwing when no handler is found.
     *
     * <p>On success, calls {@link KafkaEventObserver#logDispatch} if an observer is present.
     * On handler failure, calls {@link KafkaEventObserver#logDispatchError} and re-throws.
     *
     * @param event the incoming Kafka event, not null
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void dispatch(EventEnvelope<?> event) {
        String eventType = event == null || event.metadata() == null ? null : event.metadata().getEventType();

        KafkaEventHandler handler = handlerMap.get(eventType);

        if (handler == null) {
            log.warn("No KafkaEventHandler registered for eventType={}", eventType);
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
