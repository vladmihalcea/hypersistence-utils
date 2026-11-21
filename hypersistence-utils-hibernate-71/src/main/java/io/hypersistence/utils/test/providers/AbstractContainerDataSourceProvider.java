package io.hypersistence.utils.test.providers;

import org.testcontainers.containers.JdbcDatabaseContainer;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Vlad Mihalcea
 */
public abstract class AbstractContainerDataSourceProvider implements DataSourceProvider {

    /**
     * The containers are cached per provider type since the tests can create
     * a new provider instance every time the data source is resolved,
     * and, otherwise, every such instance would start its own container.
     */
    private static final Map<Class<?>, JdbcDatabaseContainer> CONTAINERS = new ConcurrentHashMap<>();

    public JdbcDatabaseContainer getContainer() {
        return CONTAINERS.get(getClass());
    }

    public void initContainer(String username, String password) {
        CONTAINERS.computeIfAbsent(getClass(), providerClass -> {
            JdbcDatabaseContainer container = (JdbcDatabaseContainer) newJdbcDatabaseContainer()
                .withReuse(true)
                .withEnv(Collections.singletonMap("ACCEPT_EULA", "Y"))
                .withTmpFs(Collections.singletonMap("/testtmpfs", "rw"));
            if (supportsDatabaseName()) {
                container.withDatabaseName("high-performance-java-persistence");
            }
            if (supportsCredentials()) {
                container.withUsername(username).withPassword(password);
            }
            container.start();
            return container;
        });
    }

    @Override
    public DataSource dataSource() {
        if (preferLocalDatabaseServer()) {
            DataSource dataSource = newDataSource();
            try (Connection connection = dataSource.getConnection()) {
                return dataSource;
            } catch (SQLException e) {
                //Continue with Docker container
            }
        }
        if (getContainer() == null) {
            initContainer(username(), password());
        }
        return newDataSource();
    }

    @Override
    public String url() {
        JdbcDatabaseContainer container = getContainer();
        return container != null ?
            container.getJdbcUrl() :
            defaultJdbcUrl();
    }

    protected abstract String defaultJdbcUrl();

    protected abstract DataSource newDataSource();

    protected boolean preferLocalDatabaseServer() {
        return true;
    }
}
