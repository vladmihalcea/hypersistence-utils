package com.vladmihalcea.hibernate.type.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vladmihalcea.hibernate.type.MutableDynamicParameterizedType;
import com.vladmihalcea.hibernate.type.json.internal.JsonJavaTypeDescriptor;
import com.vladmihalcea.hibernate.type.json.internal.JsonJdbcTypeDescriptor;
import com.vladmihalcea.hibernate.type.util.Configuration;
import com.vladmihalcea.hibernate.type.util.ObjectMapperWrapper;

import java.lang.reflect.Type;

/**
 * <p>
 * {@link JsonType} allows you to map any given JSON object (e.g., POJO, <code>Map&lt;String, Object&gt;</code>, List&lt;T&gt;, <code>JsonNode</code>) on any of the following database systems:
 * </p>
 * <ul>
 * <li><strong>PostgreSQL</strong> - for both <code>jsonb</code> and <code>json</code> column types</li>
 * <li><strong>MySQL</strong> - for the <code>json</code> column type</li>
 * <li><strong>SQL Server</strong> - for the <code>NVARCHAR</code> column type storing JSON</li>
 * <li><strong>Oracle</strong> - for the <code>VARCHAR</code> column type storing JSON</li>
 * <li><strong>H2</strong> - for the <code>json</code> column type</li>
 * </ul>
 *
 * <p>
 * For more details about how to use the {@link JsonType}, check out <a href="https://vladmihalcea.com/how-to-map-json-objects-using-generic-hibernate-types/">this article</a> on <a href="https://vladmihalcea.com/">vladmihalcea.com</a>.
 * </p>
 * <p>
 * If you are using <strong>Oracle</strong> and want to store JSON objects in a <code>BLOB</code> column types, then you should use the {@link JsonBlobType} instead. For more details, check out <a href="https://vladmihalcea.com/oracle-json-jpa-hibernate/">this article</a> on <a href="https://vladmihalcea.com/">vladmihalcea.com</a>.
 * </p>
 *
 * @author Vlad Mihalcea
 */
public class JsonType extends MutableDynamicParameterizedType<Object, JsonJdbcTypeDescriptor, JsonJavaTypeDescriptor> {

    public static final JsonType INSTANCE = new JsonType();

    public JsonType() {
        super(
            Object.class,
            new JsonJdbcTypeDescriptor(),
            new JsonJavaTypeDescriptor(Configuration.INSTANCE.getObjectMapperWrapper())
        );
    }

    public JsonType(Type javaType) {
        super(
            Object.class,
            new JsonJdbcTypeDescriptor(),
            new JsonJavaTypeDescriptor(Configuration.INSTANCE.getObjectMapperWrapper(), javaType)
        );
    }

    public JsonType(Configuration configuration) {
        super(
            Object.class,
            new JsonJdbcTypeDescriptor(),
            new JsonJavaTypeDescriptor(configuration.getObjectMapperWrapper())
        );
    }

    public JsonType(org.hibernate.type.spi.TypeBootstrapContext typeBootstrapContext) {
        this(new Configuration(typeBootstrapContext.getConfigurationSettings()));
    }

    public JsonType(ObjectMapper objectMapper) {
        super(
            Object.class,
            new JsonJdbcTypeDescriptor(),
            new JsonJavaTypeDescriptor(new ObjectMapperWrapper(objectMapper))
        );
    }

    public JsonType(ObjectMapperWrapper objectMapperWrapper) {
        super(
            Object.class,
            new JsonJdbcTypeDescriptor(),
            new JsonJavaTypeDescriptor(objectMapperWrapper)
        );
    }

    public JsonType(ObjectMapper objectMapper, Type javaType) {
        super(
            Object.class,
            new JsonJdbcTypeDescriptor(),
            new JsonJavaTypeDescriptor(new ObjectMapperWrapper(objectMapper), javaType)
        );
    }

    public JsonType(ObjectMapperWrapper objectMapperWrapper, Type javaType) {
        super(
            Object.class,
            new JsonJdbcTypeDescriptor(),
            new JsonJavaTypeDescriptor(objectMapperWrapper, javaType)
        );
    }

    public String getName() {
        return "json";
    }
}