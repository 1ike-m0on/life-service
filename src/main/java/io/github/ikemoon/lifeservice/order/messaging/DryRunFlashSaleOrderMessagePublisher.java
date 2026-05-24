package io.github.ikemoon.lifeservice.order.messaging;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "life-service.messaging.order.provider", havingValue = "dry-run")
public class DryRunFlashSaleOrderMessagePublisher implements FlashSaleOrderMessagePublisher {

    @Override
    public void publish(FlashSaleOrderCommand command) {
        // Intentionally no-op: used only to isolate the hot-path cost before MQ/DB persistence.
    }
}
