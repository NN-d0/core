package com.radio.core.config;

import com.radio.core.service.AiHealthService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Core 启动后输出一次 AI 健康摘要，方便答辩前快速排障
 */
@Component
@RequiredArgsConstructor
public class AiHealthStartupLogger implements ApplicationRunner {

    private final AiHealthService aiHealthService;

    @Override
    public void run(ApplicationArguments args) {
        aiHealthService.getAiHealthStatus(true);
    }
}
