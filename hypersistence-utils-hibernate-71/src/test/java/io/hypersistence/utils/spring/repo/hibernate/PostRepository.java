package io.hypersistence.utils.spring.repo.hibernate;

import io.hypersistence.utils.spring.domain.Post;
import io.hypersistence.utils.spring.repository.HibernateRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * @author Vlad Mihalcea
 */
@Repository
public interface PostRepository extends HibernateRepository<Post>, JpaRepository<Post, Long> {

}
