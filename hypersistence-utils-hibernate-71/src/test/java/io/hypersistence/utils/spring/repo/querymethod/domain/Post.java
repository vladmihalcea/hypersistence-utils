package io.hypersistence.utils.spring.repo.querymethod.domain;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "post")
public class Post {

    @Id
    @GeneratedValue
    private Long id;

    @OneToMany(
        mappedBy = "post",
        fetch = FetchType.LAZY,
        cascade = CascadeType.ALL,
        orphanRemoval = true
    )
    private final List<PostComment> comments = new ArrayList<>();

    public Long getId() {
        return id;
    }

    public Post setId(Long id) {
        this.id = id;
        return this;
    }

    public List<PostComment> getComments() {
        return comments;
    }

    public Post addPostComment(PostComment comment) {
        comment.setPost(this);
        comments.add(comment);
        return this;
    }
}
