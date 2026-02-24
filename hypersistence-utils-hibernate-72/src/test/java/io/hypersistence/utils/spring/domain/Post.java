package io.hypersistence.utils.spring.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * @author Vlad Mihalcea
 */
@Entity
@Table(
    name = "post",
    uniqueConstraints = @UniqueConstraint(
        name = "UK_POST_SLUG",
        columnNames = "slug"
    )
)
public class Post {

    @Id
    private Long id;

    private String title;

    private String slug;

    public Long getId() {
        return id;
    }

    public Post setId(Long id) {
        this.id = id;
        return this;
    }

    public String getTitle() {
        return title;
    }

    public Post setTitle(String title) {
        this.title = title;
        return this;
    }

    public Post setSlug(String slug) {
        this.slug = slug;
        return this;
    }
}
