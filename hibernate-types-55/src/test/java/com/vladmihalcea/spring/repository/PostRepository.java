package com.vladmihalcea.spring.repository;

import com.vladmihalcea.spring.repository.domain.Post;
import org.springframework.stereotype.Repository;

/**
 * @author Vlad Mihalcea
 */
@Repository
public interface PostRepository extends HibernateRepository<Post>, BaseJpaRepository<Post, Long> {

}
