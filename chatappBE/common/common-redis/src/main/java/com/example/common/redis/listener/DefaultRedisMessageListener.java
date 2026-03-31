package com.example.common.redis.listener;


import com.example.common.redis.api.IRedisMessage;
import com.example.common.redis.dispatcher.RedisMessageDispatcher;
import com.example.common.redis.observability.IRedisPubSubLogger;
import com.example.common.redis.serialization.IRedisMessageSerializer;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;

import java.nio.charset.StandardCharsets;

@RequiredArgsConstructor
public class DefaultRedisMessageListener
        implements MessageListener {

    private final IRedisMessageSerializer serializer;
    private final RedisMessageDispatcher dispatcher;
    private final IRedisPubSubLogger logger;

    @Override
    public void onMessage(Message message, byte[] pattern) {

        String channel =
                new String(message.getChannel(), StandardCharsets.UTF_8);

        String payload =
                new String(message.getBody(), StandardCharsets.UTF_8);

        try {
            IRedisMessage redisMessage =
                    serializer.deserialize(payload);

            logger.logReceive(channel, redisMessage);
            dispatcher.dispatch(redisMessage);

        } catch (Exception ex) {

            logger.logDeserializeError(channel, payload, ex);


        }
    }
}


