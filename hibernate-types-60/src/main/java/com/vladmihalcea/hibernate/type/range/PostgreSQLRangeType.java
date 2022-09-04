package com.vladmihalcea.hibernate.type.range;

import com.vladmihalcea.hibernate.type.ImmutableType;
import com.vladmihalcea.hibernate.type.util.Configuration;
import org.hibernate.HibernateException;
import org.hibernate.annotations.common.reflection.XProperty;
import org.hibernate.annotations.common.reflection.java.JavaXMember;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.usertype.DynamicParameterizedType;
import org.postgresql.util.PGobject;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.Properties;

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
public class PostgreSQLRangeType extends ImmutableType<Range> implements DynamicParameterizedType {

    public static final PostgreSQLRangeType INSTANCE = new PostgreSQLRangeType();

    private Type type;

    public PostgreSQLRangeType() {
        super(Range.class);
    }

    public PostgreSQLRangeType(org.hibernate.type.spi.TypeBootstrapContext typeBootstrapContext) {
        super(Range.class, new Configuration(typeBootstrapContext.getConfigurationSettings()));
    }

    @Override
    public int getSqlType() {
        return Types.OTHER;
    }

    @Override
    protected Range get(ResultSet rs, int position, SharedSessionContractImplementor session, Object owner) throws SQLException {
        PGobject pgObject = (PGobject) rs.getObject(position);

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
                throw new HibernateException(
                    new IllegalStateException("The range type [" + type + "] is not supported!")
                );
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

        throw new HibernateException(
            new IllegalStateException("The class [" + clazz.getName() + "] is not supported!")
        );
    }

    @Override
    public void setParameterValues(Properties parameters) {
        final XProperty xProperty = (XProperty) parameters.get(DynamicParameterizedType.XPROPERTY);
        if (xProperty instanceof JavaXMember) {
            type = ((JavaXMember) xProperty).getJavaType();
        } else {
            type = ((ParameterType) parameters.get(PARAMETER_TYPE)).getReturnedClass();
        }
    }

    public Class<?> getElementType() {
        return type instanceof ParameterizedType ?
                (Class<?>) ((ParameterizedType) type).getActualTypeArguments()[0] : null;
    }

}
