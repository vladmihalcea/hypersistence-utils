package com.vladmihalcea.spring.repo.hibernate;

import com.vladmihalcea.spring.config.AbstractSpringDataJPAConfiguration;
import com.vladmihalcea.spring.domain.Post;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.util.Properties;

/**
 *
 * @author Vlad Mihalcea
 */
@ComponentScan(
    basePackages = {
        "com.vladmihalcea.spring.repo.hibernate",
    }
)
@EnableJpaRepositories(
    value = {
        "com.vladmihalcea.spring.repository",
        "com.vladmihalcea.spring.repo.hibernate"
    }
)
public class SpringDataJPAHibernateConfiguration extends AbstractSpringDataJPAConfiguration {

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
