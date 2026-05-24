package io.github.ikemoon.lifeservice.order.messaging;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatNoException;

class DryRunFlashSaleOrderMessagePublisherTest {

    private final DryRunFlashSaleOrderMessagePublisher publisher = new DryRunFlashSaleOrderMessagePublisher();

    @Test
    void publishDoesNothing() {
        FlashSaleOrderCommand command = new FlashSaleOrderCommand("LSO202605230000000001", 1001L, 1L);

        assertThatNoException().isThrownBy(() -> publisher.publish(command));
    }
}
