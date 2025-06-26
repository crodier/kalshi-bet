package com.betfanatics.exchange.order.model.messaging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class KafkaSendResultTest {

    private static String FAIL_MESSAGE = "fail";

    @Test
    void testSuccess() {

        // When
        KafkaSendResult result = KafkaSendResult.success();

        // Then
        assertNotNull(result);
        assertTrue(result.isSuccess());
    }

    @Test
    void testFailed() {

        // When
        KafkaSendResult result = KafkaSendResult.failed(FAIL_MESSAGE);

        // Then
        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertEquals(FAIL_MESSAGE, result.getErrorMessage());
    }

}
