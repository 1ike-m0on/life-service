package io.github.ikemoon.lifeservice.order.messaging;

public interface FlashSaleOrderMessagePublisher {

    void publish(FlashSaleOrderCommand command);
}
