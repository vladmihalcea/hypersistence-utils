package io.hypersistence.utils.spring.repo.querymethod;

import io.hypersistence.utils.spring.repo.querymethod.domain.Post;
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
