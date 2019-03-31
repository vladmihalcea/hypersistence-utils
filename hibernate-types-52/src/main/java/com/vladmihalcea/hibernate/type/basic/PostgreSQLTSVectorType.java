package com.vladmihalcea.hibernate.type.basic;

import com.vladmihalcea.hibernate.type.ImmutableType;
import com.vladmihalcea.hibernate.type.util.ReflectionUtils;
import org.hibernate.engine.spi.SharedSessionContractImplementor;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

public class PostgreSQLTSVectorType  extends ImmutableType<String> {

    public PostgreSQLTSVectorType() {
        super(String.class);
    }

    @Override
    public int[] sqlTypes() {
        return new int[] { Types.OTHER };
    }

    @Override
    protected String get(ResultSet rs, String[] names, SharedSessionContractImplementor session, Object owner)
            throws SQLException {
        return rs.getString(names[0]);
    }

    @Override
    protected void set(PreparedStatement st, String value, int index, SharedSessionContractImplementor session)
            throws SQLException {
        if (value == null) {
            st.setNull(index, Types.OTHER);
        } else {
            Object holder = ReflectionUtils.newInstance("org.postgresql.util.PGobject");
            ReflectionUtils.invokeSetter(holder, "type", "tsvector");
            ReflectionUtils.invokeSetter(holder, "value", value);
            st.setObject(index, holder);
        }
    }
}
