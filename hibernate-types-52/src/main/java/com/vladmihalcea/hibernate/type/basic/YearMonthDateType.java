package com.vladmihalcea.hibernate.type.basic;

import com.vladmihalcea.hibernate.type.basic.internal.YearMonthTypeDescriptor;
import org.hibernate.type.AbstractSingleColumnStandardBasicType;
import org.hibernate.type.descriptor.sql.DateTypeDescriptor;

import java.time.YearMonth;

/**
 * Maps a Java {@link java.time.YearMonth} object to a {@code DATE} column type.
 * <p>
 * For more details about how to use it, check out <a href="">this article</a> on <a href="https://vladmihalcea.com/">vladmihalcea.com</a>.
 *
 * @author Vlad Mihalcea
 */
public class YearMonthDateType
        extends AbstractSingleColumnStandardBasicType<YearMonth> {

    public static final YearMonthDateType INSTANCE = new YearMonthDateType();

    public YearMonthDateType() {
        super(
                DateTypeDescriptor.INSTANCE,
                YearMonthTypeDescriptor.INSTANCE
        );
    }

    public String getName() {
        return "yearmonth-date";
    }

    @Override
    protected boolean registerUnderJavaType() {
        return true;
    }
}