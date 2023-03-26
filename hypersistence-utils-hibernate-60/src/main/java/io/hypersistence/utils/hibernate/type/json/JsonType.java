package io.hypersistence.utils.hibernate.type.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.hypersistence.utils.hibernate.type.MutableDynamicParameterizedType;
import io.hypersistence.utils.hibernate.type.json.internal.JsonJavaTypeDescriptor;
import io.hypersistence.utils.hibernate.type.json.internal.JsonJdbcTypeDescriptor;
import io.hypersistence.utils.hibernate.type.util.JsonConfiguration;
import io.hypersistence.utils.hibernate.type.util.ObjectMapperWrapper;

import java.lang.reflect.Type;

/**
 * <p>
 * {@link JsonType} allows you to map any given JSON object (e.g., POJO, <code>Map&lt;String, Object&gt;</code>, List&lt;T&gt;, <code>JsonNode</code>) on any of the following database systems:
 * </p>
 * <ul>
 * <li><strong>PostgreSQL</strong> - for both <strong><code>jsonb</code></strong> and <strong><code>json</code></strong> column types</li>
 * <li><strong>MySQL</strong> - for the <strong><code>json</code></strong> column type</li>
 * <li><strong>SQL Server</strong> - for the <strong><code>NVARCHAR</code></strong> column type storing JSON</li>
 * <li><strong>Oracle</strong> - for the <strong><code>JSON</code></strong> column type if you're using Oracle 21c or the <strong><code>VARCHAR</code></strong> column type storing JSON if you're using an older Oracle version</li>
 * <li><strong>H2</strong> - for the <strong><code>json</code></strong> column type</li>
 * </ul>
 * <p>
 * If you switch to Oracle 21c from an older version, then you should also migrate your {@code JSON} columns to the native JSON type since this binary type performs better than
 * {@code VARCHAR2} or {@code BLOB} column types.
 * </p>
 * <p>
 * However, if you don't want to migrate to the new {@code JSON} data type,
 * then you just have to provide the column type via the JPA {@link jakarta.persistence.Column#columnDefinition()} attribute,
 * like in the following example:
 * </p>
 * <pre>
 * {@code @Type(}JsonType.class)
 * {@code @Column(}columnDefinition = "VARCHAR2")
 * </pre>
 * <p>
 * For more details about how to use the {@link JsonType}, check out <a href="https://vladmihalcea.com/how-to-map-json-objects-using-generic-hibernate-types/">this article</a> on <a href="https://vladmihalcea.com/">vladmihalcea.com</a>.
 * </p>
 * <p>
 * If you are using <strong>Oracle</strong> and want to store JSON objects in a <code>BLOB</code> column type, then you can use the {@link JsonBlobType} instead. For more details, check out <a href="https://vladmihalcea.com/oracle-json-jpa-hibernate/">this article</a> on <a href="https://vladmihalcea.com/">vladmihalcea.com</a>.
 * </p>
 * <p>
 * Or, you can use the {@link JsonType}, but you'll have to specify the underlying column type
 * using the JPA {@link jakarta.persistence.Column#columnDefinition()} attribute, like this:
 * </p>
 * <pre>
 * {@code @Type(}JsonType.class)
 * {@code @Column(}columnDefinition = "BLOB")
 * </pre>
 * @author Vlad Mihalcea
 */
public class JsonType extends MutableDynamicParameterizedType<Object, JsonJdbcTypeDescriptor, JsonJavaTypeDescriptor> {

    public static final JsonType INSTANCE = new JsonType();

    public JsonType() {
        super(
            Object.class,
            new JsonJdbcTypeDescriptor(),
            new JsonJavaTypeDescriptor(JsonConfiguration.INSTANCE.getObjectMapperWrapper())
        );
    }

    public JsonType(Type javaType) {
        super(
            Object.class,
            new JsonJdbcTypeDescriptor(),
            new JsonJavaTypeDescriptor(JsonConfiguration.INSTANCE.getObjectMapperWrapper(), javaType)
        );
    }

    public JsonType(JsonConfiguration configuration) {
        super(
            Object.class,
            new JsonJdbcTypeDescriptor(configuration.getProperties()),
            new JsonJavaTypeDescriptor(configuration.getObjectMapperWrapper()),
            configuration
        );
    }

    public JsonType(org.hibernate.type.spi.TypeBootstrapContext typeBootstrapContext) {
        this(new JsonConfiguration(typeBootstrapContext.getConfigurationSettings()));
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