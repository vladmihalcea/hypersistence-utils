package io.hypersistence.utils.test;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.hypersistence.utils.common.ReflectionUtils;
import io.hypersistence.utils.logging.InlineQueryLogEntryCreator;
import io.hypersistence.utils.test.providers.DataSourceProvider;
import io.hypersistence.utils.test.providers.DataSourceProviderSupplier;
import io.hypersistence.utils.test.transaction.*;
import jakarta.persistence.*;
import jakarta.persistence.spi.ClassTransformer;
import jakarta.persistence.spi.PersistenceUnitInfo;
import jakarta.persistence.spi.PersistenceUnitTransactionType;
import net.ttddyy.dsproxy.listener.ChainListener;
import net.ttddyy.dsproxy.listener.DataSourceQueryCountListener;
import net.ttddyy.dsproxy.listener.logging.SLF4JQueryLoggingListener;
import net.ttddyy.dsproxy.support.ProxyDataSourceBuilder;
import org.hibernate.Interceptor;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.boot.MetadataBuilder;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.SessionFactoryBuilder;
import org.hibernate.boot.model.TypeContributor;
import org.hibernate.boot.model.naming.ImplicitNamingStrategyLegacyJpaImpl;
import org.hibernate.boot.registry.BootstrapServiceRegistry;
import org.hibernate.boot.registry.BootstrapServiceRegistryBuilder;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.boot.spi.MetadataImplementor;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.dialect.Database;
import org.hibernate.dialect.Dialect;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.integrator.spi.Integrator;
import org.hibernate.jpa.HibernatePersistenceProvider;
import org.hibernate.jpa.boot.internal.EntityManagerFactoryBuilderImpl;
import org.hibernate.jpa.boot.internal.PersistenceUnitInfoDescriptor;
import org.hibernate.jpa.boot.spi.IntegratorProvider;
import org.hibernate.jpa.boot.spi.TypeContributorList;
import org.hibernate.type.BasicType;
import org.hibernate.usertype.UserType;
import org.junit.After;
import org.junit.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.io.Closeable;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public abstract class AbstractHibernateTest {

    protected final Logger LOGGER = LoggerFactory.getLogger(getClass());

    private static final ServiceLoader<DataSourceProviderSupplier> DATA_SOURCE_PROVIDER_FACTORIES = ServiceLoader.load(DataSourceProviderSupplier.class);

    public static Map<Database, DataSourceProvider> dataSourceProviderMap;

    static {
        for (DataSourceProviderSupplier factory : DATA_SOURCE_PROVIDER_FACTORIES) {
            if (dataSourceProviderMap == null) {
                dataSourceProviderMap = factory.get();
            } else {
                throw new IllegalStateException("Multiple DataSourceProviderFactory instances found!");
            }
        }
        Thread.currentThread().setName("Alice");
    }

    protected final ExecutorService executorService = Executors.newSingleThreadExecutor(r -> {
        Thread bob = new Thread(r);
        bob.setName("Bob");
        return bob;
    });

    private Database database = Database.HSQL;

    private DataSource dataSource;

    private EntityManagerFactory emf;

    private SessionFactory sf;

    private List<Closeable> closeables = new ArrayList<>();

    @Before
    public void init() {
        beforeInit();
        if (nativeHibernateSessionFactoryBootstrap()) {
            sf = newSessionFactory();
        } else {
            emf = newEntityManagerFactory();
        }
        afterInit();
    }

    protected void beforeInit() {

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
        if (nativeHibernateSessionFactoryBootstrap()) {
            return sf;
        }
        EntityManagerFactory entityManagerFactory = entityManagerFactory();
        if (entityManagerFactory == null) {
            return null;
        }
        return entityManagerFactory.unwrap(SessionFactory.class);
    }

    protected boolean nativeHibernateSessionFactoryBootstrap() {
        return false;
    }

    protected Class<?>[] entities() {
        return new Class[]{};
    }

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
        final BootstrapServiceRegistryBuilder bsrb = new BootstrapServiceRegistryBuilder().enableAutoClose();

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
        metadataBuilder.applyImplicitNamingStrategy(ImplicitNamingStrategyLegacyJpaImpl.INSTANCE);

        final List<?> additionalTypes = additionalTypes();
        if (additionalTypes != null) {
            additionalTypes.stream().forEach(type -> {
                metadataBuilder.applyTypes((typeContributions, serviceRegistry1) -> {
                    if (type instanceof BasicType) {
                        typeContributions.contributeType((BasicType) type);
                    } else if (type instanceof UserType) {
                        typeContributions.contributeType((UserType) type);
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

    protected EntityManagerFactory newEntityManagerFactory() {
        PersistenceUnitInfo persistenceUnitInfo = persistenceUnitInfo(getClass().getSimpleName());
        Map<String, Object> configuration = new HashMap<>();
        configuration.put(AvailableSettings.INTERCEPTOR, interceptor());
        Integrator integrator = integrator();
        if (integrator != null) {
            configuration.put("hibernate.integrator_provider", (IntegratorProvider) () -> Collections.singletonList(integrator));
        }

        final List<?> additionalTypes = additionalTypes();
        if (additionalTypes != null) {
            configuration.put("hibernate.type_contributors", (TypeContributorList) () -> {
                List<TypeContributor> typeContributors = new ArrayList<>();

                for (Object additionalType : additionalTypes) {
                    if (additionalType instanceof BasicType) {
                        typeContributors.add((typeContributions, serviceRegistry) -> typeContributions.contributeType((BasicType) additionalType));


                    } else if (additionalType instanceof UserType) {
                        typeContributors.add((typeContributions, serviceRegistry) -> typeContributions.contributeType((UserType) additionalType));
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
        properties.put("hibernate.cache.ehcache.missing_cache_strategy", "create");
        additionalProperties(properties);
        return properties;
    }

    protected Dialect dialect() {
        SessionFactory sessionFactory = sessionFactory();
        return sessionFactory != null ?
            sessionFactory.unwrap(SessionFactoryImplementor.class).getJdbcServices().getDialect() :
            ReflectionUtils.newInstance(dataSourceProvider().hibernateDialect());
    }

    protected void additionalProperties(Properties properties) {

    }

    protected DataSource dataSource() {
        if (dataSource == null) {
            dataSource = newDataSource();
        }
        return dataSource;
    }

    protected DataSource newDataSource() {
        DataSource dataSource = dataSourceProvider().dataSource();
        if (proxyDataSource()) {
            ChainListener listener = new ChainListener();
            SLF4JQueryLoggingListener loggingListener = new SLF4JQueryLoggingListener();
            loggingListener.setQueryLogEntryCreator(new InlineQueryLogEntryCreator());
            listener.addListener(loggingListener);
            listener.addListener(new DataSourceQueryCountListener());
            dataSource = ProxyDataSourceBuilder
                .create(dataSource)
                .name("DATA_SOURCE_PROXY")
                .listener(listener)
                .build();
        }
        if (connectionPooling()) {
            HikariDataSource poolingDataSource = connectionPoolDataSource(dataSource);
            closeables.add(poolingDataSource::close);
            return poolingDataSource;
        }
        return dataSource;
    }

    protected boolean proxyDataSource() {
        return true;
    }

    protected HikariDataSource connectionPoolDataSource(DataSource dataSource) {
        return new HikariDataSource(hikariConfig(dataSource));
    }

    protected HikariConfig hikariConfig(DataSource dataSource) {
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setMaximumPoolSize(connectionPoolSize());
        hikariConfig.setDataSource(dataSource);
        return hikariConfig;
    }

    protected boolean connectionPooling() {
        return false;
    }

    protected int connectionPoolSize() {
        int cpuCores = Runtime.getRuntime().availableProcessors();
        return cpuCores * 4;
    }

    protected Database database() {
        return database;
    }

    public void setDatabase(Database database) {
        this.database = database;
    }

    protected DataSourceProvider dataSourceProvider() {
        return dataSourceProviderMap.get(database());
    }

    protected List<?> additionalTypes() {
        return null;
    }

    protected <T> T doInHibernate(SessionTransactionFunction<T> callable) {
        T result;
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

    protected void doInHibernate(SessionTransactionConsumer callable) {
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

    protected <T> T doInJPA(EntityManagerTransactionFunction<T> function) {
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

    protected void doInJPA(EntityManagerTransactionConsumer function) {
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

    protected <T> T doInJDBC(ConnectionTransactionFunction<T> function) {
        AtomicReference<T> result = new AtomicReference<>();
        Session session = null;
        Transaction txn = null;
        try {
            session = sessionFactory().openSession();
            function.beforeTransactionCompletion();
            txn = session.beginTransaction();
            session.doWork(connection -> {
                result.set(function.execute(connection));
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
            function.afterTransactionCompletion();
            if (session != null) {
                session.close();
            }
        }
        return result.get();
    }

    protected void doInJDBC(ConnectionTransactionConsumer function) {
        Session session = null;
        Transaction txn = null;
        try {
            session = sessionFactory().openSession();
            function.beforeTransactionCompletion();
            txn = session.beginTransaction();
            session.doWork(function::execute);
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
            if (session != null) {
                session.close();
            }
        }
    }

    protected void executeSync(VoidCallable callable) {
        try {
            List<Future<Void>> futures = executorService.invokeAll(
                Collections.singleton(callable)
            );
            for (Future<Void> future : futures) {
                future.get();
            }
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    protected <T> T executeSync(Callable<T> callable) {
        try {
            return executorService.submit(callable).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    protected void awaitOnLatch(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    protected void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static String stringValue(Object value) {
        return value.toString();
    }

    public static int intValue(Object number) {
        return ((Number) number).intValue();
    }

    public static long longValue(Object number) {
        if (number instanceof String) {
            return Long.parseLong((String) number);
        }
        return ((Number) number).longValue();
    }

    public static double doubleValue(Object number) {
        return ((Number) number).doubleValue();
    }

    public static URL urlValue(String url) {
        try {
            return url != null ? new URL(url) : null;
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public static LocalDateTime localDateTimeValue(Object value) {
        return (LocalDateTime) value;
    }

    public static class PersistenceUnitInfoImpl implements PersistenceUnitInfo {

        private final String persistenceUnitName;
        private final List<String> managedClassNames;
        private final List<String> mappingFileNames = new ArrayList<>();
        private final Properties properties;
        private PersistenceUnitTransactionType transactionType = PersistenceUnitTransactionType.RESOURCE_LOCAL;
        private DataSource jtaDataSource;

        private DataSource nonJtaDataSource;

        public PersistenceUnitInfoImpl(
            String persistenceUnitName,
            List<String> managedClassNames,
            Properties properties) {
            this.persistenceUnitName = persistenceUnitName;
            this.managedClassNames = managedClassNames;
            this.properties = properties;
        }

        @Override
        public String getPersistenceUnitName() {
            return persistenceUnitName;
        }

        @Override
        public String getPersistenceProviderClassName() {
            return HibernatePersistenceProvider.class.getName();
        }

        @Override
        public PersistenceUnitTransactionType getTransactionType() {
            return transactionType;
        }

        @Override
        public DataSource getJtaDataSource() {
            return jtaDataSource;
        }

        public PersistenceUnitInfoImpl setJtaDataSource(DataSource jtaDataSource) {
            this.jtaDataSource = jtaDataSource;
            this.nonJtaDataSource = null;
            transactionType = PersistenceUnitTransactionType.JTA;
            return this;
        }

        @Override
        public DataSource getNonJtaDataSource() {
            return nonJtaDataSource;
        }

        public PersistenceUnitInfoImpl setNonJtaDataSource(DataSource nonJtaDataSource) {
            this.nonJtaDataSource = nonJtaDataSource;
            this.jtaDataSource = null;
            transactionType = PersistenceUnitTransactionType.RESOURCE_LOCAL;
            return this;
        }

        @Override
        public List<String> getMappingFileNames() {
            return mappingFileNames;
        }

        @Override
        public List<URL> getJarFileUrls() {
            return Collections.emptyList();
        }

        @Override
        public URL getPersistenceUnitRootUrl() {
            return null;
        }

        @Override
        public List<String> getManagedClassNames() {
            return managedClassNames;
        }

        @Override
        public boolean excludeUnlistedClasses() {
            return false;
        }

        @Override
        public SharedCacheMode getSharedCacheMode() {
            return SharedCacheMode.UNSPECIFIED;
        }

        @Override
        public ValidationMode getValidationMode() {
            return ValidationMode.AUTO;
        }

        public Properties getProperties() {
            return properties;
        }

        @Override
        public String getPersistenceXMLSchemaVersion() {
            return "2.1";
        }

        @Override
        public ClassLoader getClassLoader() {
            return Thread.currentThread().getContextClassLoader();
        }

        @Override
        public void addTransformer(ClassTransformer transformer) {

        }

        @Override
        public ClassLoader getNewTempClassLoader() {
            return null;
        }
    }
}
