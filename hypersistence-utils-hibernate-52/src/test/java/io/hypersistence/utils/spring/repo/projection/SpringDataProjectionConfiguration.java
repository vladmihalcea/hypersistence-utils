package io.hypersistence.utils.spring.repo.projection;

import io.hypersistence.utils.hibernate.type.array.ListArrayType;
import io.hypersistence.utils.spring.config.AbstractSpringDataJPAConfiguration;
import io.hypersistence.utils.spring.domain.Post;
import io.hypersistence.utils.spring.repository.BaseJpaRepositoryImpl;
import org.hibernate.boot.spi.MetadataBuilderContributor;
import org.hibernate.jpa.boot.internal.EntityManagerFactoryBuilderImpl;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.util.Properties;

/**
 * @author Vlad Mihalcea
 */
@ComponentScan(
    basePackages = {
        "io.hypersistence.utils.spring.repo.projection",
    }
)
@EnableJpaRepositories(
    value = "io.hypersistence.utils.spring.repo.projection",
    repositoryBaseClass = BaseJpaRepositoryImpl.class
)
public class SpringDataProjectionConfiguration extends AbstractSpringDataJPAConfiguration {

    @Override
    protected String packageToScan() {
        return Post.class.getPackage().getName();
    }

    @Override
    protected void additionalProperties(Properties properties) {
        properties.put("hibernate.jdbc.batch_size", "100");
        properties.put("hibernate.order_inserts", "true");

        properties.put(
            EntityManagerFactoryBuilderImpl.METADATA_BUILDER_CONTRIBUTOR,
            (MetadataBuilderContributor) metadataBuilder -> metadataBuilder.applyBasicType(
                ListArrayType.INSTANCE
            )
        );
    }
}
