package io.hypersistence.utils.spring.repo.hibernate;

import io.hypersistence.utils.spring.config.AbstractSpringDataJPAConfiguration;
import io.hypersistence.utils.spring.domain.Post;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.util.Properties;

/**
 *
 * @author Vlad Mihalcea
 */
@ComponentScan(
    basePackages = {
        "io.hypersistence.utils.spring.repo.hibernate",
    }
)
@EnableJpaRepositories(
    value = {
        "io.hypersistence.utils.spring.repository",
        "io.hypersistence.utils.spring.repo.hibernate"
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
