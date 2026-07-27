package com.njupt.aiassistant;

import com.njupt.aiassistant.config.AiProperties;
import com.njupt.aiassistant.config.AiConfigSecurityProperties;
import com.njupt.aiassistant.config.AdminSecurityProperties;
import com.njupt.aiassistant.config.AnonymousSessionProperties;
import com.njupt.aiassistant.config.AppProperties;
import com.njupt.aiassistant.config.DeepSeekProperties;
import com.njupt.aiassistant.config.CrawlerProperties;
import com.njupt.aiassistant.config.ConversationProperties;
import com.njupt.aiassistant.config.JwtProperties;
import com.njupt.aiassistant.config.RagAnswerProperties;
import com.njupt.aiassistant.config.RagProperties;
import com.njupt.aiassistant.config.RateLimitProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({
        AiProperties.class,
        AiConfigSecurityProperties.class,
        AdminSecurityProperties.class,
        AnonymousSessionProperties.class,
        AppProperties.class,
        DeepSeekProperties.class,
        CrawlerProperties.class,
        ConversationProperties.class,
        JwtProperties.class,
        RagAnswerProperties.class,
        RagProperties.class,
        RateLimitProperties.class
})
public class AiAssistantApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiAssistantApplication.class, args);
    }
}
