package com.betfanatics.exchange.order.model.messaging;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
@Getter
@Setter
public class KafkaSendResult {

    boolean success;
    String errorMessage;

    public static KafkaSendResult success() {
        KafkaSendResult result = new KafkaSendResult();
        result.setSuccess(true);
        return result;
    }

    public static KafkaSendResult failed(String errorMessage) {
        KafkaSendResult result = new KafkaSendResult();
        result.setSuccess(false);
        result.setErrorMessage(errorMessage);
        return result;
    }

}


