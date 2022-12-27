package io.hypersistence.utils.hibernate.type.range.guava;

import com.google.common.base.Throwables;
import com.google.common.collect.Range;
import com.google.common.collect.Ranges;
import io.hypersistence.utils.hibernate.util.AbstractPostgreSQLIntegrationTest;
import io.hypersistence.utils.hibernate.util.transaction.JPATransactionFunction;
import io.hypersistence.utils.hibernate.util.transaction.JPATransactionVoidFunction;
import org.hibernate.annotations.TypeDef;
import org.junit.Test;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EntityManager;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Table;
import java.math.BigDecimal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author Edgar Asatryan
 * @author Jan-Willem Gmelig Meyling
 */
public class PostgreSQLGuavaRangeTypeTest extends AbstractPostgreSQLIntegrationTest {

    private final Range<BigDecimal> numeric = Ranges.closedOpen(new BigDecimal("0.5"), new BigDecimal("0.89"));

    private final Range<Long> int8Range = Ranges.closedOpen(0L, 18L);

    private final Range<Integer> int4Range = Ranges.closedOpen(0, 18);

    @Override
    protected Class<?>[] entities() {
        return new Class[]{
                Restriction.class
        };
    }

    @Test
    public void test() {

        final Restriction ageRestrictionInt = doInJPA(new JPATransactionFunction<Restriction>() {

            @Override
            public Restriction apply(EntityManager entityManager) {
                entityManager.persist(new Restriction());

                Restriction restriction = new Restriction();
                restriction.setRangeInt(int4Range);
                restriction.setRangeLong(int8Range);
                restriction.setRangeBigDecimal(numeric);
                entityManager.persist(restriction);

                return restriction;
            }
        });

        doInJPA(new JPATransactionFunction<Void>() {

            @Override
            public Void apply(EntityManager entityManager) {
                Restriction ar = entityManager.find(Restriction.class, ageRestrictionInt.getId());

                assertEquals(int4Range, ar.getRangeInt());
                assertEquals(int8Range, ar.getRangeLong());
                assertEquals(numeric, ar.getRangeBigDecimal());

                return null;
            }
        });
    }

    @Test
    public void testNullRange() {
        final Restriction ageRestrictionInt = doInJPA(new JPATransactionFunction<Restriction>() {
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
                Restriction ar = entityManager.find(Restriction.class, ageRestrictionInt.getId());

                assertNull(ar.getRangeInt());
                assertNull(ar.getRangeLong());
                assertNull(ar.getRangeBigDecimal());
            }
        });
    }

    @Test
    public void testUnboundedRangeIsRejected() {
        try {
            final Restriction ageRestrictionInt = doInJPA(new JPATransactionFunction<Restriction>() {
                @Override
                public Restriction apply(EntityManager entityManager) {
                    Restriction restriction = new Restriction();
                    restriction.setRangeInt(Ranges.<Integer>all());
                    entityManager.persist(restriction);

                    return restriction;
                }
            });
            fail("An unbounded range should throw an exception!");
        } catch (Exception e) {
            Throwable rootCause = Throwables.getRootCause(e);
            assertTrue(rootCause instanceof IllegalArgumentException);
            assertTrue(rootCause.getMessage().contains("doesn't have any upper or lower bound!"));
        }
    }

    @Test
    public void testUnboundedRangeStringIsRejected() {
        try {
            PostgreSQLGuavaRangeType instance = PostgreSQLGuavaRangeType.INSTANCE;
            instance.integerRange("(,)");
            fail("An unbounded range string should throw an exception!");
        } catch (Exception e) {
            Throwable rootCause = Throwables.getRootCause(e);
            assertTrue(rootCause instanceof IllegalArgumentException);
            assertTrue(rootCause.getMessage().contains("Cannot find bound type"));
        }
    }

    @Test
    public void testSingleBoundedRanges() {
        PostgreSQLGuavaRangeType instance = PostgreSQLGuavaRangeType.INSTANCE;

        assertEquals("(,)", instance.asString(Ranges.all()));
        assertEquals("(1,)", instance.asString(Ranges.greaterThan(1)));
        assertEquals("[2,)", instance.asString(Ranges.atLeast(2)));
        assertEquals("(,3)", instance.asString(Ranges.lessThan(3)));
        assertEquals("(,4]", instance.asString(Ranges.atMost(4)));

        assertEquals(Ranges.greaterThan(5), instance.integerRange("(5,)"));
        assertEquals(Ranges.atLeast(6), instance.integerRange("[6,)"));
        assertEquals(Ranges.lessThan(7), instance.integerRange("(,7)"));
        assertEquals(Ranges.atMost(8), instance.integerRange("(,8]"));
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
    }
}
