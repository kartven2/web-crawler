package com.webcrawler.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ configuration for the URL Frontier.
 *
 * Design:
 *  - One topic exchange (crawler.exchange) routes URLs by domain.
 *  - A default queue (crawler.url.default) handles domains without
 *    a dedicated queue.
 *  - Per-domain queues are created dynamically in UrlFrontierService
 *    when a new host is discovered (routing key: crawler.domain.<host>).
 *  - Topic exchange pattern: "crawler.domain.#" binds to any domain queue.
 *
 * This mirrors the README requirement:
 *   "Queue router: each queue only contains URLs from the same host."
 */
@Configuration
public class RabbitMQConfig {

    @Value("${crawler.rabbitmq.exchange}")
    private String exchangeName;

    @Value("${crawler.rabbitmq.default-queue}")
    private String defaultQueueName;

    /** Topic exchange allows pattern-based routing (one exchange, many queues). */
    @Bean
    public TopicExchange crawlerExchange() {
        return new TopicExchange(exchangeName, true, false);
    }

    /** Default catch-all queue for any domain without a dedicated queue. */
    @Bean
    public Queue defaultCrawlerQueue() {
        return QueueBuilder.durable(defaultQueueName).build();
    }

    /** Bind the default queue to the exchange with a wildcard routing key. */
    @Bean
    public Binding defaultBinding(Queue defaultCrawlerQueue, TopicExchange crawlerExchange) {
        return BindingBuilder.bind(defaultCrawlerQueue)
                .to(crawlerExchange)
                .with("crawler.domain.#");
    }

    /** JSON message converter so CrawlTask objects travel as JSON, not Java serialization. */
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter());
        return template;
    }

    /** RabbitAdmin auto-declares queues/exchanges on connection. */
    @Bean
    public RabbitAdmin rabbitAdmin(ConnectionFactory connectionFactory) {
        return new RabbitAdmin(connectionFactory);
    }

    /**
     * Listener container factory used by CrawlerWorker @RabbitListener.
     * concurrency = worker-count (set dynamically via CrawlerProperties).
     */
    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            CrawlerProperties props) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jsonMessageConverter());
        factory.setConcurrentConsumers(props.getWorkerCount());
        factory.setMaxConcurrentConsumers(props.getWorkerCount() * 2);
        factory.setPrefetchCount(1); // pull one message at a time per worker (fairness)
        return factory;
    }
}
