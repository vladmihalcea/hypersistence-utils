package io.hypersistence.utils.hibernate.type.json.internal;

import io.hypersistence.utils.hibernate.type.util.ObjectMapperWrapper;
import io.hypersistence.utils.common.LogUtils;
import io.hypersistence.utils.common.ReflectionUtils;
import org.hibernate.HibernateException;
import org.hibernate.annotations.common.reflection.XProperty;
import org.hibernate.annotations.common.reflection.java.JavaXMember;
import org.hibernate.engine.jdbc.BinaryStream;
import org.hibernate.engine.jdbc.internal.BinaryStreamImpl;
import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.type.descriptor.java.AbstractClassJavaType;
import org.hibernate.type.descriptor.java.BlobJavaType;
import org.hibernate.type.descriptor.java.DataHelper;
import org.hibernate.type.descriptor.java.MutableMutabilityPlan;
import org.hibernate.type.descriptor.jdbc.JdbcType;
import org.hibernate.type.descriptor.jdbc.JdbcTypeIndicators;
import org.hibernate.usertype.DynamicParameterizedType;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.sql.Blob;
import java.sql.SQLException;
import java.util.*;

/**
 * @author Vlad Mihalcea
 */
public class JsonJavaTypeDescriptor extends AbstractClassJavaType<Object> implements DynamicParameterizedType, JdbcTypeSetter {

    private Type propertyType;

    private Class propertyClass;

    private ObjectMapperWrapper objectMapperWrapper;

    private JdbcType jdbcType;

    public JsonJavaTypeDescriptor() {
        this(Object.class);
    }

    public JsonJavaTypeDescriptor(Type type) {
        this((Class<?>) type, ObjectMapperWrapper.INSTANCE);
    }

    public JsonJavaTypeDescriptor(Class clazz, final ObjectMapperWrapper objectMapperWrapper) {
        super(clazz, new MutableMutabilityPlan<>() {
            @Override
            protected Object deepCopyNotNull(Object value) {
                return objectMapperWrapper.clone(value);
            }
        });
        this.objectMapperWrapper = objectMapperWrapper;
        setPropertyClass(clazz);
    }

    public JsonJavaTypeDescriptor(final ObjectMapperWrapper objectMapperWrapper) {
        this(Object.class, objectMapperWrapper);
    }

    public JsonJavaTypeDescriptor(final ObjectMapperWrapper objectMapperWrapper, Type type) {
        this((Class) type, objectMapperWrapper);
        setPropertyClass(type);
    }

    @Override
    public void setParameterValues(Properties parameters) {
        final XProperty xProperty = (XProperty) parameters.get(DynamicParameterizedType.XPROPERTY);
        Type type = null;
        if(xProperty instanceof JavaXMember) {
            type = ((JavaXMember) xProperty).getJavaType();
        } else {
            Object parameterType = parameters.get(PARAMETER_TYPE);
            if(parameterType instanceof ParameterType) {
                type = ((ParameterType) parameterType).getReturnedClass();
            } else if(parameterType instanceof String) {
                type = ReflectionUtils.getClass((String) parameterType);
            }
        }
        if(type == null) {
            throw new HibernateException("Could not resolve property type!");
        }
        setPropertyClass(type);
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
        if ((one instanceof Collection && another instanceof Collection) ||
            (one instanceof Map && another instanceof Map)) {
            return Objects.equals(one, another);
        }
        if (one.getClass().equals(another.getClass()) &&
            ReflectionUtils.getDeclaredMethodOrNull(one.getClass(), "equals", Object.class) != null) {
            return one.equals(another);
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
    public Object fromString(CharSequence string) {
        if(propertyClass == null) {
            throw new HibernateException(
                "The propertyClass in JsonTypeDescriptor is null, " +
                    "hence it doesn't know to what Java Object type " +
                    "to map the JSON column value that was read from the database!"
            );
        }
        if (String.class.isAssignableFrom(propertyClass)) {
            return string;
        }
        return objectMapperWrapper.fromString((String) string, propertyType);
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

            final Blob blob = BlobJavaType.INSTANCE.fromString(stringValue);
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

    private void setPropertyClass(Type type) {
        this.propertyType = type;
        if (type instanceof ParameterizedType) {
            type = ((ParameterizedType) type).getRawType();
        } else if (type instanceof TypeVariable) {
            type = ((TypeVariable) type).getGenericDeclaration().getClass();
        }
        this.propertyClass = (Class) type;
        validatePropertyType();
    }

    private void validatePropertyType() {
        if(Collection.class.isAssignableFrom(propertyClass)) {
            if (propertyType instanceof ParameterizedType) {
                ParameterizedType parameterizedType = (ParameterizedType) propertyType;

                for(Class genericType : ReflectionUtils.getGenericTypes(parameterizedType)) {
                    if(!validatedTypes.add(genericType)) {
                        continue;
                    }
                    Method equalsMethod = ReflectionUtils.getMethodOrNull(genericType, "equals", Object.class);
                    Method hashCodeMethod = ReflectionUtils.getMethodOrNull(genericType, "hashCode");

                    if(equalsMethod == null ||
                        hashCodeMethod == null ||
                        Object.class.equals(equalsMethod.getDeclaringClass()) ||
                        Object.class.equals(hashCodeMethod.getDeclaringClass())) {
                        LogUtils.LOGGER.warn("The {} class should override both the equals and hashCode methods based on the JSON object value it represents!", genericType);
                    }
                }
            }
        }
    }

    @Override
    public JdbcType getRecommendedJdbcType(JdbcTypeIndicators indicators) {
        return jdbcType;
    }

    public void setJdbcType(JdbcType jdbcType) {
        this.jdbcType = jdbcType;
    }

    private static final Set<Class> validatedTypes = Collections.synchronizedSet(new HashSet<>());
}
