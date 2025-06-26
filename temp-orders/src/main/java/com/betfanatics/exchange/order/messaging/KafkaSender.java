package com.betfanatics.exchange.order.messaging;

import com.betfanatics.exchange.order.model.messaging.KafkaSendResult;

import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaSender {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public CompletableFuture<KafkaSendResult> send(String topic, Object message) {
        return send(topic, null, message);
    }

    public CompletableFuture<KafkaSendResult> send(String topic, String key,  Object message) {

        CompletableFuture<SendResult<String, Object>> future;

        if (key == null) {
            future = kafkaTemplate.send(topic, message);
        } else {
            future = kafkaTemplate.send(topic, key, message);
        }

        return future.thenApply(this::processSuccess)
                .exceptionally(this::processFailure);

    }

    protected KafkaSendResult processSuccess(SendResult<String, Object> result) {
        RecordMetadata recordMetadata = result.getRecordMetadata();
        if (recordMetadata != null) {
            log.debug("Successfully sent message to topic: {} partition: {} offset: {} timestamp: {}",
                    recordMetadata.topic(), recordMetadata.partition(), recordMetadata.offset(), recordMetadata.timestamp());
        }
        return KafkaSendResult.success();
    }

    protected KafkaSendResult processFailure(Throwable throwable) {
        log.error("Failed to produce message.  Error Message: {}", throwable.getMessage());
        return KafkaSendResult.failed(throwable.getMessage());
    }

}

