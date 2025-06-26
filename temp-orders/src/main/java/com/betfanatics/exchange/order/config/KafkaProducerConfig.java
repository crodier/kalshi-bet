package com.betfanatics.exchange.order.config;

import static org.springframework.util.ObjectUtils.isEmpty;

import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.config.SaslConfigs;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class KafkaProducerConfig {

    @Value("${spring.kafka.bootstrap-servers:#{null}}")
    private String bootstrapServers;

    @Value("${spring.kafka.properties.security.protocol:#{null}}")
    private String securityProtocol;

    @Value("${spring.kafka.properties.sasl.mechanism:#{null}}")
    private String saslMechanism;

    @Value("${spring.kafka.properties.sasl.jaas.config:#{null}}")
    private String jaasConfig;


    private Map<String, Object> producerProperties = null;


    protected static Map<String, Object> createProducerProperties(String bootstrapServers, String securityProtocol, String saslMechanism, String jaasConfig) {

        Map<String, Object> props = new HashMap<>();

        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);

        if (!isEmpty(bootstrapServers)) {
            props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        }

        // Security properties, which are optional (since this could be running locally)
        if (!isEmpty(securityProtocol)) {
            props.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, securityProtocol);
        }

        if (!isEmpty(saslMechanism)) {
            props.put(SaslConfigs.SASL_MECHANISM, saslMechanism);
        }

        if (!isEmpty(jaasConfig)) {
            props.put(SaslConfigs.SASL_JAAS_CONFIG, jaasConfig);
        }

        return props;
    }

    private Map<String, Object> getProducerProperties() {

        if (producerProperties == null) {
            producerProperties = createProducerProperties(bootstrapServers, securityProtocol, saslMechanism, jaasConfig);
        }

        return producerProperties;
    }


    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> props = getProducerProperties();
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        return new DefaultKafkaProducerFactory<>(getProducerProperties());
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }
}
