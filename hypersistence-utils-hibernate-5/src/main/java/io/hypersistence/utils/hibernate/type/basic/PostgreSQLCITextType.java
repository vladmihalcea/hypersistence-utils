package io.hypersistence.utils.hibernate.type.basic;

import io.hypersistence.utils.hibernate.type.ImmutableType;
import org.hibernate.engine.spi.SessionImplementor;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

/**
 * Maps a {@link String} object type to a PostgreSQL <a href="https://www.postgresql.org/docs/current/citext.html">citext</a>
 * column type.
 *
 * @author Sergei Portnov
 */
public class PostgreSQLCITextType extends ImmutableType<String> {

    public static final PostgreSQLCITextType INSTANCE = new PostgreSQLCITextType();

    public PostgreSQLCITextType() {
        super(String.class);
    }

    @Override
    public int[] sqlTypes() {
        return new int[]{Types.OTHER};
    }

    @Override
    protected String get(ResultSet rs, String[] names, SessionImplementor session, Object owner) throws SQLException {
        Object value = rs.getObject(names[0]);
        return value == null ? null : value.toString();
    }

    @Override
    protected void set(PreparedStatement st, String value, int index, SessionImplementor session) throws SQLException {
        st.setObject(index, value, Types.OTHER);
    }
}
