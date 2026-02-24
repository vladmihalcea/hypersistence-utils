package io.hypersistence.utils.hibernate.type.basic;

import io.hypersistence.utils.hibernate.type.ImmutableType;
import io.hypersistence.utils.hibernate.type.util.Configuration;
import org.hibernate.HibernateException;
import org.hibernate.engine.spi.SharedSessionContractImplementor;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

/**
 * Maps an {@link Character} to a nullable CHAR column type.
 * <p>
 * For more details about how to use it, check out <a href="https://vladmihalcea.com/how-to-implement-a-custom-basic-type-using-hibernate-usertype/">this article</a> on <a href="https://vladmihalcea.com/">vladmihalcea.com</a>.
 *
 * @author Vlad Mihalcea
 */
public class NullableCharacterType extends ImmutableType<Character> {

    public static final NullableCharacterType INSTANCE = new NullableCharacterType();

    public NullableCharacterType() {
        super(Character.class);
    }

    public NullableCharacterType(org.hibernate.type.spi.TypeBootstrapContext typeBootstrapContext) {
        super(Character.class, new Configuration(typeBootstrapContext.getConfigurationSettings()));
    }

    @Override
    public int getSqlType() {
        return Types.CHAR;
    }

    @Override
    public Character get(ResultSet rs, int position,
                         SharedSessionContractImplementor session, Object owner) throws SQLException {
        String value = rs.getString(position);
        return (value != null && value.length() > 0) ? value.charAt(0) : null;
    }

    @Override
    public void set(PreparedStatement st, Character value, int index,
                    SharedSessionContractImplementor session) throws SQLException {
        if (value == null) {
            st.setNull(index, Types.CHAR);
        } else {
            st.setString(index, String.valueOf(value));
        }
    }

    @Override
    public Character fromStringValue(CharSequence sequence) throws HibernateException {
        return sequence != null ? sequence.charAt(0) : null;
    }
}