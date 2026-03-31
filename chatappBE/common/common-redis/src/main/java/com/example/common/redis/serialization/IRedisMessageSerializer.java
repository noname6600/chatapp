package com.example.common.redis.serialization;


import com.example.common.redis.api.IRedisMessage;

public interface IRedisMessageSerializer {

    String serialize(IRedisMessage message);

    IRedisMessage deserialize(String payload);
}

