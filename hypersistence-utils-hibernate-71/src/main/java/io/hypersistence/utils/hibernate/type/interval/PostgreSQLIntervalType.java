package io.hypersistence.utils.hibernate.type.interval;

import io.hypersistence.utils.hibernate.type.ImmutableType;
import io.hypersistence.utils.hibernate.type.util.Configuration;
import org.hibernate.HibernateException;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.postgresql.util.PGInterval;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Duration;
import java.time.temporal.ChronoUnit;

/**
 * Maps a Java {@link Duration} object to a PostgreSQL Interval column type.
 * <p>
 * For more details about how to use it, check out <a href="https://vladmihalcea.com/map-postgresql-interval-java-duration-hibernate/">this article</a> on <a href="https://vladmihalcea.com/">vladmihalcea.com</a>.
 *
 * @author Jan-Willem Gmelig Meyling
 * @author Vlad Mihalcea
 * @since 2.5.1
 */
public class PostgreSQLIntervalType extends ImmutableType<Duration> {

    private static final double MICROS_IN_SECOND = 1000000;

    public static final PostgreSQLIntervalType INSTANCE = new PostgreSQLIntervalType();

    public PostgreSQLIntervalType() {
        super(Duration.class);
    }

    public PostgreSQLIntervalType(org.hibernate.type.spi.TypeBootstrapContext typeBootstrapContext) {
        super(Duration.class, new Configuration(typeBootstrapContext.getConfigurationSettings()));
    }

    @Override
    protected Duration get(ResultSet rs, int position, SharedSessionContractImplementor session, Object owner) throws SQLException {
        final PGInterval interval = (PGInterval) rs.getObject(position);

        if (interval == null) {
            return null;
        }

        final int days = interval.getDays();
        final int hours = interval.getHours();
        final int minutes = interval.getMinutes();
        final int seconds = (int) interval.getSeconds();
        final int micros = (int) Math.round((interval.getSeconds() - seconds) * MICROS_IN_SECOND);

        return Duration.ofDays(days)
                .plus(hours, ChronoUnit.HOURS)
                .plus(minutes, ChronoUnit.MINUTES)
                .plus(seconds, ChronoUnit.SECONDS)
                .plus(micros, ChronoUnit.MICROS);
    }

    @Override
    protected void set(PreparedStatement st, Duration value, int index, SharedSessionContractImplementor session) throws SQLException {
        if (value == null) {
            st.setNull(index, Types.OTHER);
        } else {
            final int days = (int) value.toDays();
            final int hours = (int) (value.toHours() % 24);
            final int minutes = (int) (value.toMinutes() % 60);
            final int seconds = (int) (value.getSeconds() % 60);
            final int micros = value.getNano() / 1000;
            final double secondsWithFraction = seconds + (micros / MICROS_IN_SECOND);
            st.setObject(index, new PGInterval(0, 0, days, hours, minutes, secondsWithFraction));
        }
    }

    @Override
    public int getSqlType() {
        return Types.OTHER;
    }

    @Override
    public Duration fromStringValue(CharSequence sequence) throws HibernateException {
        return sequence != null ? Duration.parse(sequence) : null;
    }
}
