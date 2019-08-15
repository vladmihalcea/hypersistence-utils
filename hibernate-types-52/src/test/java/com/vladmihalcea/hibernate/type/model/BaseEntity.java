package com.vladmihalcea.hibernate.type.model;

import com.vladmihalcea.hibernate.type.array.IntArrayType;
import com.vladmihalcea.hibernate.type.array.LongArrayType;
import com.vladmihalcea.hibernate.type.array.StringArrayType;
import com.vladmihalcea.hibernate.type.array.UUIDArrayType;
import com.vladmihalcea.hibernate.type.json.JsonBinaryType;
import com.vladmihalcea.hibernate.type.json.JsonNodeBinaryType;
import com.vladmihalcea.hibernate.type.json.JsonNodeStringType;
import com.vladmihalcea.hibernate.type.json.JsonStringType;
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
        @TypeDef(name = "json", typeClass = JsonStringType.class),
        @TypeDef(name = "jsonb", typeClass = JsonBinaryType.class),
        @TypeDef(name = "jsonb-node", typeClass = JsonNodeBinaryType.class),
        @TypeDef(name = "json-node", typeClass = JsonNodeStringType.class),
        @TypeDef(name = "json-p", typeClass = com.vladmihalcea.hibernate.type.jsonp.JsonStringType.class),
        @TypeDef(name = "jsonb-p", typeClass = com.vladmihalcea.hibernate.type.jsonp.JsonBinaryType.class),
        @TypeDef(name = "jsonb-p-value", typeClass = com.vladmihalcea.hibernate.type.jsonp.JsonNodeBinaryType.class),
        @TypeDef(name = "json-p-value", typeClass = com.vladmihalcea.hibernate.type.jsonp.JsonNodeStringType.class),
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
