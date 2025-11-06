package com.example.queuectl.shell;

import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStyle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.shell.jline.PromptProvider;

@Configuration
public class PromptConfig {
    @Bean
    public PromptProvider promptProvider() {
        return () -> new AttributedString("queuectl> ",
                AttributedStyle.DEFAULT.foreground(AttributedStyle.CYAN));
    }
}
