package io.hypersistence.utils.hibernate.util.providers;

import org.testcontainers.containers.*;

import java.util.Collections;

/**
 * @author Vlad Mihalcea
 */
public enum Database {
    POSTGRESQL {
        @Override
        protected JdbcDatabaseContainer newJdbcDatabaseContainer() {
            return new PostgreSQLContainer("postgres:13.7");
        }
    },
    ORACLE {
        @Override
        protected JdbcDatabaseContainer newJdbcDatabaseContainer() {
            return new OracleContainer("gvenzl/oracle-xe:21.3.0-slim");
        }

        @Override
        protected boolean supportsDatabaseName() {
            return false;
        }
    },
    MYSQL {
        @Override
        protected JdbcDatabaseContainer newJdbcDatabaseContainer() {
            return new MySQLContainer("mysql:8.0");
        }
    },
    SQLSERVER {
        @Override
        protected JdbcDatabaseContainer newJdbcDatabaseContainer() {
            return new MSSQLServerContainer("mcr.microsoft.com/mssql/server:2019-latest");
        }

        @Override
        protected boolean supportsDatabaseName() {
            return false;
        }

        @Override
        protected boolean supportsCredentials() {
            return false;
        }
    },
    HSQLDB,
    H2
    ;

    private JdbcDatabaseContainer container;

    public JdbcDatabaseContainer getContainer() {
        return container;
    }

    public void initContainer(String username, String password) {
        container = (JdbcDatabaseContainer) newJdbcDatabaseContainer()
            .withReuse(true)
            .withEnv(Collections.singletonMap("ACCEPT_EULA", "Y"))
            .withTmpFs(Collections.singletonMap("/testtmpfs", "rw"));
        if(supportsDatabaseName()) {
            container.withDatabaseName("high-performance-java-persistence");
        }
        if(supportsCredentials()) {
            container.withUsername(username).withPassword(password);
        }
        container.start();
    }

    protected JdbcDatabaseContainer newJdbcDatabaseContainer() {
        throw new UnsupportedOperationException(
            String.format(
                "The [%s] database was not configured to use Testcontainers!",
                name()
            )
        );
    }

    protected boolean supportsDatabaseName() {
        return true;
    }

    protected boolean supportsCredentials() {
        return true;
    }
}
