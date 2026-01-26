package io.hypersistence.utils.hibernate.type.range;

import io.hypersistence.utils.hibernate.util.AbstractPostgreSQLIntegrationTest;
import jakarta.persistence.*;
import org.hibernate.annotations.Type;
import org.junit.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static io.hypersistence.utils.hibernate.type.range.Range.infinite;
import static io.hypersistence.utils.hibernate.type.range.Range.zonedDateTimeRange;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * @author Edgar Asatryan
 */
public class PostgreSQLRangeTypeTest extends AbstractPostgreSQLIntegrationTest {

    private final Range<BigDecimal> numeric = Range.bigDecimalRange("[0.5,0.89]");

    private final Range<Long> int8Range = Range.longRange("[0,18)");

    private final Range<Integer> int4Range = infinite(Integer.class);

    private final Range<Integer> int4RangeEmpty = Range.integerRange("[123,123)");

    private final Range<Integer> int4RangeInfinity = Range.integerRange("[123,infinity)");

    private final Range<LocalDateTime> localDateTimeRange = Range.localDateTimeRange("[2014-04-28 16:00:49,2015-04-28 16:00:49]");

    private final Range<ZonedDateTime> tsTz = zonedDateTimeRange("[\"2007-12-03T10:15:30+01:00\",\"2008-12-03T10:15:30+01:00\"]");

    private final Range<ZonedDateTime> tsTzEmpty = zonedDateTimeRange("empty");

    private final Range<ZonedDateTime> infinityTsTz = zonedDateTimeRange("[\"2007-12-03T10:15:30+01:00\",infinity)");

    private final Range<LocalDate> dateRange = Range.localDateRange("[1992-01-13,1995-01-13)");

    @Override
    protected Class<?>[] entities() {
        return new Class[]{
                Restriction.class
        };
    }

    @Test
    public void test() {
        Restriction _restriction = doInJPA(entityManager -> {
            entityManager.persist(new Restriction());

            Restriction restriction = new Restriction();
            restriction.setRangeInt(int4Range);
            restriction.setRangeIntEmpty(int4RangeEmpty);
            restriction.setRangeIntInfinity(int4RangeInfinity);
            restriction.setRangeLong(int8Range);
            restriction.setRangeBigDecimal(numeric);
            restriction.setRangeLocalDateTime(localDateTimeRange);
            restriction.setRangeZonedDateTime(tsTz);
            restriction.setRangeZonedDateTimeInfinity(infinityTsTz);
            restriction.setRangeZonedDateTimeEmpty(tsTzEmpty);
            restriction.setRangeLocalDate(dateRange);
            entityManager.persist(restriction);

            return restriction;
        });

        doInJPA(entityManager -> {
            Restriction restriction = entityManager.find(Restriction.class, _restriction.getId());

            assertEquals(int4Range, restriction.getRangeInt());
            assertEquals(Range.emptyRange(Integer.class), restriction.getRangeIntEmpty());
            assertEquals(int4RangeInfinity, restriction.getRangeIntInfinity());
            assertEquals(int8Range, restriction.getRangeLong());
            assertEquals(numeric, restriction.getRangeBigDecimal());
            assertEquals(localDateTimeRange, restriction.getLocalDateTimeRange());
            assertEquals(dateRange, restriction.getRangeLocalDate());

            ZoneId zone = restriction.getRangeZonedDateTime().lower().getZone();

            ZonedDateTime lower = tsTz.lower().withZoneSameInstant(zone);
            ZonedDateTime upper = tsTz.upper().withZoneSameInstant(zone);
            assertEquals(restriction.getRangeZonedDateTime(), Range.closed(lower, upper));

            lower = infinityTsTz.lower().withZoneSameInstant(zone);
            assertEquals(restriction.getRangeZonedDateTimeInfinity(), Range.closedInfinite(lower));
        });
    }

    @Test
    public void testNullRange() {
        Restriction _restriction = doInJPA(entityManager -> {
            Restriction restriction = new Restriction();
            entityManager.persist(restriction);

            return restriction;
        });

        doInJPA(entityManager -> {
            Restriction restriction = entityManager.find(Restriction.class, _restriction.getId());

            assertNull(restriction.getRangeInt());
            assertNull(restriction.getRangeIntEmpty());
            assertNull(restriction.getRangeIntInfinity());
            assertNull(restriction.getRangeLong());
            assertNull(restriction.getRangeBigDecimal());
            assertNull(restriction.getLocalDateTimeRange());
            assertNull(restriction.getRangeLocalDate());
            assertNull(restriction.getRangeZonedDateTime());
            assertNull(restriction.getRangeZonedDateTimeInfinity());
            assertNull(restriction.getRangeZonedDateTimeEmpty());
        });
    }

    @Entity(name = "AgeRestriction")
    @Table(name = "age_restriction")
    public static class Restriction {

        @Id
        @GeneratedValue
        private Long id;

        @Type(PostgreSQLRangeType.class)
        @Column(name = "r_int", columnDefinition = "int4Range")
        private Range<Integer> rangeInt;

        @Type(PostgreSQLRangeType.class)
        @Column(name = "r_int_empty", columnDefinition = "int4Range")
        private Range<Integer> rangeIntEmpty;

        @Type(PostgreSQLRangeType.class)
        @Column(name = "r_int_infinity", columnDefinition = "int4Range")
        private Range<Integer> rangeIntInfinity;

        @Type(PostgreSQLRangeType.class)
        @Column(name = "r_long", columnDefinition = "int8range")
        private Range<Long> rangeLong;

        @Type(PostgreSQLRangeType.class)
        @Column(name = "r_numeric", columnDefinition = "numrange")
        private Range<BigDecimal> rangeBigDecimal;

        @Type(PostgreSQLRangeType.class)
        @Column(name = "r_ts", columnDefinition = "tsrange")
        private Range<LocalDateTime> rangeLocalDateTime;

        @Type(PostgreSQLRangeType.class)
        @Column(name = "r_ts_tz", columnDefinition = "tstzrange")
        private Range<ZonedDateTime> rangeZonedDateTime;

        @Type(PostgreSQLRangeType.class)
        @Column(name = "r_ts_tz_infinity", columnDefinition = "tstzrange")
        private Range<ZonedDateTime> rangeZonedDateTimeInfinity;

        @Type(PostgreSQLRangeType.class)
        @Column(name = "r_ts_tz_empty", columnDefinition = "tstzrange")
        private Range<ZonedDateTime> rangeZonedDateTimeEmpty;

        @Type(PostgreSQLRangeType.class)
        @Column(name = "r_date", columnDefinition = "daterange")
        private Range<LocalDate> rangeLocalDate;

        public Long getId() {
            return id;
        }

        public Range<Integer> getRangeInt() {
            return rangeInt;
        }

        public void setRangeInt(Range<Integer> rangeInt) {
            this.rangeInt = rangeInt;
        }

        public Range<Integer> getRangeIntEmpty() {
            return rangeIntEmpty;
        }

        public void setRangeIntEmpty(Range<Integer> rangeIntEmpty) {
            this.rangeIntEmpty = rangeIntEmpty;
        }

        public Range<Integer> getRangeIntInfinity() {
            return rangeIntInfinity;
        }

        public void setRangeIntInfinity(Range<Integer> rangeIntInfinity) {
            this.rangeIntInfinity = rangeIntInfinity;
        }

        public Range<Long> getRangeLong() {
            return rangeLong;
        }

        public void setRangeLong(Range<Long> rangeLong) {
            this.rangeLong = rangeLong;
        }

        public Range<BigDecimal> getRangeBigDecimal() {
            return rangeBigDecimal;
        }

        public void setRangeBigDecimal(Range<BigDecimal> rangeBigDecimal) {
            this.rangeBigDecimal = rangeBigDecimal;
        }

        public Range<LocalDateTime> getLocalDateTimeRange() {
            return rangeLocalDateTime;
        }

        public void setRangeLocalDateTime(Range<LocalDateTime> rangeLocalDateTime) {
            this.rangeLocalDateTime = rangeLocalDateTime;
        }

        public Range<ZonedDateTime> getRangeZonedDateTime() {
            return rangeZonedDateTime;
        }

        public void setRangeZonedDateTime(Range<ZonedDateTime> rangeZonedDateTime) {
            this.rangeZonedDateTime = rangeZonedDateTime;
        }

        public Range<ZonedDateTime> getRangeZonedDateTimeInfinity() {
            return rangeZonedDateTimeInfinity;
        }

        public Range<ZonedDateTime> getRangeZonedDateTimeEmpty() {
            return rangeZonedDateTimeEmpty;
        }

        public void setRangeZonedDateTimeEmpty(Range<ZonedDateTime> rangeZonedDateTimeEmpty) {
            this.rangeZonedDateTimeEmpty = rangeZonedDateTimeEmpty;
        }

        public void setRangeZonedDateTimeInfinity(Range<ZonedDateTime> rangeZonedDateTimeInfinity) {
            this.rangeZonedDateTimeInfinity = rangeZonedDateTimeInfinity;
        }

        public Range<LocalDate> getRangeLocalDate() {
            return rangeLocalDate;
        }

        public void setRangeLocalDate(Range<LocalDate> rangeLocalDate) {
            this.rangeLocalDate = rangeLocalDate;
        }
    }
}