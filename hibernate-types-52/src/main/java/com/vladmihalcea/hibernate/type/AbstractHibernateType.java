package com.vladmihalcea.hibernate.type;

import com.vladmihalcea.hibernate.type.util.Configuration;
import org.hibernate.type.AbstractSingleColumnStandardBasicType;
import org.hibernate.type.descriptor.java.JavaTypeDescriptor;
import org.hibernate.type.descriptor.sql.SqlTypeDescriptor;

/**
 * Very convenient base class for implementing object types using Hibernate Java and SQL descriptors.
 *
 * @author Vlad Mihalcea
 */
public abstract class AbstractHibernateType<T> extends AbstractSingleColumnStandardBasicType<T> {

    private final Configuration configuration;

    /**
     * Initialization constructor taking the {@link SqlTypeDescriptor} and {@link JavaTypeDescriptor} objects,
     * and using the default {@link Configuration} object.
     *
     * @param sqlTypeDescriptor the {@link SqlTypeDescriptor} to be used
     * @param javaTypeDescriptor the {@link JavaTypeDescriptor} to be used
     */
    protected AbstractHibernateType(
            SqlTypeDescriptor sqlTypeDescriptor,
            JavaTypeDescriptor<T> javaTypeDescriptor) {
        super(sqlTypeDescriptor, javaTypeDescriptor);
        this.configuration = Configuration.INSTANCE;
    }

    /**
     * Initialization constructor taking the {@link SqlTypeDescriptor}, {@link JavaTypeDescriptor},
     * and {@link Configuration} objects.
     *
     * @param sqlTypeDescriptor the {@link SqlTypeDescriptor} to be used
     * @param javaTypeDescriptor the {@link JavaTypeDescriptor} to be used
     * @param configuration custom {@link Configuration} object.
     */
    protected AbstractHibernateType(SqlTypeDescriptor sqlTypeDescriptor, JavaTypeDescriptor<T> javaTypeDescriptor, Configuration configuration) {
        super(sqlTypeDescriptor, javaTypeDescriptor);
        this.configuration = configuration;
    }

    /**
     * Get the current {@link Configuration} object.
     * @return the current {@link Configuration} object.
     */
    protected Configuration getConfiguration() {
        return configuration;
    }
}