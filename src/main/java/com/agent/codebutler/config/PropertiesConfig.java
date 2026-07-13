package com.agent.codebutler.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 启用 @ConfigurationProperties 绑定
 * <p>
 * 集中注册所有自定义配置属性类，替代散落在各 Service 中的 @Value 注入。
 */
@Configuration
@EnableConfigurationProperties({
        AgentScopeProperties.class,
        EmbeddingProperties.class
})
public class PropertiesConfig {
}
