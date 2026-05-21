package io.github.ikemoon.lifeservice.order.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "life-service.messaging.order.provider", havingValue = "local")
public class LocalAsyncFlashSaleOrderMessagePublisher implements FlashSaleOrderMessagePublisher {

    private static final Logger log = LoggerFactory.getLogger(LocalAsyncFlashSaleOrderMessagePublisher.class);

    private final TaskExecutor taskExecutor;
    private final FlashSaleOrderCommandHandler commandHandler;

    public LocalAsyncFlashSaleOrderMessagePublisher(
            @Qualifier("applicationTaskExecutor") TaskExecutor taskExecutor,
            FlashSaleOrderCommandHandler commandHandler) {
        this.taskExecutor = taskExecutor;
        this.commandHandler = commandHandler;
    }

    @Override
    public void publish(FlashSaleOrderCommand command) {
        taskExecutor.execute(() -> {
            try {
                commandHandler.handle(command);
            } catch (Exception e) {
                log.error("Failed to consume local flash sale order command, orderNo={}", command.orderNo(), e);
            }
        });
    }
}
