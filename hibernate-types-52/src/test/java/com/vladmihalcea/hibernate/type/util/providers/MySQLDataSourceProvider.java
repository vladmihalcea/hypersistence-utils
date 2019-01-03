package com.vladmihalcea.hibernate.type.util.providers;

import com.mysql.cj.jdbc.MysqlDataSource;

import javax.sql.DataSource;
import java.util.Properties;

/**
 * @author Vlad Mihalcea
 */
public class MySQLDataSourceProvider implements DataSourceProvider {

    private boolean rewriteBatchedStatements = true;

    private boolean cachePrepStmts = false;

    private boolean useServerPrepStmts = false;

    private boolean useTimezone = false;

    private boolean useJDBCCompliantTimezoneShift = false;

    private boolean useLegacyDatetimeCode = true;

    public boolean isRewriteBatchedStatements() {
        return rewriteBatchedStatements;
    }

    public void setRewriteBatchedStatements(boolean rewriteBatchedStatements) {
        this.rewriteBatchedStatements = rewriteBatchedStatements;
    }

    public boolean isCachePrepStmts() {
        return cachePrepStmts;
    }

    public void setCachePrepStmts(boolean cachePrepStmts) {
        this.cachePrepStmts = cachePrepStmts;
    }

    public boolean isUseServerPrepStmts() {
        return useServerPrepStmts;
    }

    public void setUseServerPrepStmts(boolean useServerPrepStmts) {
        this.useServerPrepStmts = useServerPrepStmts;
    }

    public boolean isUseTimezone() {
        return useTimezone;
    }

    public void setUseTimezone(boolean useTimezone) {
        this.useTimezone = useTimezone;
    }

    public boolean isUseJDBCCompliantTimezoneShift() {
        return useJDBCCompliantTimezoneShift;
    }

    public void setUseJDBCCompliantTimezoneShift(boolean useJDBCCompliantTimezoneShift) {
        this.useJDBCCompliantTimezoneShift = useJDBCCompliantTimezoneShift;
    }

    public boolean isUseLegacyDatetimeCode() {
        return useLegacyDatetimeCode;
    }

    public void setUseLegacyDatetimeCode(boolean useLegacyDatetimeCode) {
        this.useLegacyDatetimeCode = useLegacyDatetimeCode;
    }

    @Override
    public String hibernateDialect() {
        return "org.hibernate.dialect.MySQL57Dialect";
    }

    @Override
    public DataSource dataSource() {
        MysqlDataSource dataSource = new MysqlDataSource();
        dataSource.setURL("jdbc:mysql://localhost/high_performance_java_persistence?" +
                "rewriteBatchedStatements=" + rewriteBatchedStatements +
                "&cachePrepStmts=" + cachePrepStmts +
                "&useServerPrepStmts=" + useServerPrepStmts +
                "&useTimezone=" + useTimezone +
                "&useJDBCCompliantTimezoneShift=" + useJDBCCompliantTimezoneShift +
                "&useLegacyDatetimeCode=" + useLegacyDatetimeCode

        );
        dataSource.setUser("mysql");
        dataSource.setPassword("admin");
        return dataSource;
    }

    @Override
    public Class<? extends DataSource> dataSourceClassName() {
        return MysqlDataSource.class;
    }

    @Override
    public Properties dataSourceProperties() {
        Properties properties = new Properties();
        properties.setProperty("url", url());
        return properties;
    }

    @Override
    public String url() {
        return "jdbc:mysql://localhost/high_performance_java_persistence?user=mysql&password=admin";
    }

    @Override
    public String username() {
        return null;
    }

    @Override
    public String password() {
        return null;
    }

    @Override
    public Database database() {
        return Database.MYSQL;
    }

    @Override
    public String toString() {
        return "MySQLDataSourceProvider{" +
                "rewriteBatchedStatements=" + rewriteBatchedStatements +
                ", cachePrepStmts=" + cachePrepStmts +
                ", useServerPrepStmts=" + useServerPrepStmts +
                ", useTimezone=" + useTimezone +
                ", useJDBCCompliantTimezoneShift=" + useJDBCCompliantTimezoneShift +
                ", useLegacyDatetimeCode=" + useLegacyDatetimeCode +
                '}';
    }
}
