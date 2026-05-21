package io.github.ikemoon.lifeservice.order.messaging;

import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "life-service.messaging.order.provider", havingValue = "rocketmq", matchIfMissing = true)
@RocketMQMessageListener(
        topic = "${life-service.messaging.rocketmq.flash-sale-order-topic}",
        consumerGroup = "${life-service.messaging.rocketmq.flash-sale-order-consumer-group}")
public class RocketMqFlashSaleOrderConsumer implements RocketMQListener<FlashSaleOrderCommand> {

    private final FlashSaleOrderCommandHandler commandHandler;

    public RocketMqFlashSaleOrderConsumer(FlashSaleOrderCommandHandler commandHandler) {
        this.commandHandler = commandHandler;
    }

    @Override
    public void onMessage(FlashSaleOrderCommand command) {
        commandHandler.handle(command);
    }
}
