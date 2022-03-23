package com.vladmihalcea.hibernate.type.basic;

import com.vladmihalcea.hibernate.type.util.Configuration;
import org.hibernate.HibernateException;
import org.hibernate.engine.spi.SessionImplementor;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;

/**
 * Maps an {@link Enum} to a PostgreSQL ENUM column type.
 * <p>
 * For more details about how to use it, check out <a href="https://vladmihalcea.com/the-best-way-to-map-an-enum-type-with-jpa-and-hibernate/">this article</a> on <a href="https://vladmihalcea.com/">vladmihalcea.com</a>.
 *
 * @author Vlad Mihalcea
 */
public class PostgreSQLEnumType extends org.hibernate.type.EnumType {

    public static final PostgreSQLEnumType INSTANCE = new PostgreSQLEnumType();

    private final Configuration configuration;

    /**
     * Initialization constructor taking the default {@link Configuration} object.
     */
    public PostgreSQLEnumType() {
        this.configuration = Configuration.INSTANCE;
    }

    /**
     * Initialization constructor taking the {@link Class} and {@link Configuration} objects.
     *
     * @param configuration custom {@link Configuration} object.
     */
    public PostgreSQLEnumType(Configuration configuration) {
        this.configuration = configuration;
    }

    public void nullSafeSet(
            PreparedStatement st,
            Object value,
            int index,
            SessionImplementor session)
            throws HibernateException, SQLException {
        st.setObject(index, value != null ? ((Enum) value).name() : null, Types.OTHER);
    }

    @Override
    public int[] sqlTypes() {
        return new int[]{Types.VARCHAR};
    }
}
