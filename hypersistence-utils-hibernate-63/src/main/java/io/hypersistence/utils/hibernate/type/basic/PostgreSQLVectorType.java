package io.hypersistence.utils.hibernate.type.basic;

import io.hypersistence.utils.hibernate.type.ImmutableType;
import io.hypersistence.utils.hibernate.type.util.Configuration;
import io.hypersistence.utils.common.ReflectionUtils;
import org.hibernate.HibernateException;
import org.hibernate.engine.spi.SharedSessionContractImplementor;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.StringTokenizer;

/**
 * Maps a {@code float[]} object type to a PostgreSQL <a href="https://github.com/pgvector/pgvector">pgvector</a>
 * {@code vector} column type.
 * <p>
 * This implementation does not require the {@code pgvector-java} library on the classpath.
 * Instead, it builds the underlying {@code org.postgresql.util.PGobject} via reflection,
 * the same way {@link PostgreSQLInetType} and {@link PostgreSQLMacAddressType} do for other
 * PostgreSQL-specific column types.
 *
 * @author Chirag Gupta
 */
public class PostgreSQLVectorType extends ImmutableType<float[]> {

    public static final PostgreSQLVectorType INSTANCE = new PostgreSQLVectorType();

    public PostgreSQLVectorType() {
        super(float[].class);
    }

    public PostgreSQLVectorType(org.hibernate.type.spi.TypeBootstrapContext typeBootstrapContext) {
        super(float[].class, new Configuration(typeBootstrapContext.getConfigurationSettings()));
    }

    @Override
    public int getSqlType() {
        return Types.OTHER;
    }

    @Override
    public float[] get(ResultSet rs, int position, SharedSessionContractImplementor session, Object owner) throws SQLException {
        Object value = rs.getObject(position);
        return value == null ? null : fromStringValue(value.toString());
    }

    @Override
    public void set(PreparedStatement st, float[] value, int index, SharedSessionContractImplementor session) throws SQLException {
        if (value == null) {
            st.setNull(index, Types.OTHER);
        } else {
            Object holder = ReflectionUtils.newInstance("org.postgresql.util.PGobject");
            ReflectionUtils.invokeSetter(holder, "type", "vector");
            ReflectionUtils.invokeSetter(holder, "value", toStringValue(value));
            st.setObject(index, holder);
        }
    }

    @Override
    public float[] fromStringValue(CharSequence sequence) throws HibernateException {
        if (sequence == null) {
            return null;
        }
        String value = sequence.toString().trim();
        if (value.startsWith("[") && value.endsWith("]")) {
            value = value.substring(1, value.length() - 1);
        }
        if (value.isEmpty()) {
            return new float[0];
        }
        StringTokenizer tokenizer = new StringTokenizer(value, ",");
        float[] vector = new float[tokenizer.countTokens()];
        int i = 0;
        while (tokenizer.hasMoreTokens()) {
            vector[i++] = Float.parseFloat(tokenizer.nextToken().trim());
        }
        return vector;
    }

    private String toStringValue(float[] vector) {
        StringBuilder builder = new StringBuilder();
        builder.append('[');
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(vector[i]);
        }
        builder.append(']');
        return builder.toString();
    }

}
