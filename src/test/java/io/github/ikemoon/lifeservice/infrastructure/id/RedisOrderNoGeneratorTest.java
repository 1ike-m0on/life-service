package io.github.ikemoon.lifeservice.infrastructure.id;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisOrderNoGeneratorTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Test
    void nextOrderNoUsesDailyRedisSequence() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment("life:id:order:" + today())).thenReturn(42L);

        RedisOrderNoGenerator generator = new RedisOrderNoGenerator(redisTemplate);

        String orderNo = generator.nextOrderNo("LSO");

        assertThat(orderNo).isEqualTo("LSO" + today() + "0000000042");
        verify(valueOperations).increment("life:id:order:" + today());
    }

    @Test
    void nextOrderNoThrowsWhenRedisDoesNotReturnSequence() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment("life:id:order:" + today())).thenReturn(null);

        RedisOrderNoGenerator generator = new RedisOrderNoGenerator(redisTemplate);

        assertThatThrownBy(() -> generator.nextOrderNo("LSO"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Failed to generate order sequence");
    }

    private static String today() {
        return LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
    }
}
