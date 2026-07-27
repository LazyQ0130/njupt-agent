package com.njupt.aiassistant.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class RagClientConfig {

    @Bean("ragRestClient")
    public RestClient ragRestClient(
            RestClient.Builder builder,
            RagProperties properties
    ) {
        return buildClient(builder, properties, properties.readTimeout());
    }

    @Bean("ragIndexRestClient")
    public RestClient ragIndexRestClient(
            RestClient.Builder builder,
            RagProperties properties
    ) {
        return buildClient(builder, properties, properties.indexReadTimeout());
    }

    private RestClient buildClient(
            RestClient.Builder builder,
            RagProperties properties,
            Duration readTimeout
    ) {
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(readTimeout);
        return builder
                .clone()
                .baseUrl(properties.baseUrl().toString())
                .requestFactory(requestFactory)
                .build();
    }
}
