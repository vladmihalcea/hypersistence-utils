package io.hypersistence.utils.hibernate.util.providers;

import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * @author Vlad Mihalcea
 */
public class PgVectorPostgreSQLDataSourceProvider extends PostgreSQLDataSourceProvider {

    @Override
    protected boolean preferLocalDatabaseServer() {
        return false;
    }

    @Override
    public JdbcDatabaseContainer newJdbcDatabaseContainer() {
        return new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg18").asCompatibleSubstituteFor("postgres")
        );
    }
}
