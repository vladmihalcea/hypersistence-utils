package io.hypersistence.utils.hibernate.type.model;

import io.hypersistence.utils.hibernate.type.array.*;
import io.hypersistence.utils.hibernate.type.json.*;
import org.hibernate.annotations.TypeDef;
import org.hibernate.annotations.TypeDefs;

import javax.persistence.Id;
import javax.persistence.MappedSuperclass;
import javax.persistence.Version;

/**
 * @author Vlad Mihalcea
 */
@TypeDefs({
        @TypeDef(name = "uuid-array", typeClass = UUIDArrayType.class),
        @TypeDef(name = "string-array", typeClass = StringArrayType.class),
        @TypeDef(name = "int-array", typeClass = IntArrayType.class),
        @TypeDef(name = "long-array", typeClass = LongArrayType.class),
        @TypeDef(name = "double-array", typeClass = DoubleArrayType.class),
        @TypeDef(name = "boolean-array", typeClass = BooleanArrayType.class),
        @TypeDef(name = "date-array", typeClass = DateArrayType.class),
        @TypeDef(name = "timestamp-array", typeClass = TimestampArrayType.class),
        @TypeDef(name = "decimal-array", typeClass = DecimalArrayType.class),
        @TypeDef(name = "json", typeClass = JsonStringType.class),
        @TypeDef(name = "jsonb", typeClass = JsonBinaryType.class),
        @TypeDef(name = "jsonb-node", typeClass = JsonNodeBinaryType.class),
        @TypeDef(name = "json-node", typeClass = JsonNodeStringType.class),
        @TypeDef(name = "jsonb-lob", typeClass = JsonBlobType.class)
})
@MappedSuperclass
public class BaseEntity {

    @Id
    private Long id;

    @Version
    private Integer version;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Integer getVersion() {
        return version;
    }
}
