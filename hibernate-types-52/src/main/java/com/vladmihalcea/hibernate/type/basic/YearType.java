package com.vladmihalcea.hibernate.type.basic;

import com.vladmihalcea.hibernate.type.basic.internal.YearTypeDescriptor;
import org.hibernate.type.AbstractSingleColumnStandardBasicType;
import org.hibernate.type.descriptor.sql.SmallIntTypeDescriptor;

import java.time.Year;

/**
 * Maps a Java {@link Year} object to an {@code INT} column type.
 * <p>
 * For more details about how to use it, check out <a href="https://vladmihalcea.com/java-time-year-month-jpa-hibernate/">this article</a> on <a href="https://vladmihalcea.com/">vladmihalcea.com</a>.
 *
 * @author Vlad Mihalcea
 */
public class YearType
        extends AbstractSingleColumnStandardBasicType<Year> {

    public static final YearType INSTANCE = new YearType();

    public YearType() {
        super(
                SmallIntTypeDescriptor.INSTANCE,
                YearTypeDescriptor.INSTANCE
        );
    }

    public String getName() {
        return "year";
    }

    @Override
    protected boolean registerUnderJavaType() {
        return true;
    }
}