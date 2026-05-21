package io.github.ikemoon.lifeservice.order.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "life-service.messaging.rocketmq")
public class FlashSaleOrderMessagingProperties {

    private String flashSaleOrderTopic;
    private String flashSaleOrderConsumerGroup;

    public String getFlashSaleOrderTopic() {
        return flashSaleOrderTopic;
    }

    public void setFlashSaleOrderTopic(String flashSaleOrderTopic) {
        this.flashSaleOrderTopic = flashSaleOrderTopic;
    }

    public String getFlashSaleOrderConsumerGroup() {
        return flashSaleOrderConsumerGroup;
    }

    public void setFlashSaleOrderConsumerGroup(String flashSaleOrderConsumerGroup) {
        this.flashSaleOrderConsumerGroup = flashSaleOrderConsumerGroup;
    }
}
