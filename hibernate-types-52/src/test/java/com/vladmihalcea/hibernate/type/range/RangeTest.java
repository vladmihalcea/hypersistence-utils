package com.vladmihalcea.hibernate.type.range;

import org.junit.Test;

import static com.vladmihalcea.hibernate.type.range.Range.integerRange;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;

/**
 * @author Edgar Asatryan
 */
public class RangeTest {

    @Test
    public void ofStringTest() {
        assertThat(integerRange("[1,3]").lower(), is(1));
        assertThat(integerRange("[1,3]").upper(), is(3));

        assertThat(integerRange("[,3]").lower(), is(nullValue()));
        assertThat(integerRange("[,3]").upper(), is(3));
        assertThat(integerRange("[,3]").hasLowerBound(), is(false));
        assertThat(integerRange("[,3]").hasUpperBound(), is(true));

        assertThat(integerRange("[,]").lower(), is(nullValue()));
        assertThat(integerRange("[,]").upper(), is(nullValue()));
        assertThat(integerRange("[,]").hasLowerBound(), is(false));
        assertThat(integerRange("[,]").hasUpperBound(), is(false));
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
}