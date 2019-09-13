package com.vladmihalcea.hibernate.type.util.providers;

import com.microsoft.sqlserver.jdbc.SQLServerDataSource;

import javax.sql.DataSource;
import java.util.Properties;

/**
 * @author Vlad Mihalcea
 */
public class SQLServerDataSourceProvider implements DataSourceProvider {
	@Override
	public String hibernateDialect() {
		return "org.hibernate.dialect.SQLServer2012Dialect";
	}

	@Override
	public DataSource dataSource() {
		SQLServerDataSource dataSource = new SQLServerDataSource();
		dataSource.setURL(
				"jdbc:sqlserver://localhost;instance=SQLEXPRESS;" +
				"databaseName=high_performance_java_persistence;"
		);
		dataSource.setUser("sa");
		dataSource.setPassword("adm1n");
		return dataSource;
	}

	@Override
	public Class<? extends DataSource> dataSourceClassName() {
		return SQLServerDataSource.class;
	}

	@Override
	public Properties dataSourceProperties() {
		Properties properties = new Properties();
		properties.setProperty( "URL", url() );
		return properties;
	}

	@Override
	public String url() {
		return "jdbc:sqlserver://localhost;instance=SQLEXPRESS;databaseName=high_performance_java_persistence;user=sa;password=adm1n";
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
