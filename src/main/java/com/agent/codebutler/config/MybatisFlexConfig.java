package com.agent.codebutler.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * MyBatis-Flex Mapper 扫描配置
 * 在 test profile 下禁用，避免加载需要 DataSource 的 Mapper Bean
 */
@Configuration
@MapperScan("com.agent.codebutler.mapper")
@Profile("!test")
public class MybatisFlexConfig {
}
