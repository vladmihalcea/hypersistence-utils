package io.hypersistence.utils.hibernate.util;

import io.hypersistence.utils.hibernate.type.json.JsonNodeStringType;
import io.hypersistence.utils.hibernate.type.json.internal.JacksonUtil;
import io.hypersistence.utils.hibernate.type.util.JsonSerializer;
import org.hibernate.type.JavaObjectType;
import org.hibernate.type.descriptor.java.JavaType;

/**
 * @author Vlad Mihalcea
 */
public class JsonNodeUtil {

    public static final JavaObjectType SCALAR_JSON_NODE_TYPE = new JavaObjectType(
        JsonNodeStringType.INSTANCE.getJdbcType(),
        (JavaType) JsonNodeStringType.INSTANCE.getJavaTypeDescriptor()
    );
}
