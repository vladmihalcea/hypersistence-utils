package com.vladmihalcea.hibernate.type.basic;

import com.vladmihalcea.hibernate.type.ImmutableType;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.postgresql.util.PGobject;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

/**
 * Maps an {@link Inet} object type to a PostgreSQL INET column type.
 * <p>
 * For more details about how to use it,
 * check out <a href="https://vladmihalcea.com/postgresql-inet-type-hibernate/">this article</a>
 * on <a href="https://vladmihalcea.com/">vladmihalcea.com</a>.
 *
 * @author Vlad Mihalcea
 */
public class PostgreSQLInetType extends ImmutableType<Inet> {

    public static final PostgreSQLInetType INSTANCE = new PostgreSQLInetType();

    public PostgreSQLInetType() {
        super(Inet.class);
    }

    @Override
    public int[] sqlTypes() {
        return new int[]{Types.OTHER};
    }

    @Override
    public Inet get(ResultSet rs, String[] names, SharedSessionContractImplementor session, Object owner) throws SQLException {
        String ip = rs.getString(names[0]);
        return (ip != null) ? new Inet(ip) : null;
    }

    @Override
    public void set(PreparedStatement st, Inet value, int index, SharedSessionContractImplementor session) throws SQLException {
        if (value == null) {
            st.setNull(index, Types.OTHER);
        } else {
            PGobject holder = new PGobject();
            holder.setType("inet");
            holder.setValue(value.getAddress());
            st.setObject(index, holder);
        }
    }
}