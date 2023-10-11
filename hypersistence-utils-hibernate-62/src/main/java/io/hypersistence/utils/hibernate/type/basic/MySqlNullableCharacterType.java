package io.hypersistence.utils.hibernate.type.basic;

import org.hibernate.engine.spi.SharedSessionContractImplementor;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;

/**
 * Maps an {@link Character} to a nullable CHAR column type.
 * <p>
 * For mySql, if the character is \ (backslash) it must be escaped.
 * <p>
 * For more details about how to use it, check out <a href="https://vladmihalcea.com/how-to-implement-a-custom-basic-type-using-hibernate-usertype/">this article</a> on <a href="https://vladmihalcea.com/">vladmihalcea.com</a>.
 *
 * @author Wim Wintmolders
 */
public class MySqlNullableCharacterType extends NullableCharacterType {

    public static final MySqlNullableCharacterType INSTANCE = new MySqlNullableCharacterType();

    @Override
    public void set(PreparedStatement st, Character value, int index,
                    SharedSessionContractImplementor session) throws SQLException {
        if (value == null) {
            st.setNull(index, Types.CHAR);
        } else {
            if (value == '\\') {
                st.setString(index, "\\\\");
            } else {
                st.setString(index, String.valueOf(value));
            }
        }
    }
}
