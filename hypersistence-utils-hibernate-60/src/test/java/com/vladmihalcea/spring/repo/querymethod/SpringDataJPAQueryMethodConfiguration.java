package com.vladmihalcea.spring.repo.querymethod;

import com.vladmihalcea.spring.config.AbstractSpringDataJPAConfiguration;
import com.vladmihalcea.spring.repo.querymethod.domain.Post;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.util.Properties;

/**
 *
 * @author Vlad Mihalcea
 */
@ComponentScan(
    basePackages = {
        "com.vladmihalcea.spring.repo.querymethod",
    }
)
@EnableJpaRepositories(
    value = {
        "com.vladmihalcea.spring.repo.querymethod"
    }
)
public class SpringDataJPAQueryMethodConfiguration extends AbstractSpringDataJPAConfiguration {

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
