package com.vladmihalcea.hibernate.type.range;

import com.vladmihalcea.hibernate.type.util.AbstractPostgreSQLIntegrationTest;
import org.hibernate.annotations.TypeDef;
import org.junit.Test;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static com.vladmihalcea.hibernate.type.range.Range.infinite;
import static com.vladmihalcea.hibernate.type.range.Range.zonedDateTimeRange;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * @author Edgar Asatryan
 */
public class PostgreSQLRangeTypeTest extends AbstractPostgreSQLIntegrationTest {

    private final Range<BigDecimal> numeric = Range.bigDecimalRange("[0.5,0.89]");

    private final Range<Long> int8Range = Range.longRange("[0,18)");

    private final Range<Integer> int4Range = infinite(Integer.class);

    private final Range<LocalDateTime> localDateTimeRange = Range.localDateTimeRange("[2014-04-28 16:00:49,2015-04-28 16:00:49]");

    private final Range<ZonedDateTime> tsTz = zonedDateTimeRange("[\"2007-12-03T10:15:30+01:00\",\"2008-12-03T10:15:30+01:00\"]");

    private final Range<LocalDate> dateRange = Range.localDateRange("[1992-01-13,1995-01-13)");

    @Override
    protected Class<?>[] entities() {
        return new Class[]{
                Restriction.class
        };
    }

    @Test
    public void test() {
        Restriction ageRestrictionInt = doInJPA(entityManager -> {
            entityManager.persist(new Restriction());

            Restriction restriction = new Restriction();
            restriction.setRangeInt(int4Range);
            restriction.setRangeLong(int8Range);
            restriction.setRangeBigDecimal(numeric);
            restriction.setRangeLocalDateTime(localDateTimeRange);
            restriction.setRangeZonedDateTime(tsTz);
            restriction.setLocalDateRange(dateRange);
            entityManager.persist(restriction);

            return restriction;
        });

        doInJPA(entityManager -> {
            Restriction ar = entityManager.find(Restriction.class, ageRestrictionInt.getId());

            assertEquals(int4Range, ar.getRangeInt());
            assertEquals(int8Range, ar.getRangeLong());
            assertEquals(numeric, ar.getRangeBigDecimal());
            assertEquals(localDateTimeRange, ar.getLocalDateTimeRange());
            assertEquals(dateRange, ar.getLocalDateRange());

            ZoneId zone = ar.getRangeZonedDateTime().lower().getZone();

            ZonedDateTime lower = tsTz.lower().withZoneSameInstant(zone);
            ZonedDateTime upper = tsTz.upper().withZoneSameInstant(zone);

            assertEquals(ar.getRangeZonedDateTime(), Range.closed(lower, upper));
        });
    }

    @Test
    public void testNullRange() {
        Restriction ageRestrictionInt = doInJPA(entityManager -> {
            Restriction restriction = new Restriction();
            entityManager.persist(restriction);

            return restriction;
        });

        doInJPA(entityManager -> {
            Restriction ar = entityManager.find(Restriction.class, ageRestrictionInt.getId());

            assertNull(ar.getRangeInt());
            assertNull(ar.getRangeLong());
            assertNull(ar.getRangeBigDecimal());
            assertNull(ar.getLocalDateTimeRange());
            assertNull(ar.getLocalDateRange());
            assertNull(ar.getRangeZonedDateTime());
        });
    }

    @Entity(name = "AgeRestriction")
    @Table(name = "age_restriction")
    @TypeDef(name = "range", typeClass = PostgreSQLRangeType.class, defaultForType = Range.class)
    public static class Restriction {

        @Id
        @GeneratedValue
        private Long id;

        @Column(name = "r_int", columnDefinition = "int4Range")
        private Range<Integer> rangeInt;

        @Column(name = "r_long", columnDefinition = "int8range")
        private Range<Long> rangeLong;

        @Column(name = "r_numeric", columnDefinition = "numrange")
        private Range<BigDecimal> rangeBigDecimal;

        @Column(name = "r_tsrange", columnDefinition = "tsrange")
        private Range<LocalDateTime> rangeLocalDateTime;

        @Column(name = "r_tstzrange", columnDefinition = "tstzrange")
        private Range<ZonedDateTime> rangeZonedDateTime;

        @Column(name = "r_daterange", columnDefinition = "daterange")
        private Range<LocalDate> localDateRange;

        public Long getId() {
            return id;
        }

        public Range<Long> getRangeLong() {
            return rangeLong;
        }

        public void setRangeLong(Range<Long> rangeLong) {
            this.rangeLong = rangeLong;
        }

        public Range<Integer> getRangeInt() {
            return rangeInt;
        }

        public void setRangeInt(Range<Integer> rangeInt) {
            this.rangeInt = rangeInt;
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

        public Range<LocalDate> getLocalDateRange() {
            return localDateRange;
        }

        public void setLocalDateRange(Range<LocalDate> localDateRange) {
            this.localDateRange = localDateRange;
        }
    }
}