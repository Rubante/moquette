package io.moquette.persistence.redis;

import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import redis.clients.jedis.JedisPoolConfig;

public class RedisDao<T> {

    private static final Logger logger = LoggerFactory.getLogger(RedisDao.class);

    protected RedisTemplate<String, T> redisTemplate = new RedisTemplate<>();

    private JedisConnectionFactory connectionFactory;

    /** 是否准备好 **/
    private boolean ready = false;

    public void connect(Properties config) {

        // 如果已经存在，则销毁
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }

        connectionFactory = new JedisConnectionFactory();

        String host = config.getProperty(RedisConstant.HOST);
        String port = config.getProperty(RedisConstant.PORT);
        String password = config.getProperty(RedisConstant.PASSWORD);
        String database = config.getProperty(RedisConstant.DATABASE);

        connectionFactory.setHostName(host);
        connectionFactory.setPort(Integer.parseInt(port));
        connectionFactory.setPassword(password);
        connectionFactory.setDatabase(Integer.parseInt(database));
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(100);
        connectionFactory.setPoolConfig(poolConfig);

        connectionFactory.afterPropertiesSet();

        redisTemplate.setConnectionFactory(connectionFactory);

        redisTemplate.setKeySerializer(new StringRedisSerializer());
        redisTemplate.setValueSerializer(new MyRedisSerialize<>());
        redisTemplate.setHashKeySerializer(new StringRedisSerializer());
        
        redisTemplate.afterPropertiesSet();

        logger.info("redis config set! ip:" + host + ", port:" + port + ", password:" + password + ", database:"
                + database);

        ready = true;
    }

    /**
     * 清理redis相关资源
     * 
     */
    public void close() {
        connectionFactory.destroy();
    }

    /**
     * 获取
     * 
     * @return
     */
    public RedisTemplate<String, T> getTemplate() {

        if (ready) {
            return redisTemplate;
        }

        return null;
    }

    /**
     * 获取字符串操作对象
     * 
     * @return
     */
    public <V> ValueOperations<String, V> opsForValue() {
        return (ValueOperations<String,V>)redisTemplate.opsForValue();
    }

    /**
     * 获取列表操作对象
     * 
     * @return
     */
    public ListOperations<String, T> opsForList() {
        return redisTemplate.opsForList();
    }

    /**
     * 获取hash操作对象
     * 
     * @return
     */
    public <K, V> HashOperations<String, K, V> opsForHash() {
        return redisTemplate.opsForHash();
    }

    /**
     * 获取Set操作对象
     * 
     * @return
     */
    public <V> SetOperations<String, V> opsForSet() {
        return (SetOperations<String, V>)redisTemplate.opsForSet();
    }

    /**
     * 获取ZSet操作对象
     * 
     * @return
     */
    public ZSetOperations<String, T> opsForZSet() {
        return redisTemplate.opsForZSet();
    }

}
