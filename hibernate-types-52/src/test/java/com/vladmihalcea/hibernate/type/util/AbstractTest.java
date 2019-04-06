package com.vladmihalcea.hibernate.type.util;

import com.vladmihalcea.hibernate.type.util.providers.DataSourceProvider;
import com.vladmihalcea.hibernate.type.util.providers.HSQLDBDataSourceProvider;
import com.vladmihalcea.hibernate.type.util.transaction.*;
import org.hibernate.Interceptor;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.boot.MetadataBuilder;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.SessionFactoryBuilder;
import org.hibernate.boot.model.TypeContributions;
import org.hibernate.boot.model.TypeContributor;
import org.hibernate.boot.model.naming.ImplicitNamingStrategyLegacyJpaImpl;
import org.hibernate.boot.registry.BootstrapServiceRegistry;
import org.hibernate.boot.registry.BootstrapServiceRegistryBuilder;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.boot.spi.MetadataImplementor;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.cfg.Configuration;
import org.hibernate.integrator.spi.Integrator;
import org.hibernate.jpa.boot.internal.EntityManagerFactoryBuilderImpl;
import org.hibernate.jpa.boot.internal.PersistenceUnitInfoDescriptor;
import org.hibernate.jpa.boot.spi.IntegratorProvider;
import org.hibernate.jpa.boot.spi.TypeContributorList;
import org.hibernate.service.ServiceRegistry;
import org.hibernate.type.BasicType;
import org.hibernate.type.Type;
import org.hibernate.usertype.CompositeUserType;
import org.hibernate.usertype.UserType;
import org.junit.After;
import org.junit.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import javax.persistence.spi.PersistenceUnitInfo;
import javax.sql.DataSource;
import java.io.Closeable;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public abstract class AbstractTest {

    static {
        Thread.currentThread().setName("Alice");
    }

    protected final ExecutorService executorService = Executors.newSingleThreadExecutor(r -> {
        Thread bob = new Thread(r);
        bob.setName("Bob");
        return bob;
    });

    protected final Logger LOGGER = LoggerFactory.getLogger(getClass());

    private EntityManagerFactory emf;

    private SessionFactory sf;

    private List<Closeable> closeables = new ArrayList<>();

    @Before
    public void init() {
        if(nativeHibernateSessionFactoryBootstrap()) {
            sf = newSessionFactory();
        } else {
            emf = newEntityManagerFactory();
        }
        afterInit();
    }

    protected void afterInit() {

    }

    @After
    public void destroy() {
        if (nativeHibernateSessionFactoryBootstrap()) {
            sf.close();
        } else {
            emf.close();
        }
        for (Closeable closeable : closeables) {
            try {
                closeable.close();
            } catch (IOException e) {
                LOGGER.error("Failure", e);
            }
        }
        closeables.clear();
    }

    public EntityManagerFactory entityManagerFactory() {
        return nativeHibernateSessionFactoryBootstrap() ? sf : emf;
    }

    public SessionFactory sessionFactory() {
        return nativeHibernateSessionFactoryBootstrap() ? sf : entityManagerFactory().unwrap(SessionFactory.class);
    }

    protected boolean nativeHibernateSessionFactoryBootstrap() {
        return false;
    }

    protected abstract Class<?>[] entities();

    protected List<String> entityClassNames() {
        return Arrays.asList(entities()).stream().map(Class::getName).collect(Collectors.toList());
    }

    protected String[] packages() {
        return null;
    }

    protected String[] resources() {
        return null;
    }

    protected Interceptor interceptor() {
        return null;
    }

    private SessionFactory newSessionFactory() {
        final BootstrapServiceRegistryBuilder bsrb = new BootstrapServiceRegistryBuilder()
                .enableAutoClose();

        Integrator integrator = integrator();
        if (integrator != null) {
            bsrb.applyIntegrator(integrator);
        }

        final BootstrapServiceRegistry bsr = bsrb.build();

        final StandardServiceRegistry serviceRegistry = new StandardServiceRegistryBuilder(bsr)
                .applySettings(properties())
                .build();

        final MetadataSources metadataSources = new MetadataSources(serviceRegistry);

        for (Class annotatedClass : entities()) {
            metadataSources.addAnnotatedClass(annotatedClass);
        }

        String[] packages = packages();
        if (packages != null) {
            for (String annotatedPackage : packages) {
                metadataSources.addPackage(annotatedPackage);
            }
        }

        String[] resources = resources();
        if (resources != null) {
            for (String resource : resources) {
                metadataSources.addResource(resource);
            }
        }

        final MetadataBuilder metadataBuilder = metadataSources.getMetadataBuilder();
        metadataBuilder.enableNewIdentifierGeneratorSupport(true);
        metadataBuilder.applyImplicitNamingStrategy(ImplicitNamingStrategyLegacyJpaImpl.INSTANCE);

        final List<Type> additionalTypes = additionalTypes();
        if (additionalTypes != null) {
            additionalTypes.stream().forEach(type -> {
                metadataBuilder.applyTypes((typeContributions, serviceRegistry1) -> {
                    if(type instanceof BasicType) {
                        typeContributions.contributeType((BasicType) type);
                    } else if (type instanceof UserType ){
                        typeContributions.contributeType((UserType) type);
                    } else if (type instanceof CompositeUserType) {
                        typeContributions.contributeType((CompositeUserType) type);
                    }
                });
            });
        }

        MetadataImplementor metadata = (MetadataImplementor) metadataBuilder.build();

        final SessionFactoryBuilder sfb = metadata.getSessionFactoryBuilder();
        Interceptor interceptor = interceptor();
        if (interceptor != null) {
            sfb.applyInterceptor(interceptor);
        }

        return sfb.build();
    }

    private SessionFactory newLegacySessionFactory() {
        Properties properties = properties();
        Configuration configuration = new Configuration().addProperties(properties);
        for (Class<?> entityClass : entities()) {
            configuration.addAnnotatedClass(entityClass);
        }
        String[] packages = packages();
        if (packages != null) {
            for (String scannedPackage : packages) {
                configuration.addPackage(scannedPackage);
            }
        }
        String[] resources = resources();
        if (resources != null) {
            for (String resource : resources) {
                configuration.addResource(resource);
            }
        }
        Interceptor interceptor = interceptor();
        if (interceptor != null) {
            configuration.setInterceptor(interceptor);
        }

        final List<Type> additionalTypes = additionalTypes();
        if (additionalTypes != null) {
            configuration.registerTypeContributor((typeContributions, serviceRegistry) -> {
                additionalTypes.stream().forEach(type -> {
                    if (type instanceof BasicType) {
                        typeContributions.contributeType((BasicType) type);
                    } else if (type instanceof UserType) {
                        typeContributions.contributeType((UserType) type);
                    } else if (type instanceof CompositeUserType) {
                        typeContributions.contributeType((CompositeUserType) type);
                    }
                });
            });
        }
        return configuration.buildSessionFactory(
                new StandardServiceRegistryBuilder()
                        .applySettings(properties)
                        .build()
        );
    }

    protected EntityManagerFactory newEntityManagerFactory() {
        PersistenceUnitInfo persistenceUnitInfo = persistenceUnitInfo(getClass().getSimpleName());
        Map<String, Object> configuration = new HashMap<>();
        configuration.put(AvailableSettings.INTERCEPTOR, interceptor());
        Integrator integrator = integrator();
        if (integrator != null) {
            configuration.put("hibernate.integrator_provider", (IntegratorProvider) () -> Collections.singletonList(integrator));
        }

        final List<Type> additionalTypes = additionalTypes();
        if (additionalTypes != null) {
            configuration.put("hibernate.type_contributors", (TypeContributorList) () -> {
                List<TypeContributor> typeContributors = new ArrayList<>();

                for (Type additionalType : additionalTypes) {
                    if (additionalType instanceof BasicType) {
                        typeContributors.add((typeContributions, serviceRegistry) -> typeContributions.contributeType((BasicType) additionalType));


                    } else if (additionalType instanceof UserType) {
                        typeContributors.add((typeContributions, serviceRegistry) -> typeContributions.contributeType((UserType) additionalType));
                    } else if (additionalType instanceof CompositeUserType) {
                        typeContributors.add((typeContributions, serviceRegistry) -> typeContributions.contributeType((CompositeUserType) additionalType));
                    }
                }
                return typeContributors;
            });
        }

        EntityManagerFactoryBuilderImpl entityManagerFactoryBuilder = new EntityManagerFactoryBuilderImpl(
                new PersistenceUnitInfoDescriptor(persistenceUnitInfo), configuration
        );
        return entityManagerFactoryBuilder.build();
    }

    protected Integrator integrator() {
        return null;
    }

    protected PersistenceUnitInfoImpl persistenceUnitInfo(String name) {
        PersistenceUnitInfoImpl persistenceUnitInfo = new PersistenceUnitInfoImpl(
                name, entityClassNames(), properties()
        );
        String[] resources = resources();
        if (resources != null) {
            persistenceUnitInfo.getMappingFileNames().addAll(Arrays.asList(resources));
        }
        return persistenceUnitInfo;
    }

    protected Properties properties() {
        Properties properties = new Properties();
        properties.put("hibernate.dialect", dataSourceProvider().hibernateDialect());
        //log settings
        properties.put("hibernate.hbm2ddl.auto", "create-drop");
        //data source settings
        DataSource dataSource = newDataSource();
        if (dataSource != null) {
            properties.put("hibernate.connection.datasource", dataSource);
        }
        properties.put("hibernate.generate_statistics", Boolean.TRUE.toString());
        //properties.put("hibernate.ejb.metamodel.population", "disabled");
        additionalProperties(properties);
        return properties;
    }

    protected void additionalProperties(Properties properties) {

    }

    protected DataSourceProxyType dataSourceProxyType() {
        return DataSourceProxyType.DATA_SOURCE_PROXY;
    }

    protected DataSource newDataSource() {
        DataSource dataSource =
                proxyDataSource()
                        ? dataSourceProxyType().dataSource(dataSourceProvider().dataSource())
                        : dataSourceProvider().dataSource();
        return dataSource;
    }

    protected boolean proxyDataSource() {
        return true;
    }

    protected DataSourceProvider dataSourceProvider() {
        return new HSQLDBDataSourceProvider();
    }

    protected List<Type> additionalTypes() {
        return null;
    }

    protected <T> T doInHibernate(HibernateTransactionFunction<T> callable) {
        T result = null;
        Session session = null;
        Transaction txn = null;
        try {
            session = sessionFactory().openSession();
            callable.beforeTransactionCompletion();
            txn = session.beginTransaction();

            result = callable.apply(session);
            if (!txn.getRollbackOnly()) {
                txn.commit();
            } else {
                try {
                    txn.rollback();
                } catch (Exception e) {
                    LOGGER.error("Rollback failure", e);
                }
            }
        } catch (Throwable t) {
            if (txn != null && txn.isActive()) {
                try {
                    txn.rollback();
                } catch (Exception e) {
                    LOGGER.error("Rollback failure", e);
                }
            }
            throw t;
        } finally {
            callable.afterTransactionCompletion();
            if (session != null) {
                session.close();
            }
        }
        return result;
    }

    protected void doInHibernate(HibernateTransactionConsumer callable) {
        Session session = null;
        Transaction txn = null;
        try {
            session = sessionFactory().openSession();
            callable.beforeTransactionCompletion();
            txn = session.beginTransaction();

            callable.accept(session);
            if (!txn.getRollbackOnly()) {
                txn.commit();
            } else {
                try {
                    txn.rollback();
                } catch (Exception e) {
                    LOGGER.error("Rollback failure", e);
                }
            }
        } catch (Throwable t) {
            if (txn != null && txn.isActive()) {
                try {
                    txn.rollback();
                } catch (Exception e) {
                    LOGGER.error("Rollback failure", e);
                }
            }
            throw t;
        } finally {
            callable.afterTransactionCompletion();
            if (session != null) {
                session.close();
            }
        }
    }

    protected <T> T doInJPA(JPATransactionFunction<T> function) {
        T result = null;
        EntityManager entityManager = null;
        EntityTransaction txn = null;
        try {
            entityManager = entityManagerFactory().createEntityManager();
            function.beforeTransactionCompletion();
            txn = entityManager.getTransaction();
            txn.begin();
            result = function.apply(entityManager);
            if (!txn.getRollbackOnly()) {
                txn.commit();
            } else {
                try {
                    txn.rollback();
                } catch (Exception e) {
                    LOGGER.error("Rollback failure", e);
                }
            }
        } catch (Throwable t) {
            if (txn != null && txn.isActive()) {
                try {
                    txn.rollback();
                } catch (Exception e) {
                    LOGGER.error("Rollback failure", e);
                }
            }
            throw t;
        } finally {
            function.afterTransactionCompletion();
            if (entityManager != null) {
                entityManager.close();
            }
        }
        return result;
    }

    protected void doInJPA(JPATransactionVoidFunction function) {
        EntityManager entityManager = null;
        EntityTransaction txn = null;
        try {
            entityManager = entityManagerFactory().createEntityManager();
            function.beforeTransactionCompletion();
            txn = entityManager.getTransaction();
            txn.begin();
            function.accept(entityManager);
            if (!txn.getRollbackOnly()) {
                txn.commit();
            } else {
                try {
                    txn.rollback();
                } catch (Exception e) {
                    LOGGER.error("Rollback failure", e);
                }
            }
        } catch (Throwable t) {
            if (txn != null && txn.isActive()) {
                try {
                    txn.rollback();
                } catch (Exception e) {
                    LOGGER.error("Rollback failure", e);
                }
            }
            throw t;
        } finally {
            function.afterTransactionCompletion();
            if (entityManager != null) {
                entityManager.close();
            }
        }
    }

    protected <T> T doInJDBC(ConnectionCallable<T> callable) {
        AtomicReference<T> result = new AtomicReference<>();
        Session session = null;
        Transaction txn = null;
        try {
            session = sessionFactory().openSession();
            txn = session.beginTransaction();
            session.doWork(connection -> {
                result.set(callable.execute(connection));
            });
            if (!txn.getRollbackOnly()) {
                txn.commit();
            } else {
                try {
                    txn.rollback();
                } catch (Exception e) {
                    LOGGER.error("Rollback failure", e);
                }
            }
        } catch (Throwable t) {
            if (txn != null && txn.isActive()) {
                try {
                    txn.rollback();
                } catch (Exception e) {
                    LOGGER.error("Rollback failure", e);
                }
            }
            throw t;
        } finally {
            if (session != null) {
                session.close();
            }
        }
        return result.get();
    }

    protected void doInJDBC(ConnectionVoidCallable callable) {
        Session session = null;
        Transaction txn = null;
        try {
            session = sessionFactory().openSession();
            txn = session.beginTransaction();
            session.doWork(callable::execute);
            if (!txn.getRollbackOnly()) {
                txn.commit();
            } else {
                try {
                    txn.rollback();
                } catch (Exception e) {
                    LOGGER.error("Rollback failure", e);
                }
            }
        } catch (Throwable t) {
            if (txn != null && txn.isActive()) {
                try {
                    txn.rollback();
                } catch (Exception e) {
                    LOGGER.error("Rollback failure", e);
                }
            }
            throw t;
        } finally {
            if (session != null) {
                session.close();
            }
        }
    }
}
