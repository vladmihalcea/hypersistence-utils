package com.vladmihalcea.hibernate.type.range;

import org.junit.Test;

import static com.vladmihalcea.hibernate.type.range.Range.integerRange;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.*;
import static org.junit.Assert.assertFalse;

/**
 * @author Edgar Asatryan
 */
public class RangeTest {

    @Test
    public void ofStringTest() {
        assertThat(integerRange("[1,3]").lower(), is(1));
        assertThat(integerRange("[1,3]").upper(), is(3));
        assertThat(integerRange("[1,3]").isUpperBoundClosed(), is(true));
        assertThat(integerRange("[1,3]").isLowerBoundClosed(), is(true));

        assertThat(integerRange("[,3]").lower(), is(nullValue()));
        assertThat(integerRange("[,3]").upper(), is(3));
        assertThat(integerRange("[,3]").hasLowerBound(), is(false));
        assertThat(integerRange("[,3]").hasUpperBound(), is(true));
        assertThat(integerRange("[,3]").isUpperBoundClosed(), is(true));
        assertThat(integerRange("[,3]").isLowerBoundClosed(), is(false));

        assertThat(integerRange("[,]").lower(), is(nullValue()));
        assertThat(integerRange("[,]").upper(), is(nullValue()));
        assertThat(integerRange("[,]").hasLowerBound(), is(false));
        assertThat(integerRange("[,]").hasUpperBound(), is(false));
        assertThat(integerRange("[,]").isUpperBoundClosed(), is(false));
        assertThat(integerRange("[,]").isLowerBoundClosed(), is(false));

        assertThat(integerRange("(-5,5]").isUpperBoundClosed(), is(true));
        assertThat(integerRange("(-5,5]").isLowerBoundClosed(), is(false));
        assertThat(integerRange("(,)").contains(integerRange("empty")), is(true));

        assertThat(integerRange("empty").contains(integerRange("(,)")), is(false));
    }

    @Test
    public void containsRange() {
        assertThat(integerRange("[-5,5]").contains(integerRange("[-4,4]")), is(true));
        assertThat(integerRange("[-5,5]").contains(integerRange("[-5,5]")), is(true));
        assertThat(integerRange("(-5,5]").contains(integerRange("[-4,4]")), is(true));
        assertThat(integerRange("(-5,5]").contains(integerRange("(-4,4]")), is(true));

        assertThat(integerRange("(,)").contains(integerRange("(,)")), is(true));
        assertThat(integerRange("(5,)").contains(integerRange("(6,)")), is(true));
        assertThat(integerRange("(,5)").contains(integerRange("(,4)")), is(true));
        assertThat(integerRange("(,)").contains(integerRange("(6,)")), is(true));
        assertThat(integerRange("(,)").contains(integerRange("(,6)")), is(true));
    }

    @Test
    public void emptyInfinityEquality() {
        assertEquals(integerRange("empty"), integerRange("empty"));
        assertEquals(integerRange("(infinity,infinity)"), integerRange("(infinity,infinity)"));
        assertEquals(integerRange("(,)"), integerRange("(infinity,infinity)"));
        assertEquals(integerRange("(infinity,infinity)"), integerRange("(,)"));

        assertNotEquals(integerRange("empty"), integerRange("(infinity,infinity)"));
        assertNotEquals(integerRange("empty"), integerRange("(,)"));
        assertNotEquals(integerRange("empty"), integerRange("(5,5)"));
    }

    @Test
    public void emptyRangeWithEmptyKeyword() {
        Range<Long> empty = Range.longRange("empty");

        assertTrue(empty.isEmpty());

        assertFalse(empty.contains(Long.MIN_VALUE));
        assertFalse(empty.contains(Long.valueOf(0)));
        assertFalse(empty.contains(Long.MAX_VALUE));

        assertNull(empty.upper());
        assertNull(empty.lower());
    }

    @Test
    public void emptyRangeWithValues() {
        Range<Long> empty = Range.longRange("(1,1)");

        assertTrue(empty.isEmpty());
        assertFalse(empty.contains(Long.MIN_VALUE));
        assertFalse(empty.contains(Long.valueOf(0)));
        assertFalse(empty.contains(Long.MAX_VALUE));

        assertTrue(integerRange("(5,5)").isEmpty());
    }

    @Test
    public void notEmptyWithValues() {
        assertFalse(integerRange("(5,)").isEmpty());
        assertFalse(integerRange("(5,5]").isEmpty());
        assertFalse(integerRange("(,5)").isEmpty());
        assertFalse(integerRange("(,)").isEmpty());
    }
}