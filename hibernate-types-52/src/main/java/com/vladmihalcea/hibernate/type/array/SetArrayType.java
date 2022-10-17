package com.vladmihalcea.hibernate.type.array;

import com.vladmihalcea.hibernate.type.array.internal.AbstractArrayType;
import com.vladmihalcea.hibernate.type.array.internal.SetArrayTypeDescriptor;
import com.vladmihalcea.hibernate.type.util.Configuration;

/**
 * Maps an {@link java.util.Set} entity attribute on a PostgreSQL ARRAY column type.
 * <p>
 * For more details about how to use it, check out <a href="https://vladmihalcea.com/postgresql-array-java-list/">this article</a> on <a href="https://vladmihalcea.com/">vladmihalcea.com</a>.
 *
 * @author Aleksey2093
 */
public class SetArrayType extends AbstractArrayType<Object> {

    public static final SetArrayType INSTANCE = new SetArrayType();

    public SetArrayType() {
        super(new SetArrayTypeDescriptor());
    }

    public SetArrayType(Configuration configuration) {
        super(new SetArrayTypeDescriptor(), configuration);
    }

    public String getName() {
        return "set-array";
    }

}
