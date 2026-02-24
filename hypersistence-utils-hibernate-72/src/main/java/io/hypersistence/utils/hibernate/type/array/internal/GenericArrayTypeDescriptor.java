package io.hypersistence.utils.hibernate.type.array.internal;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.UUID;

/**
 * @author Vlad Mihalcea
 * @version 3.14.1
 */
public class GenericArrayTypeDescriptor extends AbstractArrayTypeDescriptor<Object> {

    public GenericArrayTypeDescriptor() {
        super(Object.class);
    }

    @Override
    protected String getSqlArrayType() {
        String sqlArrayType = super.getSqlArrayType();
        if(sqlArrayType != null) {
            return sqlArrayType;
        }
        Class javaAttributeClass = getJavaArrayComponentType(getArrayObjectClass());
        if(boolean.class.isAssignableFrom(javaAttributeClass) || Boolean.class.isAssignableFrom(javaAttributeClass)) {
            setSqlArrayType("boolean");
        } else if (java.sql.Date.class.isAssignableFrom(javaAttributeClass) || LocalDate.class.isAssignableFrom(javaAttributeClass)) {
            setSqlArrayType("date");
        } else if (BigDecimal.class.isAssignableFrom(javaAttributeClass)) {
            setSqlArrayType("decimal");
        } else if (double.class.isAssignableFrom(javaAttributeClass) || Double.class.isAssignableFrom(javaAttributeClass)) {
            setSqlArrayType("float8");
        } else if (float.class.isAssignableFrom(javaAttributeClass) || Float.class.isAssignableFrom(javaAttributeClass)) {
            setSqlArrayType("float4");
        } else if (int.class.isAssignableFrom(javaAttributeClass) || Integer.class.isAssignableFrom(javaAttributeClass)) {
            setSqlArrayType("integer");
        } else if (long.class.isAssignableFrom(javaAttributeClass) || Long.class.isAssignableFrom(javaAttributeClass)) {
            setSqlArrayType("bigint");
        }else if (Date.class.isAssignableFrom(javaAttributeClass) || Timestamp.class.isAssignableFrom(javaAttributeClass) || LocalDateTime.class.isAssignableFrom(javaAttributeClass)) {
            setSqlArrayType("timestamp");
        } else if (String.class.isAssignableFrom(javaAttributeClass)) {
            setSqlArrayType("text");
        } else if (UUID.class.isAssignableFrom(javaAttributeClass)) {
            setSqlArrayType("uuid");
        }
        return super.getSqlArrayType();
    }

    private Class getJavaArrayComponentType(Class javaAttributeClass) {
        Class javaArrayComponentType = javaAttributeClass.getComponentType();
        if(javaArrayComponentType.isArray()) {
            return getJavaArrayComponentType(javaArrayComponentType);
        }
        return javaArrayComponentType;
    }
}