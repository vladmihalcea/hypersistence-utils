package com.vladmihalcea.hibernate.type.range;

import com.vladmihalcea.hibernate.util.AbstractPostgreSQLIntegrationTest;
import com.vladmihalcea.hibernate.util.transaction.JPATransactionFunction;
import com.vladmihalcea.hibernate.util.transaction.JPATransactionVoidFunction;
import org.hibernate.annotations.TypeDef;
import org.junit.Test;

import javax.persistence.*;
import java.math.BigDecimal;

import static com.vladmihalcea.hibernate.type.range.Range.infinite;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * @author Edgar Asatryan
 */
public class PostgreSQLRangeTypeTest extends AbstractPostgreSQLIntegrationTest {

    private final Range<BigDecimal> numeric = Range.bigDecimalRange("[0.5,0.89]");

    private final Range<Long> int8Range = Range.longRange("[0,18)");

    private final Range<Integer> int4RangeEmpty = Range.integerRange("[123,123)");

    private final Range<Integer> int4Range = infinite(Integer.class);

    private final Range<Integer> int4RangeInfinity = Range.integerRange("[123,infinity)");

    @Override
    protected Class<?>[] entities() {
        return new Class[]{
            Restriction.class
        };
    }

    @Test
    public void test() {

        final Restriction _restriction = doInJPA(new JPATransactionFunction<Restriction>() {

            @Override
            public Restriction apply(EntityManager entityManager) {
                entityManager.persist(new Restriction());

                Restriction restriction = new Restriction();
                restriction.setRangeInt(int4Range);
                restriction.setRangeIntEmpty(int4RangeEmpty);
                restriction.setRangeIntInfinity(int4RangeInfinity);
                restriction.setRangeLong(int8Range);
                restriction.setRangeBigDecimal(numeric);
                entityManager.persist(restriction);

                return restriction;
            }
        });

        doInJPA(new JPATransactionFunction<Void>() {

            @Override
            public Void apply(EntityManager entityManager) {
                Restriction restriction = entityManager.find(Restriction.class, _restriction.getId());

                assertEquals(int4Range, restriction.getRangeInt());
                assertEquals(Range.emptyRange(Integer.class), restriction.getRangeIntEmpty());
                assertEquals(int4RangeInfinity, restriction.getRangeIntInfinity());
                assertEquals(int8Range, restriction.getRangeLong());
                assertEquals(numeric, restriction.getRangeBigDecimal());

                return null;
            }
        });
    }

    @Test
    public void testNullRange() {
        final Restriction _restriction = doInJPA(new JPATransactionFunction<Restriction>() {
            @Override
            public Restriction apply(EntityManager entityManager) {
                Restriction restriction = new Restriction();
                entityManager.persist(restriction);

                return restriction;
            }
        });

        doInJPA(new JPATransactionVoidFunction() {
            @Override
            public void accept(EntityManager entityManager) {
                Restriction restriction = entityManager.find(Restriction.class, _restriction.getId());

                assertNull(restriction.getRangeInt());
                assertNull(restriction.getRangeIntInfinity());
                assertNull(restriction.getRangeLong());
                assertNull(restriction.getRangeBigDecimal());
            }
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

        @Column(name = "r_int_empty", columnDefinition = "int4Range")
        private Range<Integer> rangeIntEmpty;

        @Column(name = "r_int_infinity", columnDefinition = "int4Range")
        private Range<Integer> rangeIntInfinity;

        @Column(name = "r_long", columnDefinition = "int8range")
        private Range<Long> rangeLong;

        @Column(name = "r_numeric", columnDefinition = "numrange")
        private Range<BigDecimal> rangeBigDecimal;

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
    }
}