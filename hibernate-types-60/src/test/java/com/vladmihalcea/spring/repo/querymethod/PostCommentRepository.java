package com.vladmihalcea.spring.repo.querymethod;

import com.vladmihalcea.spring.repo.querymethod.domain.PostComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * @author Vlad Mihalcea
 */
@Repository
public interface PostCommentRepository extends JpaRepository<PostComment, Long> {

}
