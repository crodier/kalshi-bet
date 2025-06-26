package com.betfanatics.exchange.order.util;

import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.spi.ILoggingEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FixMessageLayout extends PatternLayout {
    private static final Pattern FIX_FIELD_PATTERN = Pattern.compile("(\\d+)=([^\\x01]+)\\x01");
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String doLayout(ILoggingEvent event) {
        String message = event.getFormattedMessage();
        
        try {
            // Check if the message contains FIX message format (fields separated by SOH)
            if (message.contains("\u0001")) {
                Map<String, String> fixFields = new LinkedHashMap<>();
                Matcher matcher = FIX_FIELD_PATTERN.matcher(message);
                
                while (matcher.find()) {
                    String tag = matcher.group(1);
                    String value = matcher.group(2);
                    fixFields.put(tag, value);
                }

                ObjectNode jsonNode = objectMapper.createObjectNode();
                jsonNode.put("timestamp", event.getTimeStamp());
                jsonNode.put("level", event.getLevel().toString());
                jsonNode.put("logger", event.getLoggerName());
                
                ObjectNode fixNode = objectMapper.createObjectNode();
                fixFields.forEach(fixNode::put);
                jsonNode.set("fix", fixNode);

                return objectMapper.writeValueAsString(jsonNode) + "\n";
            }
        } catch (Exception e) {
            return String.format("{\"error\":\"Failed to parse FIX message: %s\", \"original\":\"%s\"}\n", 
                e.getMessage(), message.replace("\"", "\\\""));
        }

        // If not a FIX message, return the original message in a simple JSON format
        return String.format("{\"message\":\"%s\"}\n", message.replace("\"", "\\\""));
    }
}
