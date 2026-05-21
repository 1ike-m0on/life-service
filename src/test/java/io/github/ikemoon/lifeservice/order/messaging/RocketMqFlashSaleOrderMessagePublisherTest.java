package io.github.ikemoon.lifeservice.order.messaging;

import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RocketMqFlashSaleOrderMessagePublisherTest {

    @Test
    void publishSendsCommandToConfiguredTopic() {
        RocketMQTemplate rocketMQTemplate = mock(RocketMQTemplate.class);
        FlashSaleOrderMessagingProperties properties = new FlashSaleOrderMessagingProperties();
        properties.setFlashSaleOrderTopic("life-service-flash-sale-order");
        RocketMqFlashSaleOrderMessagePublisher publisher =
                new RocketMqFlashSaleOrderMessagePublisher(rocketMQTemplate, properties);
        FlashSaleOrderCommand command = new FlashSaleOrderCommand("LSO202605200000000001", 1L, 10L);
        when(rocketMQTemplate.syncSend("life-service-flash-sale-order", command))
                .thenReturn(sendResult(SendStatus.SEND_OK));

        publisher.publish(command);

        verify(rocketMQTemplate).syncSend("life-service-flash-sale-order", command);
    }

    @Test
    void publishThrowsWhenRocketMqSendStatusIsNotOk() {
        RocketMQTemplate rocketMQTemplate = mock(RocketMQTemplate.class);
        FlashSaleOrderMessagingProperties properties = new FlashSaleOrderMessagingProperties();
        properties.setFlashSaleOrderTopic("life-service-flash-sale-order");
        RocketMqFlashSaleOrderMessagePublisher publisher =
                new RocketMqFlashSaleOrderMessagePublisher(rocketMQTemplate, properties);
        FlashSaleOrderCommand command = new FlashSaleOrderCommand("LSO202605200000000001", 1L, 10L);
        when(rocketMQTemplate.syncSend("life-service-flash-sale-order", command))
                .thenReturn(sendResult(SendStatus.FLUSH_DISK_TIMEOUT));

        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> publisher.publish(command));
    }

    private static SendResult sendResult(SendStatus status) {
        SendResult sendResult = new SendResult();
        sendResult.setSendStatus(status);
        return sendResult;
    }
}
