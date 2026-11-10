package com.example.MedRational.Config;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.env.PropertySourcesPropertyResolver;
import org.springframework.core.io.ClassPathResource;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;

// The k6 load tests (loadtest/RESULTS.md) found that 5 DB connections made requests queue from
// about 500 active students, whatever the CPU, and that 12 kept p95 at 155 ms with 800.
class DatabasePoolSizeTest {

    private static final String POOL_SIZE = "spring.datasource.hikari.maximum-pool-size";

    private static String poolSize(String profile, Properties environment) {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application-" + profile + ".yaml"));
        MutablePropertySources sources = new MutablePropertySources();
        sources.addLast(new PropertiesPropertySource("environment", environment));
        sources.addLast(new PropertiesPropertySource(profile, yaml.getObject()));
        return new PropertySourcesPropertyResolver(sources).getProperty(POOL_SIZE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"uat", "prod"})
    void defaultsToTwelveConnections(String profile) {
        assertEquals("12", poolSize(profile, new Properties()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"uat", "prod"})
    void dbPoolSizeOverridesIt(String profile) {
        Properties environment = new Properties();
        environment.setProperty("DB_POOL_SIZE", "20");
        assertEquals("20", poolSize(profile, environment));
    }
}
