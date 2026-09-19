package com.jurisflow.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
@ConfigurationProperties(prefix = "app.datajud")
@Getter
@Setter
public class DataJudConfig {

    private String baseUrl;
    private String apiKey;
    private int rateLimitSeconds = 60;

    public boolean isConfigured() {
        return baseUrl != null && !baseUrl.isBlank() && apiKey != null && !apiKey.isBlank();
    }

    @Bean("datajudRestTemplate")
    public RestTemplate datajudRestTemplate() {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(15_000); // 15s
        factory.setReadTimeout(45_000);    // 45s (API externa do governo pode ser lenta)
        return new RestTemplate(factory);
    }
}
