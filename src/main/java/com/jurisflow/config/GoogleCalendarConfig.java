package com.jurisflow.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.util.Base64;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "app.google-calendar")
public class GoogleCalendarConfig {

    private String clientId;
    private String clientSecret;
    private String redirectUri;
    private String tokenEncryptionKey;

    public boolean isConfigured() {
        if (clientId == null || clientId.isBlank() ||
            clientSecret == null || clientSecret.isBlank() ||
            tokenEncryptionKey == null || tokenEncryptionKey.isBlank()) {
            return false;
        }

        try {
            byte[] decoded = Base64.getDecoder().decode(tokenEncryptionKey.trim());
            return decoded.length >= 32;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    @Bean
    public RestTemplate restTemplate() {
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000); // 10s
        factory.setReadTimeout(15_000);    // 15s
        return new RestTemplate(factory);
    }
}
