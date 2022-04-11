package com.vladmihalcea.hibernate.util.providers;

import com.vladmihalcea.hibernate.util.ReflectionUtils;

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
		DataSource dataSource = ReflectionUtils.newInstance("com.microsoft.sqlserver.jdbc.SQLServerDataSource");
		ReflectionUtils.invokeMethod(dataSource, "setURL", "jdbc:sqlserver://localhost;instance=SQLEXPRESS;" +
			"databaseName=high_performance_java_persistence;");
		ReflectionUtils.invokeMethod(dataSource, "setUser", "sa");
		ReflectionUtils.invokeMethod(dataSource, "setPassword", "Admin_123456");
		return dataSource;
	}

	@Override
	public Class<? extends DataSource> dataSourceClassName() {
		return ReflectionUtils.getClass("com.microsoft.sqlserver.jdbc.SQLServerDataSource");
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
