package com.vladmihalcea.hibernate.type.basic;

import com.vladmihalcea.hibernate.type.ImmutableType;
import com.vladmihalcea.hibernate.type.util.ReflectionUtils;
import org.hibernate.engine.spi.SessionImplementor;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

/**
 * Maps a {@link String} object type to a PostgreSQL TSVector column type.
 *
 * @author Vlad Mihalcea
 * @author Philip Riecks
 */
public class PostgreSQLTSVectorType extends ImmutableType<String> {

    public PostgreSQLTSVectorType() {
        super(String.class);
    }

    @Override
    public int[] sqlTypes() {
        return new int[] { Types.OTHER };
    }

    @Override
    protected String get(ResultSet rs, String[] names, SessionImplementor session, Object owner)
            throws SQLException {
        return rs.getString(names[0]);
    }

    @Override
    protected void set(PreparedStatement st, String value, int index, SessionImplementor session)
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
