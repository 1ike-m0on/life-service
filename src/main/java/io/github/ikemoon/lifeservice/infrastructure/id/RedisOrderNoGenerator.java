package io.github.ikemoon.lifeservice.infrastructure.id;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Component
public class RedisOrderNoGenerator implements OrderNoGenerator {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;
    private static final int SEQUENCE_WIDTH = 10;

    private final StringRedisTemplate redisTemplate;

    public RedisOrderNoGenerator(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public String nextOrderNo(String prefix) {
        String date = LocalDate.now().format(DATE_FORMATTER);
        String key = "life:id:order:" + date;
        Long sequence = redisTemplate.opsForValue().increment(key);
        if (sequence == null) {
            throw new IllegalStateException("Failed to generate order sequence");
        }
        return prefix + date + String.format("%0" + SEQUENCE_WIDTH + "d", sequence);
    }
}
