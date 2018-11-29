package com.vladmihalcea.hibernate.type.array;

import com.vladmihalcea.hibernate.type.array.internal.ArraySqlTypeDescriptor;
import com.vladmihalcea.hibernate.type.array.internal.EnumArrayTypeDescriptor;
import org.hibernate.type.AbstractSingleColumnStandardBasicType;
import org.hibernate.usertype.DynamicParameterizedType;

import java.util.Properties;

/**
 * Maps an {@code Enum[]} array on a PostgreSQL ARRAY type.
 * <p>
 * For more details about how to use it, check out <a href="https://vladmihalcea.com/how-to-map-java-and-sql-arrays-with-jpa-and-hibernate/">this article</a> on <a href="https://vladmihalcea.com/">vladmihalcea.com</a>.
 * <p>
 * Note that you must set the {@code TYPE_DEF_NAME} and {@code TYPE_DB_NAME} parameters when defining the custom {@code TypeDef}.
 * The {@code TYPE_DEF_NAME} must equal the same name as the type definition and the {@code TYPE_DB_NAME} must equal the enum type name in PostgreSQL.
 *
 * @author Nazir El-Kayssi
 */
public class EnumArrayType
        extends AbstractSingleColumnStandardBasicType<Enum[]>
        implements DynamicParameterizedType {

    public static final EnumArrayType INSTANCE = new EnumArrayType();
    public static final String TYPE_DEF_NAME = "type_def_name";
    public static final String TYPE_DB_NAME = "type_db_name";
    
    private String typeDefName;

    public EnumArrayType() {
        super(ArraySqlTypeDescriptor.INSTANCE, EnumArrayTypeDescriptor.INSTANCE);
    }

    public String getName() {
        return typeDefName;
    }

    @Override
    protected boolean registerUnderJavaType() {
        return true;
    }

    @Override
    public void setParameterValues(Properties parameters) {
      typeDefName = parameters.getProperty(TYPE_DEF_NAME);
      ((EnumArrayTypeDescriptor) getJavaTypeDescriptor()).setParameterValues(parameters);
    }
}