package com.vladmihalcea.hibernate.type.range.guava;

import com.google.common.collect.Range;
import com.vladmihalcea.hibernate.type.util.AbstractPostgreSQLIntegrationTest;
import org.hibernate.annotations.TypeDef;
import org.junit.Test;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * @author Edgar Asatryan
 * @author Jan-Willem Gmelig Meyling
 */
public class PostgreSQLGuavaRangeTypeTest extends AbstractPostgreSQLIntegrationTest {

    private final Range<BigDecimal> numeric = Range.closedOpen(new BigDecimal("0.5"), new BigDecimal("0.89"));

    private final Range<Long> int8Range = Range.closedOpen(0L, 18L);

    private final Range<Integer> int4Range = Range.closedOpen(0, 18);

    private final Range<LocalDateTime> localDateTimeRange = Range.closed(
            LocalDateTime.of(2014, Month.APRIL, 28, 16, 0, 49),
            LocalDateTime.of(2015, Month.APRIL, 28, 16, 0, 49));

    private final Range<ZonedDateTime> tsTz = Range.closed(
            OffsetDateTime.of(LocalDateTime.of(2007, Month.DECEMBER, 3, 10, 15, 30), ZoneOffset.ofHours(1)).toZonedDateTime(),
            OffsetDateTime.of(LocalDateTime.of(2008, Month.DECEMBER, 3, 10, 15, 30), ZoneOffset.ofHours(1)).toZonedDateTime());

    private final Range<LocalDate> dateRange = Range.closedOpen(LocalDate.of(1992, Month.JANUARY, 13), LocalDate.of(1995, Month.JANUARY, 13));

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

            ZoneId zone = ar.getRangeZonedDateTime().lowerEndpoint().getZone();

            ZonedDateTime lower = tsTz.lowerEndpoint().withZoneSameInstant(zone);
            ZonedDateTime upper = tsTz.upperEndpoint().withZoneSameInstant(zone);

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
    @TypeDef(name = "range", typeClass = PostgreSQLGuavaRangeType.class, defaultForType = Range.class)
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