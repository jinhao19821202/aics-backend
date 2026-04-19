package com.aics.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory cf) {
        RedisTemplate<String, Object> tpl = new RedisTemplate<>();
        tpl.setConnectionFactory(cf);

        ObjectMapper om = new ObjectMapper();
        om.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        om.registerModule(new JavaTimeModule());
        om.activateDefaultTyping(
                BasicPolymorphicTypeValidator.builder().allowIfSubType(Object.class).build(),
                ObjectMapper.DefaultTyping.NON_FINAL);

        Jackson2JsonRedisSerializer<Object> ser = new Jackson2JsonRedisSerializer<>(om, Object.class);
        StringRedisSerializer strSer = new StringRedisSerializer();

        tpl.setKeySerializer(strSer);
        tpl.setHashKeySerializer(strSer);
        tpl.setValueSerializer(ser);
        tpl.setHashValueSerializer(ser);
        tpl.afterPropertiesSet();
        return tpl;
    }

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory cf) {
        return new StringRedisTemplate(cf);
    }

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(RedisConnectionFactory cf) {
        RedisMessageListenerContainer c = new RedisMessageListenerContainer();
        c.setConnectionFactory(cf);
        return c;
    }
}
