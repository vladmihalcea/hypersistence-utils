package io.hypersistence.utils.spring.domain;

import com.google.common.collect.HashMultimap;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Type;

@Entity
@Table(
        name = "movie"
)
public class Movie {
    @Id
    private String name;

    @Type(JsonType.class)
    @Column(columnDefinition = "json", name = "actors")
    private HashMultimap<String, String> cast = HashMultimap.create();

    public String getName() {
        return name;
    }

    public Movie setName(String name) {
        this.name = name;
        return this;
    }

    public Movie addCast(String role, String name) {
        cast.put(role, name);
        return this;
    }
    public HashMultimap<String, String> getCast() {
        return cast;
    }
}
