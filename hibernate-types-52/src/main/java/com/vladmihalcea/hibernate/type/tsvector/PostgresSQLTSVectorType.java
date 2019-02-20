package com.vladmihalcea.hibernate.type.tsvector;

import com.vladmihalcea.hibernate.type.ImmutableType;
import com.vladmihalcea.hibernate.type.util.ReflectionUtils;
import org.hibernate.engine.spi.SharedSessionContractImplementor;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;


/**
 * @author bitluke
 */
public class PostgresSQLTSVectorType extends ImmutableType<TSVector> {

    public PostgresSQLTSVectorType() {
        super(TSVector.class);
    }

    @Override
    protected TSVector get(ResultSet rs, String[] names, SharedSessionContractImplementor session, Object owner) throws SQLException {
        String text = rs.getString(names[0]);
        return (text != null) ? new TSVector(text) : null;
    }

    @Override
    protected void set(PreparedStatement st, TSVector value, int index, SharedSessionContractImplementor session) throws SQLException {
        if (value == null) {
            st.setNull(index, Types.OTHER);
        } else {
            Object holder = ReflectionUtils.newInstance("org.postgresql.util.PGobject");
            ReflectionUtils.invokeSetter(holder, "type", "tsvector");
            ReflectionUtils.invokeSetter(holder, "value", value.getTokens());
            st.setObject(index, holder);
        }
    }

    @Override
    public int[] sqlTypes() {
        return new int[]{Types.OTHER};
    }
}
