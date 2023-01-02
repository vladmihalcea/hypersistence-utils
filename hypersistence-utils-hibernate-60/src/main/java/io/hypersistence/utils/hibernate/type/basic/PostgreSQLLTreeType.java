package io.hypersistence.utils.hibernate.type.basic;

import io.hypersistence.utils.hibernate.type.ImmutableType;
import io.hypersistence.utils.hibernate.type.util.Configuration;
import org.hibernate.engine.spi.SharedSessionContractImplementor;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

public class PostgreSQLLTreeType extends ImmutableType<String> {

    public static final PostgreSQLLTreeType INSTANCE = new PostgreSQLLTreeType();

    protected PostgreSQLLTreeType() {
        super(String.class);
    }

    public PostgreSQLLTreeType(org.hibernate.type.spi.TypeBootstrapContext typeBootstrapContext) {
        super(String.class, new Configuration(typeBootstrapContext.getConfigurationSettings()));
    }

    @Override
    public int getSqlType() {
        return Types.OTHER;
    }

    @Override
    protected String get(ResultSet rs, int position, SharedSessionContractImplementor session, Object owner) throws SQLException {
        Object value = rs.getObject(position);
        return value == null ? null : value.toString();
    }

    @Override
    protected void set(PreparedStatement st, String value, int index, SharedSessionContractImplementor session) throws SQLException {
        st.setObject(index, value, Types.OTHER);
    }
}
