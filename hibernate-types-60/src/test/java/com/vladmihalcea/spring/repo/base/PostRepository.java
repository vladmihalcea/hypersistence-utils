package com.vladmihalcea.spring.repo.base;

import com.vladmihalcea.spring.domain.Post;
import com.vladmihalcea.spring.repository.BaseJpaRepository;
import org.springframework.stereotype.Repository;

/**
 * @author Vlad Mihalcea
 */
@Repository
public interface PostRepository extends BaseJpaRepository<Post, Long> {

}
