package com.bbebig.pushserver.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan(basePackages = {
		"com.bbebig.commonmodule.kafka.config",
		"com.bbebig.commonmodule.redis.config"
})
public class PushServerConfig {
}
