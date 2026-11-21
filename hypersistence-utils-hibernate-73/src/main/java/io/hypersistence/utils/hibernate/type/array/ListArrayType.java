package io.hypersistence.utils.hibernate.type.array;

import io.hypersistence.utils.hibernate.type.array.internal.AbstractArrayType;
import io.hypersistence.utils.hibernate.type.array.internal.ListArrayTypeDescriptor;
import io.hypersistence.utils.hibernate.type.util.Configuration;
import org.hibernate.usertype.DynamicParameterizedType;

import java.util.Collection;
import java.util.Properties;

/**
 * Maps an {@link java.util.List} entity attribute on a PostgreSQL ARRAY column type.
 * <p>
 * For more details about how to use it, check out <a href="https://vladmihalcea.com/postgresql-array-java-list/">this article</a> on <a href="https://vladmihalcea.com/">vladmihalcea.com</a>.
 *
 * @author Vlad Mihalcea
 */
public class ListArrayType extends AbstractArrayType<Collection> implements DynamicParameterizedType {

    public static final ListArrayType INSTANCE = new ListArrayType();

    public ListArrayType() {
        super(
            new ListArrayTypeDescriptor()
        );
    }

    public ListArrayType(Configuration configuration) {
        super(
            new ListArrayTypeDescriptor(), configuration
        );
    }

    public ListArrayType(org.hibernate.type.spi.TypeBootstrapContext typeBootstrapContext) {
        this(new Configuration(typeBootstrapContext.getConfigurationSettings()));
    }

    public String getName() {
        return "list-array";
    }

    @Override
    public void setParameterValues(Properties parameters) {
        ((DynamicParameterizedType) getJavaTypeDescriptor()).setParameterValues(parameters);
    }
}