package com.example.common.redis.dispatcher;

import com.example.common.redis.api.IRedisMessage;
import com.example.common.redis.api.IRedisSubscriber;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
public class RedisMessageDispatcher {

    private final Map<String, IRedisSubscriber<?>> subscriberMap;

    public RedisMessageDispatcher(List<IRedisSubscriber<?>> subscribers) {

        this.subscriberMap = subscribers.stream()
                .collect(Collectors.toMap(
                        IRedisSubscriber::eventType,
                        s -> s,
                        (a, b) -> {
                            throw new IllegalStateException(
                                    "Duplicate Redis subscriber for eventType=" + a.eventType()
                            );
                        }
                ));
    }

    public void dispatch(IRedisMessage message) {

        IRedisSubscriber subscriber =
                subscriberMap.get(message.getEventType());

        if (subscriber == null) {
            log.warn("No subscriber for eventType={}", message.getEventType());
            return;
        }

        subscriber.onMessage(message);
    }
}



