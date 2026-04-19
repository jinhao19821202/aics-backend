package com.aics.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.util.concurrent.TimeUnit;

@Configuration
public class WebClientConfig {

    @Bean
    public WebClient dashScopeWebClient(AppProperties props) {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, props.getDashscope().getConnectTimeoutMs())
                .doOnConnected(conn -> conn.addHandlerLast(
                        new ReadTimeoutHandler(props.getDashscope().getReadTimeoutMs(), TimeUnit.MILLISECONDS)));

        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(c -> c.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .build();

        return WebClient.builder()
                .baseUrl(props.getDashscope().getBaseUrl())
                .defaultHeader("Authorization", "Bearer " + props.getDashscope().getApiKey())
                .defaultHeader("Content-Type", "application/json")
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .exchangeStrategies(strategies)
                .build();
    }

    @Bean
    public WebClient wecomWebClient(AppProperties props) {
        return WebClient.builder()
                .baseUrl(props.getWecom().getApiBase())
                .defaultHeader("Content-Type", "application/json")
                .build();
    }
}
