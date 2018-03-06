package com.vladmihalcea.hibernate.type.json.internal;

import java.lang.reflect.Type;
import java.util.Objects;
import java.util.Properties;

import org.hibernate.annotations.common.reflection.XProperty;
import org.hibernate.annotations.common.reflection.java.JavaXMember;
import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.type.descriptor.java.AbstractTypeDescriptor;
import org.hibernate.type.descriptor.java.MutableMutabilityPlan;
import org.hibernate.usertype.DynamicParameterizedType;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vladmihalcea.hibernate.type.util.ReflectionUtils;

/**
 * @author Vlad Mihalcea
 */
public class JsonTypeDescriptor
        extends AbstractTypeDescriptor<Object> implements DynamicParameterizedType {

    protected final ObjectMapper mapperInstance;
    protected Type type;

    public JsonTypeDescriptor() {
        this(JacksonUtil.OBJECT_MAPPER);
    }

    public JsonTypeDescriptor(ObjectMapper mapper) {
        super(Object.class, new MutableMutabilityPlan<Object>() {
            @Override
            protected Object deepCopyNotNull(Object value) {
                return JacksonUtil.clone(mapper, value);
            }
        });
        this.mapperInstance = Objects.requireNonNull(mapper, "No object mapper provided");
    }

    @Override
    public void setParameterValues(Properties parameters) {
        XProperty xProperty = (XProperty) parameters.get(DynamicParameterizedType.XPROPERTY);
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

        if ((one == null) || (another == null)) {
            return false;
        }

        if ((one instanceof String) && (another instanceof String)) {
            return one.equals(another);
        }

        String oneString = JacksonUtil.toString(mapperInstance, one);
        JsonNode oneNode = JacksonUtil.toJsonNode(mapperInstance, oneString);
        String anotherString = JacksonUtil.toString(mapperInstance, another);
        JsonNode anotherNode = JacksonUtil.toJsonNode(mapperInstance, anotherString);
        return Objects.equals(oneNode, anotherNode);
    }

    @Override
    public String toString(Object value) {
        return JacksonUtil.toString(mapperInstance, value);
    }

    @Override
    public Object fromString(String string) {
        return JacksonUtil.fromString(mapperInstance, string, type);
    }

    @Override
    public <X> X unwrap(Object value, Class<X> type, WrapperOptions options) {
        if (value == null) {
            return null;
        }
        if (String.class.isAssignableFrom(type)) {
            String str = toString(value);
            return type.cast(str);
        }
        if (Object.class.isAssignableFrom(type)) {
            String str = toString(value);
            /*
             * NOTE: do not cast it using type.cast(...) for safety reasons - since
             * we only ask Object.class.isAssignableFrom(type) - which means "everybody"
             */
            return (X) JacksonUtil.toJsonNode(mapperInstance, str);
        }

        throw unknownUnwrap(type);
    }

    @Override
    public <X> Object wrap(X value, WrapperOptions options) {
        if (value == null) {
            return null;
        }
        return fromString(value.toString());
    }
}
