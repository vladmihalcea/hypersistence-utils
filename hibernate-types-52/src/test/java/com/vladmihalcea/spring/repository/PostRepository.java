package com.vladmihalcea.spring.repository;

import com.vladmihalcea.spring.repository.domain.Post;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * @author Vlad Mihalcea
 */
@Repository
public interface PostRepository extends HibernateRepository<Post>, JpaRepository<Post, Long> {

}
