package io.hypersistence.utils.spring.repo.sort;

import io.hypersistence.utils.spring.domain.Post;
import io.hypersistence.utils.spring.repository.BaseJpaRepository;
import org.springframework.data.repository.ListPagingAndSortingRepository;
import org.springframework.stereotype.Repository;

/**
 * @author Vlad Mihalcea
 */
@Repository
public interface PostRepository extends BaseJpaRepository<Post, Long>, ListPagingAndSortingRepository<Post, Long> {

}
