package com.vladmihalcea.hibernate.type.range;

import com.vladmihalcea.hibernate.type.ImmutableType;
import org.hibernate.engine.spi.SessionImplementor;
import org.postgresql.util.PGobject;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

/**
 * Maps a {@link Range} object type to a PostgreSQL <a href="https://www.postgresql.org/docs/current/rangetypes.html">range</a>
 * column type.
 * <p>
 * Supported range types:
 * <ul>
 * <li>int4range</li>
 * <li>int8range</li>
 * <li>numrange</li>
 * <li>tsrange</li>
 * <li>tstzrange</li>
 * <li>daterange</li>
 * </ul>
 * <p>
 * For more details about how to use it,
 * check out <a href="https://vladmihalcea.com/map-postgresql-range-column-type-jpa-hibernate/">this article</a>
 * on <a href="https://vladmihalcea.com/">vladmihalcea.com</a>.
 *
 * @author Edgar Asatryan
 * @author Vlad Mihalcea
 */
public class PostgreSQLRangeType extends ImmutableType<Range> {

    public PostgreSQLRangeType() {
        super(Range.class);
    }

    @Override
    public int[] sqlTypes() {
        return new int[]{Types.OTHER};
    }

    @Override
    protected Range get(ResultSet rs, String[] names, SessionImplementor session, Object owner) throws SQLException {
        PGobject pgObject = (PGobject) rs.getObject(names[0]);
        String type = pgObject.getType();
        String value = pgObject.getValue();

        if("int4range".equals(type)) {
            return Range.integerRange(value);
        } else if("int8range".equals(type)) {
            return Range.longRange(value);
        } else if("numrange".equals(type)) {
            return Range.bigDecimalRange(value);
        } else {
            throw new IllegalStateException("The range type [" + type + "] is not supported!");
        }
    }

    @Override
    protected void set(PreparedStatement st, Range range, int index, SessionImplementor session) throws SQLException {

        if (range == null) {
            st.setNull(index, Types.OTHER);
        } else {
            PGobject object = new PGobject();
            object.setType(determineRangeType(range));
            object.setValue(range.asString());

            st.setObject(index, object);
        }
    }

    private static String determineRangeType(Range<?> range) {
        Class<?> clazz = range.getClazz();

        if (clazz.equals(Integer.class)) {
            return "int4range";
        } else if (clazz.equals(Long.class)) {
            return "int8range";
        } else if (clazz.equals(BigDecimal.class)) {
            return "numrange";
        }

        throw new IllegalStateException("The class [" + clazz.getName() + "] is not supported!");
    }
}
