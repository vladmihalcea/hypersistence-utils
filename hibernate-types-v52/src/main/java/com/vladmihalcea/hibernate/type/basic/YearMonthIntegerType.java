package com.vladmihalcea.hibernate.type.basic;

import com.vladmihalcea.hibernate.type.basic.internal.YearMonthTypeDescriptor;
import org.hibernate.type.AbstractSingleColumnStandardBasicType;
import org.hibernate.type.descriptor.sql.IntegerTypeDescriptor;

import java.time.YearMonth;

/**
 * Maps a Java {@link YearMonth} object to an {@code INT} column type.
 * <p>
 * For more details about how to use it, check out <a href="https://vladmihalcea.com/java-yearmonth-jpa-hibernate/">this article</a> on <a href="https://vladmihalcea.com/">vladmihalcea.com</a>.
 *
 * @author Vlad Mihalcea
 */
public class YearMonthIntegerType
        extends AbstractSingleColumnStandardBasicType<YearMonth> {

    public static final YearMonthIntegerType INSTANCE = new YearMonthIntegerType();

    public YearMonthIntegerType() {
        super(
                IntegerTypeDescriptor.INSTANCE,
                YearMonthTypeDescriptor.INSTANCE
        );
    }

    public String getName() {
        return "yearmonth-int";
    }

    @Override
    protected boolean registerUnderJavaType() {
        return true;
    }
}