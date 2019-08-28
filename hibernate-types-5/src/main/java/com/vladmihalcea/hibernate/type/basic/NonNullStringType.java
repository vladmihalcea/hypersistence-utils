package com.vladmihalcea.hibernate.type.basic;

import com.vladmihalcea.hibernate.type.ImmutableType;
import org.hibernate.engine.spi.SessionImplementor;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

/**
 * Maps a {@link String} object to an SQL <code>VARCHAR</code> or <code>LONGVARCHAR</code> value,
 * avoiding two possible values for “no data” by replacing NULLs with blank lines.
 * Usually string-based fields have two possible values for “no data”: NULL, and the empty (blank) string.
 * In most cases, it’s redundant and can lead to mistakes.
 *
 * @author Andrei Akinchev
 */
public class NonNullStringType extends ImmutableType<String> {

    public static final NonNullStringType INSTANCE = new NonNullStringType();

    public NonNullStringType() {
        super(String.class);
    }

    @Override
    public int[] sqlTypes() {
        return new int[]{Types.OTHER};
    }

    @Override
    protected String get(ResultSet rs, String[] names, SessionImplementor session, Object owner) throws SQLException {
        String value = rs.getString(names[0]);
        if (value == null) {
            return "";
        }
        return value;
    }

    @Override
    protected void set(PreparedStatement st, String value, int index, SessionImplementor session) throws SQLException {
        if (value == null) {
            value = "";
        }
        st.setString(index, value);
    }
}
