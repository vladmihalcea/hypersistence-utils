package com.vladmihalcea.hibernate.type.json.internal;

import com.vladmihalcea.hibernate.type.util.ObjectMapperWrapper;
import com.vladmihalcea.hibernate.type.util.ReflectionUtils;
import org.hibernate.HibernateException;
import org.hibernate.annotations.common.reflection.XProperty;
import org.hibernate.annotations.common.reflection.java.JavaXMember;
import org.hibernate.engine.jdbc.BinaryStream;
import org.hibernate.engine.jdbc.internal.BinaryStreamImpl;
import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.type.descriptor.java.AbstractTypeDescriptor;
import org.hibernate.type.descriptor.java.BlobTypeDescriptor;
import org.hibernate.type.descriptor.java.DataHelper;
import org.hibernate.type.descriptor.java.MutableMutabilityPlan;
import org.hibernate.usertype.DynamicParameterizedType;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.sql.Blob;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Objects;
import java.util.Properties;

/**
 * @author Vlad Mihalcea
 */
public class JsonTypeDescriptor
    extends AbstractTypeDescriptor<Object> implements DynamicParameterizedType {

    private Type type;

    private ObjectMapperWrapper objectMapperWrapper;

    public JsonTypeDescriptor() {
        super(Object.class, new MutableMutabilityPlan<Object>() {
            @Override
            protected Object deepCopyNotNull(Object value) {
                return ObjectMapperWrapper.INSTANCE.clone(value);
            }
        });
    }

    public JsonTypeDescriptor(Type type) {
        this();
        this.type = type;
    }

    public JsonTypeDescriptor(final ObjectMapperWrapper objectMapperWrapper) {
        super(Object.class, new MutableMutabilityPlan<Object>() {
            @Override
            protected Object deepCopyNotNull(Object value) {
                return objectMapperWrapper.clone(value);
            }
        });
        this.objectMapperWrapper = objectMapperWrapper;
    }

    public JsonTypeDescriptor(final ObjectMapperWrapper objectMapperWrapper, Type type) {
        this(objectMapperWrapper);
        this.type = type;
    }

    @Override
    public void setParameterValues(Properties parameters) {
        final XProperty xProperty = (XProperty) parameters.get(DynamicParameterizedType.XPROPERTY);
        if (xProperty instanceof JavaXMember) {
            type = ReflectionUtils.invokeGetter(xProperty, "javaType");
        } else {
            type = ((ParameterType) parameters.get(PARAMETER_TYPE)).getReturnedClass();
        }
    }

    @Override
    public boolean areEqual(Object one, Object another) {
        if (one == another) {
            return true;
        }
        if (one == null || another == null) {
            return false;
        }
        if (one instanceof String && another instanceof String) {
            return one.equals(another);
        }
        if (one instanceof Collection && another instanceof Collection) {
            return Objects.equals(one, another);
        }
        return objectMapperWrapper.toJsonNode(objectMapperWrapper.toString(one)).equals(
            objectMapperWrapper.toJsonNode(objectMapperWrapper.toString(another))
        );
    }

    @Override
    public String toString(Object value) {
        return objectMapperWrapper.toString(value);
    }

    @Override
    public Object fromString(String string) {
        if (String.class.isAssignableFrom(typeToClass())) {
            return string;
        }
        return objectMapperWrapper.fromString(string, type);
    }

    @SuppressWarnings({"unchecked"})
    @Override
    public <X> X unwrap(Object value, Class<X> type, WrapperOptions options) {
        if (value == null) {
            return null;
        }

        if (String.class.isAssignableFrom(type)) {
            return value instanceof String ? (X) value : (X) toString(value);
        } else if (BinaryStream.class.isAssignableFrom(type) ||
            byte[].class.isAssignableFrom(type)) {
            String stringValue = (value instanceof String) ? (String) value : toString(value);

            return (X) new BinaryStreamImpl(DataHelper.extractBytes(new ByteArrayInputStream(stringValue.getBytes())));
        } else if (Blob.class.isAssignableFrom(type)) {
            String stringValue = (value instanceof String) ? (String) value : toString(value);

            final Blob blob = BlobTypeDescriptor.INSTANCE.fromString(stringValue);
            return (X) blob;
        } else if (Object.class.isAssignableFrom(type)) {
            String stringValue = (value instanceof String) ? (String) value : toString(value);
            return (X) objectMapperWrapper.toJsonNode(stringValue);
        }

        throw unknownUnwrap(type);
    }

    @Override
    public <X> Object wrap(X value, WrapperOptions options) {
        if (value == null) {
            return null;
        }

        Blob blob = null;

        if (Blob.class.isAssignableFrom(value.getClass())) {
            blob = options.getLobCreator().wrap((Blob) value);
        } else if (byte[].class.isAssignableFrom(value.getClass())) {
            blob = options.getLobCreator().createBlob((byte[]) value);
        } else if (InputStream.class.isAssignableFrom(value.getClass())) {
            InputStream inputStream = (InputStream) value;
            try {
                blob = options.getLobCreator().createBlob(inputStream, inputStream.available());
            } catch (IOException e) {
                throw unknownWrap(value.getClass());
            }
        }

        String stringValue;
        try {
            stringValue = (blob != null) ? new String(DataHelper.extractBytes(blob.getBinaryStream())) : value.toString();
        } catch (SQLException e) {
            throw new HibernateException("Unable to extract binary stream from Blob", e);
        }

        return fromString(stringValue);
    }

    private Class typeToClass() {
        Type classType = type;
        if (type instanceof ParameterizedType) {
            classType = ((ParameterizedType) type).getRawType();
        }
        return (Class) classType;
    }
}
