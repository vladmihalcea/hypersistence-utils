package com.vladmihalcea.hibernate.type.basic;

import com.vladmihalcea.hibernate.type.ImmutableType;
import com.vladmihalcea.hibernate.type.util.ReflectionUtils;
import org.hibernate.engine.spi.SessionImplementor;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

/**
 * @author Vlad Mihalcea
 */
public class PostgreSQLInetType extends ImmutableType<Inet> {

    public PostgreSQLInetType() {
        super(Inet.class);
    }

    @Override
    public int[] sqlTypes() {
        return new int[]{Types.OTHER};
    }

    @Override
    public Inet get(ResultSet rs, String[] names, SessionImplementor session, Object owner) throws SQLException {
        String ip = rs.getString(names[0]);
        return (ip != null) ? new Inet(ip) : null;
    }

    @Override
    public void set(PreparedStatement st, Inet value, int index, SessionImplementor session) throws SQLException {
        if (value == null) {
            st.setNull(index, Types.OTHER);
        } else {
            Object holder = ReflectionUtils.newInstance("org.postgresql.util.PGobject");
            ReflectionUtils.invokeSetter(holder, "type", "inet");
            ReflectionUtils.invokeSetter(holder, "value", value.getAddress());
            st.setObject(index, holder);
        }
    }
}