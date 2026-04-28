package com.invest.infrastructure.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AssetUpdateRabbitMqConfig {

    public static final String EXCHANGE_NAME = "invest.assets.exchange";
    public static final String QUEUE_NAME = "invest.assets.update.queue";
    public static final String ROUTING_KEY = "asset.update";

    public static final String DLX_EXCHANGE_NAME = "invest.assets.dlx.exchange";
    public static final String DLQ_NAME = "invest.assets.update.dlq";

    @Bean
    DirectExchange assetUpdateExchange() {
        return new DirectExchange(EXCHANGE_NAME);
    }

    @Bean
    Queue assetUpdateQueue() {
        return QueueBuilder.durable(QUEUE_NAME)
                .withArgument("x-dead-letter-exchange", DLX_EXCHANGE_NAME)
                .build();
    }

    @Bean
    Binding assetUpdateBinding(Queue assetUpdateQueue, DirectExchange assetUpdateExchange) {
        return BindingBuilder.bind(assetUpdateQueue).to(assetUpdateExchange).with(ROUTING_KEY);
    }

    @Bean
    FanoutExchange assetUpdateDeadLetterExchange() {
        return new FanoutExchange(DLX_EXCHANGE_NAME);
    }

    @Bean
    Queue assetUpdateDeadLetterQueue() {
        return QueueBuilder.durable(DLQ_NAME).build();
    }

    @Bean
    Binding assetUpdateDeadLetterBinding(Queue assetUpdateDeadLetterQueue,
                                         FanoutExchange assetUpdateDeadLetterExchange) {
        return BindingBuilder.bind(assetUpdateDeadLetterQueue).to(assetUpdateDeadLetterExchange);
    }
}
