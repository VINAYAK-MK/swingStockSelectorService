package com.swingStockSelector.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "python.service")
public class PythonClientConfig {

    private String baseUrl;
    private String username;
    private String password;

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
