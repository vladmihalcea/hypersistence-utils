package com.vladmihalcea.spring.repo.querymethod;

import com.vladmihalcea.spring.repo.querymethod.domain.Post;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * @author Vlad Mihalcea
 */
@Repository
public interface PostRepository extends JpaRepository<Post, Long> {

    List<Post> deleteByCommentsIsNull();
}
