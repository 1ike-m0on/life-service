package io.github.ikemoon.lifeservice.order.messaging;

import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RocketMqFlashSaleOrderConsumerTest {

    @Test
    void onMessageDelegatesToCommandHandler() {
        FlashSaleOrderCommandHandler commandHandler = mock(FlashSaleOrderCommandHandler.class);
        RocketMqFlashSaleOrderConsumer consumer = new RocketMqFlashSaleOrderConsumer(commandHandler);
        FlashSaleOrderCommand command = new FlashSaleOrderCommand("LSO202605200000000001", 1L, 10L);

        consumer.onMessage(command);

        verify(commandHandler).handle(command);
    }
}
