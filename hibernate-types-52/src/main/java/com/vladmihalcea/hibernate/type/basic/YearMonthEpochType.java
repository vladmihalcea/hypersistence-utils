package com.vladmihalcea.hibernate.type.basic;

import com.vladmihalcea.hibernate.type.basic.internal.YearMonthEpochTypeDescriptor;
import org.hibernate.type.AbstractSingleColumnStandardBasicType;
import org.hibernate.type.descriptor.sql.SmallIntTypeDescriptor;

import java.time.YearMonth;

/**
 * Maps a Java {@link YearMonth} object to an small and continuous {@code INT} column type
 * which defines the months that passed since the Unix epoch.
 * <p>
 * For more details about how to use it, check out <a href="TODO">this article</a> on <a href="https://vladmihalcea.com/">vladmihalcea.com</a>.
 *
 * @author Vlad Mihalcea
 */
public class YearMonthEpochType
        extends AbstractSingleColumnStandardBasicType<YearMonth> {

    public static final YearMonthEpochType INSTANCE = new YearMonthEpochType();

    public YearMonthEpochType() {
        super(
                SmallIntTypeDescriptor.INSTANCE,
                YearMonthEpochTypeDescriptor.INSTANCE
        );
    }

    public String getName() {
        return "yearmonth-epoch";
    }

    @Override
    protected boolean registerUnderJavaType() {
        return true;
    }
}