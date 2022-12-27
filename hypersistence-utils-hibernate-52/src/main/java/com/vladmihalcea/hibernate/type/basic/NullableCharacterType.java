package com.vladmihalcea.hibernate.type.basic;

import com.vladmihalcea.hibernate.type.ImmutableType;
import org.hibernate.HibernateException;
import org.hibernate.MappingException;
import org.hibernate.engine.jdbc.Size;
import org.hibernate.engine.spi.Mapping;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.type.ForeignKeyDirection;
import org.hibernate.type.Type;

import java.io.Serializable;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Map;

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

    @Override
    public int[] sqlTypes() {
        return new int[]{Types.CHAR};
    }

    @Override
    public Character get(ResultSet rs, String[] names,
                         SharedSessionContractImplementor session, Object owner) throws SQLException {
        String value = rs.getString(names[0]);
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
}