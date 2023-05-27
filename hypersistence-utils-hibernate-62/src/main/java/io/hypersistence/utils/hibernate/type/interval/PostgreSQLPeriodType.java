package io.hypersistence.utils.hibernate.type.interval;

import io.hypersistence.utils.hibernate.type.ImmutableType;
import io.hypersistence.utils.hibernate.type.util.Configuration;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.postgresql.util.PGInterval;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Duration;
import java.time.Period;

/**
 * Maps a Java {@link Period} object to a PostgreSQL Interval column type.
 *
 * @author Jan-Willem Gmelig Meyling
 * @author Vlad Mihalcea
 * @since 2.6.2
 */
public class PostgreSQLPeriodType extends ImmutableType<Period> {

    public static final PostgreSQLPeriodType INSTANCE = new PostgreSQLPeriodType();

    public PostgreSQLPeriodType() {
        super(Period.class);
    }

    public PostgreSQLPeriodType(org.hibernate.type.spi.TypeBootstrapContext typeBootstrapContext) {
        super(Period.class, new Configuration(typeBootstrapContext.getConfigurationSettings()));
    }

    @Override
    protected Period get(ResultSet rs, int position, SharedSessionContractImplementor session, Object owner) throws SQLException {
        final PGInterval interval = (PGInterval) rs.getObject(position);

        if (interval == null) {
            return null;
        }

        final int years = interval.getYears();
        final int months = interval.getMonths();
        final int days = interval.getDays();

        return Period.ofYears(years)
                .plusMonths(months)
                .plusDays(days);
    }

    @Override
    protected void set(PreparedStatement st, Period value, int index, SharedSessionContractImplementor session) throws SQLException {
        if (value == null) {
            st.setNull(index, Types.OTHER);
        } else {
            final int days = value.getDays();
            final int months = value.getMonths();
            final int years = value.getYears();
            st.setObject(index, new PGInterval(years, months, days, 0, 0, 0));
        }
    }

    @Override
    public int getSqlType() {
        return Types.OTHER;
    }

}
