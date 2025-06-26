package com.betfanatics.exchange.order.messaging;

import com.betfanatics.exchange.order.model.messaging.KafkaSendResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;


@ExtendWith(MockitoExtension.class)
class KafkaSenderTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private KafkaSender kafkaSender;


    @Test
    void testSend_withoutKey_shouldSendMessage() throws Exception {

        // Given
        String topic = "test-topic";
        Object message = "test-message";

        SendResult<String, Object> sendResult = mock(SendResult.class);
        CompletableFuture<SendResult<String, Object>> future = CompletableFuture.completedFuture(sendResult);

        when(kafkaTemplate.send(topic, message)).thenReturn(future);

        // When
        CompletableFuture<KafkaSendResult> resultFuture = kafkaSender.send(topic, message);

        // Then
        KafkaSendResult result = resultFuture.get();

        assertThat(result).isNotNull();
        assertThat(result.isSuccess()).isTrue();
        verify(kafkaTemplate, times(1)).send(topic, message);
    }

    @Test
    void testSend_withKey_shouldSendMessage() throws Exception {

        // Given
        String topic = "test-topic";
        String key = "test-key";
        Object message = "test-message";

        SendResult<String, Object> sendResult = mock(SendResult.class);
        CompletableFuture<SendResult<String, Object>> future = CompletableFuture.completedFuture(sendResult);

        when(kafkaTemplate.send(topic, key, message)).thenReturn(future);

        // When
        CompletableFuture<KafkaSendResult> resultFuture = kafkaSender.send(topic, key, message);

        // Then
        KafkaSendResult result = resultFuture.get();

        assertThat(result).isNotNull();
        assertThat(result.isSuccess()).isTrue();
        verify(kafkaTemplate, times(1)).send(topic, key, message);
    }

    @Test
    void testSend_shouldHandleFailure() throws Exception {

        // Given
        String topic = "test-topic";
        Object message = "test-message";
        String errorMessage = "Kafka error";

        CompletableFuture<SendResult<String, Object>> future = new CompletableFuture<>();
        RuntimeException exception = new RuntimeException(errorMessage);
        future.completeExceptionally(exception);
        when(kafkaTemplate.send(topic, message)).thenReturn(future);

        // When
        CompletableFuture<KafkaSendResult> resultFuture = kafkaSender.send(topic, message);

        // Then
        KafkaSendResult result = resultFuture.get();

        assertThat(result).isNotNull();
        assertThat(result.isSuccess()).isFalse();

        // -- Error message returned contains the canonical name of the exception class
        String expectedErrorMessage = exception.getClass().getCanonicalName() + ": " + errorMessage;
        assertThat(result.getErrorMessage()).isEqualTo(expectedErrorMessage);
        verify(kafkaTemplate, times(1)).send(topic, message);
    }
}
