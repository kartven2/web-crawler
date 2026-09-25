package com.webcrawler.config;

import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.util.Timeout;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Configures Apache HttpClient 5 with a connection pool.
 *
 * Key design decisions:
 *  - PoolingHttpClientConnectionManager: limits connections per host and in total,
 *    preventing resource exhaustion when crawling many domains in parallel.
 *  - Configurable timeouts (connectTimeout, responseTimeout) guard against
 *    slow or unresponsive servers hanging worker threads.
 *  - Follow redirects by default (HttpClients.custom() default).
 */
@Configuration
public class HttpClientConfig {

    private final CrawlerProperties props;

    public HttpClientConfig(CrawlerProperties props) {
        this.props = props;
    }

    @Bean
    public CloseableHttpClient httpClient() {
        // Connection pool – central to horizontal scalability
        PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager();
        cm.setMaxTotal(props.getConnectionPoolSize());
        cm.setDefaultMaxPerRoute(5); // max connections per single host

        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(Timeout.of(props.getConnectTimeoutMs(), TimeUnit.MILLISECONDS))
                .setResponseTimeout(Timeout.of(props.getSocketTimeoutMs(), TimeUnit.MILLISECONDS))
                .build();

        return HttpClients.custom()
                .setConnectionManager(cm)
                .setDefaultRequestConfig(requestConfig)
                .setUserAgent(props.getUserAgent())
                .disableRedirectHandling() // We handle redirects manually so we can track them
                .build();
    }
}
