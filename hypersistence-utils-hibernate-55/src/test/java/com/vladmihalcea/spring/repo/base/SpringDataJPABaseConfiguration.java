package com.vladmihalcea.spring.repo.base;

import com.vladmihalcea.spring.config.AbstractSpringDataJPAConfiguration;
import com.vladmihalcea.spring.domain.Post;
import com.vladmihalcea.spring.repository.BaseJpaRepositoryImpl;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.util.Properties;

/**
 *
 * @author Vlad Mihalcea
 */
@ComponentScan(
    basePackages = {
        "com.vladmihalcea.spring.repo.base",
    }
)
@EnableJpaRepositories(
    value = "com.vladmihalcea.spring.repo.base",
    repositoryBaseClass = BaseJpaRepositoryImpl.class
)
public class SpringDataJPABaseConfiguration extends AbstractSpringDataJPAConfiguration {

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
