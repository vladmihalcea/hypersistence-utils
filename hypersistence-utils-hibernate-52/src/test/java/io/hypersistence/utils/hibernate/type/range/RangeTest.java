package io.hypersistence.utils.hibernate.type.range;

import org.junit.Test;

import static io.hypersistence.utils.hibernate.type.range.Range.integerRange;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.time.LocalDate;

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
    public void localDateTimeTest() {
        assertNotNull(Range.localDateTimeRange("[2019-03-27 16:33:10.1,)"));
        assertNotNull(Range.localDateTimeRange("[2019-03-27 16:33:10.12,)"));
        assertNotNull(Range.localDateTimeRange("[2019-03-27 16:33:10.123,)"));
        assertNotNull(Range.localDateTimeRange("[2019-03-27 16:33:10.1234,)"));
        assertNotNull(Range.localDateTimeRange("[2019-03-27 16:33:10.12345,)"));
        assertNotNull(Range.localDateTimeRange("[2019-03-27 16:33:10.123456,)"));
        assertNotNull(Range.localDateTimeRange("[2019-03-27 16:33:10.123456,infinity)"));
    }

    @Test
    public void zonedDateTimeTest() {
        assertNotNull(Range.zonedDateTimeRange("[2019-03-27 16:33:10.1-06,)"));
        assertNotNull(Range.zonedDateTimeRange("[2019-03-27 16:33:10.12-06,)"));
        assertNotNull(Range.zonedDateTimeRange("[2019-03-27 16:33:10.123-06,)"));
        assertNotNull(Range.zonedDateTimeRange("[2019-03-27 16:33:10.1234-06,)"));
        assertNotNull(Range.zonedDateTimeRange("[2019-03-27 16:33:10.12345-06,)"));
        assertNotNull(Range.zonedDateTimeRange("[2019-03-27 16:33:10.123456-06,)"));
        assertNotNull(Range.zonedDateTimeRange("[2019-03-27 16:33:10.123456+05:30,)"));
        assertNotNull(Range.zonedDateTimeRange("[2019-03-27 16:33:10.123456-06,infinity)"));
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
        Range<LocalDate> empty = Range.localDateRange("empty");

        assertTrue(empty.isEmpty());

        assertFalse(empty.contains(LocalDate.MIN));
        assertFalse(empty.contains((LocalDate) null));
        assertFalse(empty.contains(LocalDate.now()));
        assertFalse(empty.contains(LocalDate.MAX));

        assertNull(empty.upper());
        assertNull(empty.lower());
    }

    @Test
    public void asStringWithEmptyKeyword() {
        Range<Integer> empty = Range.integerRange("empty");

        assertEquals("empty", empty.asString());
    }

    @Test
    public void asStringWithEmptyValue() {
        Range<Integer> empty = Range.integerRange("(5,5)");

        assertEquals("(5,5)", empty.asString());
    }

    @Test
    public void asStringWithInfinity() {
        Range<Integer> infinity = Range.integerRange("(,)");

        assertEquals("(,)", infinity.asString());
    }

    @Test
    public void emptyRangeWithValues() {
        Range<LocalDate> empty = Range.localDateRange("(2019-03-27,2019-03-27)");

        assertTrue(empty.isEmpty());
        assertFalse(empty.contains(LocalDate.MIN));
        assertFalse(empty.contains(LocalDate.now()));
        assertFalse(empty.contains(LocalDate.MAX));

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