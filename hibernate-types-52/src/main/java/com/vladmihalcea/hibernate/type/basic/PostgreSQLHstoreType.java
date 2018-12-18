package com.vladmihalcea.hibernate.type.basic;

import com.vladmihalcea.hibernate.type.ImmutableType;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.postgresql.core.Encoding;
import org.postgresql.util.HStoreConverter;
import org.postgresql.util.PGobject;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Map;

/**
 * @author Edgar Asatryan
 */
public class PostgreSQLHstoreType extends ImmutableType<Map> {

    public PostgreSQLHstoreType() {
        super(Map.class);
    }

    @Override
    public int[] sqlTypes() {
        return new int[]{Types.OTHER};
    }

    @Override
    protected Map get(ResultSet rs, String[] names, SharedSessionContractImplementor session, Object owner) throws SQLException {
        return (Map) rs.getObject(names[0]);
    }

    @Override
    protected void set(PreparedStatement st, Map value, int index, SharedSessionContractImplementor session) throws SQLException {
        if (value == null) {
            st.setNull(index, Types.OTHER);
        } else {
            PGobject holder = new PGobject();
            holder.setType("hstore");
            holder.setValue(HStoreConverter.toString(value));

            st.setObject(index, holder);
        }
    }
}
