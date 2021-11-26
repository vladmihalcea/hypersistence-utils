package com.vladmihalcea.hibernate.util.providers;

import com.microsoft.sqlserver.jdbc.SQLServerDataSource;
import org.testcontainers.containers.MSSQLServerContainer;
import org.testcontainers.containers.OracleContainer;

import javax.sql.DataSource;
import java.util.Properties;

/**
 * @author Vlad Mihalcea
 */
public class SQLServerDataSourceProvider implements DataSourceProvider {
	private static final MSSQLServerContainer<?> MSSQL_SERVER_CONTAINER;

	static {
		MSSQL_SERVER_CONTAINER = new MSSQLServerContainer<>("mcr.microsoft.com/mssql/server:2017-CU12")
				.acceptLicense()
				.withPassword("SuPeRaDmIn1337");

		MSSQL_SERVER_CONTAINER.start();
	}

	@Override
	public String hibernateDialect() {
		return "org.hibernate.dialect.SQLServer2012Dialect";
	}

	@Override
	public DataSource dataSource() {
		SQLServerDataSource dataSource = new SQLServerDataSource();
		dataSource.setURL(MSSQL_SERVER_CONTAINER.getJdbcUrl());
		dataSource.setUser(MSSQL_SERVER_CONTAINER.getUsername());
		dataSource.setPassword(MSSQL_SERVER_CONTAINER.getPassword());
		return dataSource;
	}

	@Override
	public Database database() {
		return Database.SQLSERVER;
	}
}
