package io.hypersistence.utils.hibernate.type.basic;

import io.hypersistence.utils.hibernate.type.ImmutableType;
import io.hypersistence.utils.hibernate.type.json.internal.JacksonUtil;
import io.hypersistence.utils.hibernate.type.util.Configuration;
import org.hibernate.HibernateException;
import org.hibernate.engine.spi.SharedSessionContractImplementor;

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

    public static final PostgreSQLHStoreType INSTANCE = new PostgreSQLHStoreType();

    public PostgreSQLHStoreType() {
        super(Map.class);
    }

    public PostgreSQLHStoreType(org.hibernate.type.spi.TypeBootstrapContext typeBootstrapContext) {
        super(Map.class, new Configuration(typeBootstrapContext.getConfigurationSettings()));
    }

    @Override
    public int getSqlType() {
        return Types.OTHER;
    }

    @Override
    protected Map get(ResultSet rs, int position, SharedSessionContractImplementor session, Object owner) throws SQLException {
        return (Map) rs.getObject(position);
    }

    @Override
    protected void set(PreparedStatement st, Map value, int index, SharedSessionContractImplementor session) throws SQLException {
        st.setObject(index, value);
    }

    @Override
    public Map fromStringValue(CharSequence sequence) throws HibernateException {
        try {
            return JacksonUtil.fromString((String) sequence, Map.class);
        } catch (Exception e) {
            throw new IllegalArgumentException(
                String.format(
                    "Could not transform the [%s] value to a Map!",
                    sequence
                )
            );
        }
    }
}
