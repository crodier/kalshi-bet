package com.betfanatics.exchange.order.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.config.SaslConfigs;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(classes = KafkaProducerConfig.class)
@ActiveProfiles("test")
class KafkaProducerConfigTestIT {

    @Autowired
    private KafkaProducerConfig kafkaProducerConfig;

    @Autowired
    private ProducerFactory<String, Object> producerFactory;

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    static AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();

    static {
        context.getEnvironment().setActiveProfiles("test");

        // Setup spring properties that will be used in the test
        TestPropertyValues.of(
            "spring.kafka.bootstrap-servers=localhost:9092",
            "spring.kafka.properties.security.protocol=SASL_PLAINTEXT",
            "spring.kafka.properties.sasl.mechanism=PLAIN",
            "spring.kafka.properties.sasl.jaas.config=testJaasConfig"
        ).applyTo(context);
        context.register(KafkaProducerConfig.class);
        context.refresh();
    }

    @Test
    void testProducerFactoryBeanShouldBeCreated() {
        assertThat(producerFactory).isNotNull();
        assertThat(producerFactory).isInstanceOf(DefaultKafkaProducerFactory.class);
    }

    @Test
    void testKafkaTemplateBeanShouldBeCreated() {
        assertThat(kafkaTemplate).isNotNull();
    }

    @Test
    void testProducerPropertiesShouldBeSetCorrectly() {
        Map<String, Object> props = kafkaProducerConfig.producerFactory().getConfigurationProperties();
        assertThat(props.get(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG)).isEqualTo(StringSerializer.class);
        assertThat(props.get(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG)).isEqualTo(JsonSerializer.class);
    }

    @Test
    void testProducerPropertiesWithSecuritySettings() {
        KafkaProducerConfig configWithSecurity = context.getBean(KafkaProducerConfig.class);
        Map<String, Object> props = configWithSecurity.producerFactory().getConfigurationProperties();

        assertThat(props.get(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG)).isEqualTo("SASL_PLAINTEXT");
        assertThat(props.get(SaslConfigs.SASL_MECHANISM)).isEqualTo("PLAIN");
        assertThat(props.get(SaslConfigs.SASL_JAAS_CONFIG)).isEqualTo("testJaasConfig");
        context.close();
    }
}
