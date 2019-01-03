package com.vladmihalcea.hibernate.type.basic;

import com.vladmihalcea.hibernate.type.ImmutableType;
import com.vladmihalcea.hibernate.type.util.ReflectionUtils;
import org.hibernate.engine.spi.SessionImplementor;

import java.lang.reflect.Method;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Map;

/**
 * Maps a {@link Map} object type to a PostgreSQL <a href="https://www.postgresql.org/docs/current/hstore.html">hstore</a>
 * column type.
 * <p>
 * For more details about how to use it,
 * check out <a href="https://vladmihalcea.com/map-postgresql-hstore-jpa-entity-property-hibernate/">this article</a>
 * on <a href="https://vladmihalcea.com/">vladmihalcea.com</a>.
 *
 * @author Edgar Asatryan
 * @author Vlad Mihalcea
 */
public class PostgreSQLHStoreType extends ImmutableType<Map> {

    public PostgreSQLHStoreType() {
        super(Map.class);
    }

    @Override
    public int[] sqlTypes() {
        return new int[]{Types.OTHER};
    }

    @Override
    protected Map get(ResultSet rs, String[] names, SessionImplementor session, Object owner) throws SQLException {
        return (Map) rs.getObject(names[0]);
    }

    @Override
    protected void set(PreparedStatement st, Map value, int index, SessionImplementor session) throws SQLException {
        if (value == null) {
            st.setNull(index, Types.OTHER);
        } else {
            Object holder = ReflectionUtils.newInstance("org.postgresql.util.PGobject");
            ReflectionUtils.invokeSetter(
                holder,
                "type",
                "hstore"
            );

            Class targetClass = ReflectionUtils.getClass("org.postgresql.util.HStoreConverter");
            Method mapToStringMethod = ReflectionUtils.getMethod(targetClass, "toString", Map.class);

            ReflectionUtils.invokeSetter(
                holder,
                "value",
                ReflectionUtils.invokeStatic(
                    mapToStringMethod,
                    value
                )
            );

            st.setObject(index, holder);
        }
    }
}
