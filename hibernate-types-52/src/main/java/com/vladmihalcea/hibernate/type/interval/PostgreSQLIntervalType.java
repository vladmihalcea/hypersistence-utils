package com.vladmihalcea.hibernate.type.interval;

import com.vladmihalcea.hibernate.type.ImmutableType;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.postgresql.util.PGInterval;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Duration;
import java.time.temporal.ChronoUnit;

/**
 * Maps an {@link Duration} object type to a PostgreSQL Interval column type.
 * <p>
 *
 * @author Jan-Willem Gmelig Meyling
 */
public class PostgreSQLIntervalType extends ImmutableType<Duration> {

    public static final PostgreSQLIntervalType INSTANCE = new PostgreSQLIntervalType();

    public PostgreSQLIntervalType() {
        super(Duration.class);
    }

    @Override
    protected Duration get(ResultSet rs, String[] names, SharedSessionContractImplementor session, Object owner) throws SQLException {
        final PGInterval pgi = (PGInterval) rs.getObject(names[0]);

        if (pgi == null) {
            return null;
        }

        final int days = pgi.getDays();
        final int hours = pgi.getHours();
        final int mins = pgi.getMinutes();
        final double secs = pgi.getSeconds();

        return Duration.ofDays(days)
            .plus(hours, ChronoUnit.HOURS)
            .plus(mins, ChronoUnit.MINUTES)
            .plus((long) Math.floor(secs), ChronoUnit.SECONDS);
    }

    @Override
    protected void set(PreparedStatement st, Duration value, int index, SharedSessionContractImplementor session) throws SQLException {
        if (value == null) {
            st.setNull(index, Types.OTHER);
        } else {
            final int days = (int) value.toDays();
            final int hours = (int) (value.toHours() % 24);
            final int mins = (int) (value.toMinutes() % 60);
            final double secs = value.getSeconds() % 60;
            st.setObject(index, new PGInterval(0, 0, days, hours, mins, secs));
        }
    }

    @Override
    public int[] sqlTypes() {
        return new int[] { Types.OTHER };
    }

}
