package com.vladmihalcea.hibernate.util.providers;

import com.microsoft.sqlserver.jdbc.SQLServerDataSource;
import org.hibernate.dialect.SQLServerDialect;
import org.testcontainers.containers.JdbcDatabaseContainer;

import javax.sql.DataSource;
import java.util.Properties;

/**
 * @author Vlad Mihalcea
 */
public class SQLServerDataSourceProvider extends AbstractContainerDataSourceProvider {

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
		JdbcDatabaseContainer container = database().getContainer();
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
	public Database database() {
		return Database.SQLSERVER;
	}
}
