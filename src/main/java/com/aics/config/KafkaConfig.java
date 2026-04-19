package com.aics.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
@EnableKafka
public class KafkaConfig {

    private final AppProperties props;

    public KafkaConfig(AppProperties props) {
        this.props = props;
    }

    @Bean
    public NewTopic inboundTopic() {
        return TopicBuilder.name(props.getKafkaTopics().getInbound())
                .partitions(6).replicas(1).build();
    }

    @Bean
    public NewTopic inboundDlq() {
        return TopicBuilder.name(props.getKafkaTopics().getInboundDlq())
                .partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic triggeredTopic() {
        return TopicBuilder.name(props.getKafkaTopics().getTriggered())
                .partitions(6).replicas(1).build();
    }

    @Bean
    public NewTopic handoffTopic() {
        return TopicBuilder.name(props.getKafkaTopics().getHandoff())
                .partitions(3).replicas(1).build();
    }
}
