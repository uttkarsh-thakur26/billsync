package com.uttkarsh.billsync.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI billSyncOpenApi() {
        return new OpenAPI().info(new Info()
                .title("BillSync API")
                .version("v1")
                .description("Group expense splitter: exact-paisa splits and greedy debt simplification."));
    }
}
