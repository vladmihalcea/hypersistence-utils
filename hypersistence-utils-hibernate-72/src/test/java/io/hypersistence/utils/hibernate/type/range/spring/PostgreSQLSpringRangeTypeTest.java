package io.hypersistence.utils.hibernate.type.range.spring;

import io.hypersistence.utils.hibernate.util.AbstractPostgreSQLIntegrationTest;
import jakarta.persistence.*;
import org.hibernate.annotations.Type;
import org.junit.Test;
import org.springframework.data.domain.Range;

import java.math.BigDecimal;
import java.time.*;

import static io.hypersistence.utils.hibernate.type.range.spring.PostgreSQLSpringRangeType.integerRange;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class PostgreSQLSpringRangeTypeTest extends AbstractPostgreSQLIntegrationTest {

    private static final ZoneOffset DEFAULT_OFFSET = OffsetDateTime.now().getOffset();

    private final Range<BigDecimal> numeric = Range.rightOpen(new BigDecimal("0.5"), new BigDecimal("0.89"));

    private final Range<BigDecimal> numericEmpty = Range.rightOpen(BigDecimal.ZERO, BigDecimal.ZERO);

    private final Range<Long> int8Range = Range.rightOpen(0L, 18L);

    private final Range<Long> int8RangeEmpty = Range.rightOpen(Long.MIN_VALUE, Long.MIN_VALUE);

    private final Range<Integer> int4Range = Range.rightOpen(0, 18);

    private final Range<Integer> int4RangeEmpty = Range.rightOpen(Integer.MIN_VALUE, Integer.MIN_VALUE);

    private final Range<LocalDateTime> localDateTimeRange = Range.rightOpen(
            LocalDateTime.of(2014, Month.APRIL, 28, 16, 0, 49),
            LocalDateTime.of(2015, Month.APRIL, 28, 16, 0, 49));

    private final Range<LocalDateTime> localDateTimeRangeEmpty = Range.rightOpen(LocalDateTime.MIN, LocalDateTime.MIN);

    private final Range<ZonedDateTime> tsTz = Range.rightOpen(
            OffsetDateTime.of(LocalDateTime.of(2007, Month.DECEMBER, 3, 10, 15, 30), ZoneOffset.ofHours(1)).toZonedDateTime(),
            OffsetDateTime.of(LocalDateTime.of(2008, Month.DECEMBER, 3, 10, 15, 30), ZoneOffset.ofHours(1)).toZonedDateTime());

    private final Range<ZonedDateTime> tsTzEmpty = Range.rightOpen(OffsetDateTime.MIN.toZonedDateTime(), OffsetDateTime.MIN.toZonedDateTime());

    private final Range<OffsetDateTime> tsTzO = Range.rightOpen(
            OffsetDateTime.of(LocalDateTime.of(2007, Month.MAY, 3, 10, 15, 30), DEFAULT_OFFSET),
            OffsetDateTime.of(LocalDateTime.of(2008, Month.MAY, 3, 10, 15, 30), DEFAULT_OFFSET)
    );

//    private final Range<OffsetDateTime> tsTz0Empty = Range.rightOpen(OffsetDateTime.MIN.toZonedDateTime().)

    private final Range<LocalDate> dateRange = Range.rightOpen(LocalDate.of(1992, Month.JANUARY, 13), LocalDate.of(1995, Month.JANUARY, 13));

    private final Range<LocalDate> dateRangeEmpty = Range.rightOpen(LocalDate.MIN, LocalDate.MIN);

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
            restriction.setRangeIntEmpty(int4RangeEmpty);
            restriction.setRangeLong(int8Range);
            restriction.setRangeLongEmpty(int8RangeEmpty);
            restriction.setRangeBigDecimal(numeric);
            restriction.setRangeBigDecimalEmpty(numericEmpty);
            restriction.setRangeLocalDateTime(localDateTimeRange);
            restriction.setRangeLocalDateTimeEmpty(localDateTimeRangeEmpty);
            restriction.setRangeZonedDateTime(tsTz);
            restriction.setRangeZonedDateTimeEmpty(tsTzEmpty);
            restriction.setRangeLocalDate(dateRange);
            restriction.setRangeLocalDateEmpty(dateRangeEmpty);
            restriction.setOffsetZonedDateTime(tsTzO);
            entityManager.persist(restriction);

            return restriction;
        });

        doInJPA(entityManager -> {
            Restriction ar = entityManager.find(Restriction.class, ageRestrictionInt.getId());

            assertEquals(int4Range, ar.getRangeInt());
            assertEquals(int4RangeEmpty, ar.getRangeIntEmpty());
            assertEquals(int8Range, ar.getRangeLong());
            assertEquals(int8RangeEmpty, ar.getRangeLongEmpty());
            assertEquals(numeric, ar.getRangeBigDecimal());
            assertEquals(numericEmpty, ar.getRangeBigDecimalEmpty());
            assertEquals(localDateTimeRange, ar.getRangeLocalDateTime());
            assertEquals(localDateTimeRangeEmpty, ar.getRangeLocalDateTimeEmpty());
            assertEquals(dateRange, ar.getRangeLocalDate());
            assertEquals(dateRangeEmpty, ar.getRangeLocalDateEmpty());

            ZoneId zone = ar.getRangeZonedDateTime().getLowerBound().getValue().get().getZone();
            ZonedDateTime lower = tsTz.getLowerBound().getValue().get().withZoneSameInstant(zone);
            ZonedDateTime upper = tsTz.getUpperBound().getValue().get().withZoneSameInstant(zone);
            assertEquals(ar.getRangeZonedDateTime(), Range.rightOpen(lower, upper));

            ZoneId zoneEmpty = ar.getRangeZonedDateTimeEmpty().getLowerBound().getValue().get().getZone();
            ZonedDateTime lowerEmpty = tsTzEmpty.getLowerBound().getValue().get().withZoneSameInstant(zoneEmpty);
            ZonedDateTime upperEmpty = tsTzEmpty.getUpperBound().getValue().get().withZoneSameInstant(zoneEmpty);
            assertEquals(ar.getRangeZonedDateTimeEmpty(), Range.rightOpen(lowerEmpty, upperEmpty));

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
            assertNull(ar.getRangeIntEmpty());
            assertNull(ar.getRangeLong());
            assertNull(ar.getRangeLongEmpty());
            assertNull(ar.getRangeBigDecimal());
            assertNull(ar.getRangeBigDecimalEmpty());
            assertNull(ar.getRangeLocalDateTime());
            assertNull(ar.getRangeLocalDateTimeEmpty());
            assertNull(ar.getRangeLocalDate());
            assertNull(ar.getRangeLocalDateEmpty());
            assertNull(ar.getRangeZonedDateTime());
            assertNull(ar.getRangeZonedDateTimeEmpty());
        });
    }

    @Test
    public void testUnboundedRangeIsRejected() {
        Restriction ageRestrictionInt = doInJPA(entityManager -> {
            Restriction restriction = new Restriction();
            restriction.setRangeInt(Range.unbounded());
            entityManager.persist(restriction);

            return restriction;
        });


        doInJPA(entityManager -> {
            Restriction ar = entityManager.find(Restriction.class, ageRestrictionInt.getId());
            assertEquals(ar.getRangeInt(), Range.unbounded());
        });
    }

    @Test
    public void testUnboundedRangeStringIsRejected() {
        PostgreSQLSpringRangeType instance = PostgreSQLSpringRangeType.INSTANCE;
        assertEquals(Range.unbounded(), integerRange("(,)"));
    }

    @Test
    public void testSingleBoundedRanges() {
        PostgreSQLSpringRangeType instance = PostgreSQLSpringRangeType.INSTANCE;

        assertEquals("(,)", instance.asString(Range.unbounded()));
        assertEquals("(1,)", instance.asString(Range.rightUnbounded(Range.Bound.exclusive(1))));
        assertEquals("[2,)", instance.asString(Range.rightUnbounded(Range.Bound.inclusive(2))));
        assertEquals("(,3)", instance.asString(Range.leftUnbounded(Range.Bound.exclusive(3))));
        assertEquals("(,4]", instance.asString(Range.leftUnbounded(Range.Bound.inclusive(4))));

        assertEquals(Range.rightUnbounded(Range.Bound.exclusive(5)), integerRange("(5,)"));
        assertEquals(Range.rightUnbounded(Range.Bound.inclusive(6)), integerRange("[6,)"));
        assertEquals(Range.leftUnbounded(Range.Bound.exclusive(7)), integerRange("(,7)"));
        assertEquals(Range.leftUnbounded(Range.Bound.inclusive(8)), integerRange("(,8]"));
    }

    @Entity(name = "AgeRestriction")
    @Table(name = "age_restriction")
    public static class Restriction {

        @Id
        @GeneratedValue
        private Long id;

        @Type(PostgreSQLSpringRangeType.class)
        @Column(name = "r_int", columnDefinition = "int4Range")
        private Range<Integer> rangeInt;

        @Type(PostgreSQLSpringRangeType.class)
        @Column(name = "r_int_empty", columnDefinition = "int4Range")
        private Range<Integer> rangeIntEmpty;

        @Type(PostgreSQLSpringRangeType.class)
        @Column(name = "r_long", columnDefinition = "int8range")
        private Range<Long> rangeLong;

        @Type(PostgreSQLSpringRangeType.class)
        @Column(name = "r_long_empty", columnDefinition = "int8range")
        private Range<Long> rangeLongEmpty;

        @Type(PostgreSQLSpringRangeType.class)
        @Column(name = "r_numeric", columnDefinition = "numrange")
        private Range<BigDecimal> rangeBigDecimal;

        @Type(PostgreSQLSpringRangeType.class)
        @Column(name = "r_numeric_empty", columnDefinition = "numrange")
        private Range<BigDecimal> rangeBigDecimalEmpty;

        @Type(PostgreSQLSpringRangeType.class)
        @Column(name = "r_tsrange", columnDefinition = "tsrange")
        private Range<LocalDateTime> rangeLocalDateTime;

        @Type(PostgreSQLSpringRangeType.class)
        @Column(name = "r_tsrange_empty", columnDefinition = "tsrange")
        private Range<LocalDateTime> rangeLocalDateTimeEmpty;

        @Type(PostgreSQLSpringRangeType.class)
        @Column(name = "r_tstzrange", columnDefinition = "tstzrange")
        private Range<ZonedDateTime> rangeZonedDateTime;

        @Type(PostgreSQLSpringRangeType.class)
        @Column(name = "r_tstzrange_empty", columnDefinition = "tstzrange")
        private Range<ZonedDateTime> rangeZonedDateTimeEmpty;

        @Type(PostgreSQLSpringRangeType.class)
        @Column(name = "r_otstzrange", columnDefinition = "tstzrange")
        private Range<OffsetDateTime> offsetZonedDateTime;

        @Type(PostgreSQLSpringRangeType.class)
        @Column(name = "r_daterange", columnDefinition = "daterange")
        private Range<LocalDate> rangeLocalDate;

        @Type(PostgreSQLSpringRangeType.class)
        @Column(name = "r_daterange_empty", columnDefinition = "daterange")
        private Range<LocalDate> rangeLocalDateEmpty;

        public Long getId() {
            return id;
        }

        public Range<Long> getRangeLong() {
            return rangeLong;
        }

        public void setRangeLong(Range<Long> rangeLong) {
            this.rangeLong = rangeLong;
        }

        public Range<Long> getRangeLongEmpty() {
            return rangeLongEmpty;
        }

        public void setRangeLongEmpty(Range<Long> rangeLongEmpty) {
            this.rangeLongEmpty = rangeLongEmpty;
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
        
        public Range<BigDecimal> getRangeBigDecimal() {
            return rangeBigDecimal;
        }

        public void setRangeBigDecimal(Range<BigDecimal> rangeBigDecimal) {
            this.rangeBigDecimal = rangeBigDecimal;
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

        public Range<LocalDate> getRangeLocalDate() {
            return rangeLocalDate;
        }

        public void setRangeLocalDate(Range<LocalDate> localDateRange) {
            this.rangeLocalDate = localDateRange;
        }

        public Range<OffsetDateTime> getOffsetZonedDateTime() {
            return offsetZonedDateTime;
        }

        public void setOffsetZonedDateTime(Range<OffsetDateTime> offsetZonedDateTime) {
            this.offsetZonedDateTime = offsetZonedDateTime;
        }

        public Range<BigDecimal> getRangeBigDecimalEmpty() {
            return rangeBigDecimalEmpty;
        }

        public void setRangeBigDecimalEmpty(Range<BigDecimal> rangeBigDecimalEmpty) {
            this.rangeBigDecimalEmpty = rangeBigDecimalEmpty;
        }

        public Range<LocalDateTime> getRangeLocalDateTime() {
            return rangeLocalDateTime;
        }

        public Range<LocalDateTime> getRangeLocalDateTimeEmpty() {
            return rangeLocalDateTimeEmpty;
        }

        public void setRangeLocalDateTimeEmpty(Range<LocalDateTime> rangeLocalDateTimeEmpty) {
            this.rangeLocalDateTimeEmpty = rangeLocalDateTimeEmpty;
        }

        public Range<ZonedDateTime> getRangeZonedDateTimeEmpty() {
            return rangeZonedDateTimeEmpty;
        }

        public void setRangeZonedDateTimeEmpty(Range<ZonedDateTime> rangeZonedDateTimeEmpty) {
            this.rangeZonedDateTimeEmpty = rangeZonedDateTimeEmpty;
        }

        public Range<LocalDate> getRangeLocalDateEmpty() {
            return rangeLocalDateEmpty;
        }

        public void setRangeLocalDateEmpty(Range<LocalDate> localDateRangeEmpty) {
            this.rangeLocalDateEmpty = localDateRangeEmpty;
        }
    }
}
