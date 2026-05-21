package io.github.ikemoon.lifeservice.order.messaging;

import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "life-service.messaging.order.provider", havingValue = "rocketmq", matchIfMissing = true)
public class RocketMqFlashSaleOrderMessagePublisher implements FlashSaleOrderMessagePublisher {

    private final RocketMQTemplate rocketMQTemplate;
    private final FlashSaleOrderMessagingProperties properties;

    public RocketMqFlashSaleOrderMessagePublisher(
            RocketMQTemplate rocketMQTemplate,
            FlashSaleOrderMessagingProperties properties) {
        this.rocketMQTemplate = rocketMQTemplate;
        this.properties = properties;
    }

    @Override
    public void publish(FlashSaleOrderCommand command) {
        SendResult sendResult = rocketMQTemplate.syncSend(properties.getFlashSaleOrderTopic(), command);
        if (sendResult == null || sendResult.getSendStatus() != SendStatus.SEND_OK) {
            throw new IllegalStateException("Failed to send flash sale order message, result=" + sendResult);
        }
    }
}
