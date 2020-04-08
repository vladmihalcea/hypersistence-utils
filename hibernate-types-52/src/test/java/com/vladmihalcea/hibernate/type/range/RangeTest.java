package com.vladmihalcea.hibernate.type.range;

import org.junit.Test;

import static com.vladmihalcea.hibernate.type.range.Range.integerRange;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

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
        assertThat(integerRange("(infinity,infinity)").lower(), is(nullValue()));
        assertThat(integerRange("(infinity,infinity)").upper(), is(nullValue()));
        assertThat(integerRange("[,]").hasLowerBound(), is(false));
        assertThat(integerRange("[,]").hasUpperBound(), is(false));
        assertThat(integerRange("[,]").isUpperBoundClosed(), is(false));
        assertThat(integerRange("[,]").isLowerBoundClosed(), is(false));

        assertThat(integerRange("(-5,5]").isUpperBoundClosed(), is(true));
        assertThat(integerRange("(-5,5]").isLowerBoundClosed(), is(false));
    }

    @Test
    public void containsRange() {
        assertThat(integerRange("[-5,5]").contains(integerRange("[-4,4]")), is(true));
        assertThat(integerRange("[-5,5]").contains(integerRange("[-5,5]")), is(true));
        assertThat(integerRange("(-5,5]").contains(integerRange("[-4,4]")), is(true));
        assertThat(integerRange("(-5,5]").contains(integerRange("(-4,4]")), is(true));

        assertThat(integerRange("(,)").contains(integerRange("(,)")), is(true));
        assertThat(integerRange("(5,)").contains(integerRange("(6,)")), is(true));
        assertThat(integerRange("(5,infinity)").contains(integerRange("(6,infinity)")), is(true));
        assertThat(integerRange("(,5)").contains(integerRange("(,4)")), is(true));
        assertThat(integerRange("(infinity,5)").contains(integerRange("(infinity,4)")), is(true));
        assertThat(integerRange("(,)").contains(integerRange("(6,)")), is(true));
        assertThat(integerRange("(infinity,infinity)").contains(integerRange("(6,infinity)")), is(true));
        assertThat(integerRange("(infinity,infinity)").contains(integerRange("(infinity,6)")), is(true));
    }
    
    @Test
    public void zonedDateTimeTest() {
    	assertNotNull(Range.zonedDateTimeRange("[2019-03-27 16:33:10.1-06,)"));
    	assertNotNull(Range.zonedDateTimeRange("[2019-03-27 16:33:10.12-06,)"));
    	assertNotNull(Range.zonedDateTimeRange("[2019-03-27 16:33:10.123-06,)"));
    	assertNotNull(Range.zonedDateTimeRange("[2019-03-27 16:33:10.1234-06,)"));
    	assertNotNull(Range.zonedDateTimeRange("[2019-03-27 16:33:10.12345-06,)"));
    	assertNotNull(Range.zonedDateTimeRange("[2019-03-27 16:33:10.123456-06,)"));
    	assertNotNull(Range.zonedDateTimeRange("[2019-03-27 16:33:10.123456-06,infinity)"));
    }
}