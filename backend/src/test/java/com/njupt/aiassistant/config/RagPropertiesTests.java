package com.njupt.aiassistant.config;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class RagPropertiesTests {

    @Test
    void usesSeparateDefaultTimeoutsForSearchAndIndexing() {
        var properties = new RagProperties(null, null, null, null, null);

        assertThat(properties.connectTimeout()).isEqualTo(Duration.ofSeconds(2));
        assertThat(properties.readTimeout()).isEqualTo(Duration.ofSeconds(10));
        assertThat(properties.indexReadTimeout()).isEqualTo(Duration.ofSeconds(60));
        assertThat(properties.indexingEnabled()).isTrue();
    }

    @Test
    void preservesConfiguredSearchAndIndexTimeouts() {
        var properties = new RagProperties(
                URI.create("http://knowledge-service:8090"),
                Duration.ofSeconds(3),
                Duration.ofSeconds(8),
                Duration.ofSeconds(75),
                false
        );

        assertThat(properties.readTimeout()).isEqualTo(Duration.ofSeconds(8));
        assertThat(properties.indexReadTimeout()).isEqualTo(Duration.ofSeconds(75));
        assertThat(properties.indexingEnabled()).isFalse();
    }
}
