package com.vladmihalcea.hibernate.type.range;

import com.vladmihalcea.hibernate.type.ImmutableType;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.postgresql.util.PGobject;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;

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

    public static final PostgreSQLRangeType INSTANCE = new PostgreSQLRangeType();

    public PostgreSQLRangeType() {
        super(Range.class);
    }

    @Override
    public int[] sqlTypes() {
        return new int[]{Types.OTHER};
    }

    @Override
    protected Range get(ResultSet rs, String[] names, SharedSessionContractImplementor session, Object owner) throws SQLException {
        PGobject pgObject = (PGobject) rs.getObject(names[0]);

        if (pgObject == null) {
            return null;
        }

        String type = pgObject.getType();
        String value = pgObject.getValue();

        switch (type) {
            case "int4range":
                return Range.integerRange(value);
            case "int8range":
                return Range.longRange(value);
            case "numrange":
                return Range.bigDecimalRange(value);
            case "tsrange":
                return Range.localDateTimeRange(value);
            case "tstzrange":
                return Range.zonedDateTimeRange(value);
            case "daterange":
                return Range.localDateRange(value);
            default:
                throw new IllegalStateException("The range type [" + type + "] is not supported!");
        }
    }

    @Override
    protected void set(PreparedStatement st, Range range, int index, SharedSessionContractImplementor session) throws SQLException {

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
        } else if (clazz.equals(LocalDateTime.class)) {
            return "tsrange";
        } else if (clazz.equals(ZonedDateTime.class)) {
            return "tstzrange";
        } else if (clazz.equals(LocalDate.class)) {
            return "daterange";
        }

        throw new IllegalStateException("The class [" + clazz.getName() + "] is not supported!");
    }
}
