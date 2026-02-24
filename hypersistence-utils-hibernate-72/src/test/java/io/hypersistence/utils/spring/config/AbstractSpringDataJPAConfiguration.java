package io.hypersistence.utils.spring.config;

import io.hypersistence.utils.logging.InlineQueryLogEntryCreator;
import io.hypersistence.utils.test.providers.DataSourceProvider;
import io.hypersistence.utils.hibernate.util.providers.PostgreSQLDataSourceProvider;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManagerFactory;
import net.ttddyy.dsproxy.listener.logging.SLF4JQueryLoggingListener;
import net.ttddyy.dsproxy.support.ProxyDataSourceBuilder;
import org.hibernate.jpa.HibernatePersistenceProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Primary;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.JpaVendorAdapter;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.util.Properties;

/**
 *
 * @author Vlad Mihalcea
 */
@Configuration
@EnableTransactionManagement
@EnableAspectJAutoProxy
public abstract class AbstractSpringDataJPAConfiguration {

    private DataSourceProvider dataSourceProvider = new PostgreSQLDataSourceProvider();

    @Bean
    public static PropertySourcesPlaceholderConfigurer propertySources() {
        return new PropertySourcesPlaceholderConfigurer();
    }

    @Bean(destroyMethod = "close")
    public HikariDataSource actualDataSource() {
        Properties properties = new Properties();
        properties.setProperty("maximumPoolSize", String.valueOf(3));
        HikariConfig hikariConfig = new HikariConfig(properties);
        hikariConfig.setAutoCommit(false);
        hikariConfig.setDataSource(dataSourceProvider.dataSource());
        return new HikariDataSource(hikariConfig);
    }

    @Bean
    @Primary
    public DataSource dataSource() {
        SLF4JQueryLoggingListener loggingListener = new SLF4JQueryLoggingListener();
        loggingListener.setQueryLogEntryCreator(new InlineQueryLogEntryCreator());
        DataSource dataSource = ProxyDataSourceBuilder
                .create(actualDataSource())
                .name("DATA_SOURCE_PROXY")
                .listener(loggingListener)
                .build();
        return dataSource;
    }

    @Bean
    public LocalContainerEntityManagerFactoryBean entityManagerFactory() {
        LocalContainerEntityManagerFactoryBean entityManagerFactoryBean = new LocalContainerEntityManagerFactoryBean();
        entityManagerFactoryBean.setPersistenceUnitName(getClass().getSimpleName());
        entityManagerFactoryBean.setPersistenceProvider(new HibernatePersistenceProvider());
        entityManagerFactoryBean.setDataSource(dataSource());
        entityManagerFactoryBean.setPackagesToScan(packagesToScan());

        JpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
        entityManagerFactoryBean.setJpaVendorAdapter(vendorAdapter);
        entityManagerFactoryBean.setJpaProperties(properties());
        return entityManagerFactoryBean;
    }

    @Bean
    public JpaTransactionManager transactionManager(EntityManagerFactory entityManagerFactory){
        JpaTransactionManager transactionManager = new JpaTransactionManager();
        transactionManager.setEntityManagerFactory(entityManagerFactory);
        return transactionManager;
    }

    @Bean
    public TransactionTemplate transactionTemplate(EntityManagerFactory entityManagerFactory) {
        return new TransactionTemplate(transactionManager(entityManagerFactory));
    }

    @Bean
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    protected Properties properties() {
        Properties properties = new Properties();
        properties.setProperty("hibernate.dialect", dataSourceProvider.hibernateDialect());
        properties.setProperty("hibernate.hbm2ddl.auto", "create-drop");
        additionalProperties(properties);
        return properties;
    }

    protected void additionalProperties(Properties properties) {
    }

    protected String[] packagesToScan() {
        return new String[]{
            packageToScan()
        };
    }

    protected String packageToScan() {
        return "io.hypersistence.utils.spring.domain";
    }
}
