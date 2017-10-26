package com.vladmihalcea.hibernate.type.basic;

import com.vladmihalcea.hibernate.type.ImmutableType;
import org.hibernate.engine.spi.SharedSessionContractImplementor;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

/**
 * Maps a Character to a nullable CHAR column type.
 *
 * For more details about how to use it, check out <a href="https://vladmihalcea.com/2016/09/22/how-to-implement-a-custom-basic-type-using-hibernate-usertype/">this article</a> for more info.
 *
 * @author Vlad Mihalcea
 */
public class NullableCharacterType extends ImmutableType<Character> {

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