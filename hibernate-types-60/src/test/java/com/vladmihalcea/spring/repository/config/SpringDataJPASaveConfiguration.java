package com.vladmihalcea.spring.repository.config;

import com.vladmihalcea.spring.base.config.SpringDataJPABaseConfiguration;
import com.vladmihalcea.spring.repository.domain.Post;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.util.Properties;

/**
 *
 * @author Vlad Mihalcea
 */
@ComponentScan(
    basePackages = {
        "com.vladmihalcea.spring.repository",
    }
)
@EnableJpaRepositories("com.vladmihalcea.spring.repository")
public class SpringDataJPASaveConfiguration extends SpringDataJPABaseConfiguration {

    @Override
    protected String packageToScan() {
        return Post.class.getPackage().getName();
    }

    @Override
    protected void additionalProperties(Properties properties) {
        properties.put("hibernate.jdbc.batch_size", "100");
        properties.put("hibernate.order_inserts", "true");
    }
}
