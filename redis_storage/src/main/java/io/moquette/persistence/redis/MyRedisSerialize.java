package io.moquette.persistence.redis;

import org.nustaq.serialization.FSTConfiguration;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;

public class MyRedisSerialize<T> implements RedisSerializer<T> {

    private static FSTConfiguration configuration = FSTConfiguration.createDefaultConfiguration();

    @Override
    public byte[] serialize(T t) throws SerializationException {
        byte[] byts = configuration.asByteArray(t);

        return byts;
    }

    @SuppressWarnings("unchecked")
    @Override
    public T deserialize(byte[] bytes) throws SerializationException {
        if (bytes != null) {
            return (T) configuration.asObject(bytes);
        } else {
            return null;
        }
    }

}
