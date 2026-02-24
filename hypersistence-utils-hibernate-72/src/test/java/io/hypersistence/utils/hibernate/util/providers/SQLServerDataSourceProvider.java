package io.hypersistence.utils.hibernate.util.providers;

import com.microsoft.sqlserver.jdbc.SQLServerDataSource;
import io.hypersistence.utils.test.providers.AbstractContainerDataSourceProvider;
import io.hypersistence.utils.test.providers.DataSourceProvider;
import org.hibernate.dialect.Database;
import org.hibernate.dialect.SQLServerDialect;
import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.containers.MSSQLServerContainer;

import javax.sql.DataSource;

/**
 * @author Vlad Mihalcea
 */
public class SQLServerDataSourceProvider extends AbstractContainerDataSourceProvider {

	public static final DataSourceProvider INSTANCE = new SQLServerDataSourceProvider();

	@Override
	public Database database() {
		return Database.SQLSERVER;
	}

	@Override
	public String hibernateDialect() {
		return SQLServerDialect.class.getName();
	}

	@Override
	public String defaultJdbcUrl() {
		return "jdbc:sqlserver://localhost;instance=SQLEXPRESS;databaseName=high_performance_java_persistence;encrypt=true;trustServerCertificate=true";
	}

	@Override
	public DataSource newDataSource() {
		SQLServerDataSource dataSource = new SQLServerDataSource();
		dataSource.setURL(url());
		JdbcDatabaseContainer container = getContainer();
		if(container == null) {
			dataSource.setUser(username());
			dataSource.setPassword(password());
		} else {
			dataSource.setUser(container.getUsername());
			dataSource.setPassword(container.getPassword());
		}
		return dataSource;
	}

	@Override
	public String username() {
		return "sa";
	}

	@Override
	public String password() {
		return "adm1n";
	}

	@Override
	public JdbcDatabaseContainer newJdbcDatabaseContainer() {
		return new MSSQLServerContainer("mcr.microsoft.com/mssql/server:2019-latest");
	}

	@Override
	public boolean supportsDatabaseName() {
		return false;
	}

	@Override
	public boolean supportsCredentials() {
		return false;
	}
}
