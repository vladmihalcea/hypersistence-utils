package io.hypersistence.utils.spring.repo.projection;

import io.hypersistence.utils.spring.domain.Post;
import io.hypersistence.utils.spring.repository.BaseJpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import javax.annotation.Nullable;
import java.util.List;

/**
 * @author Vlad Mihalcea
 */
@Repository
public interface PostRepository extends BaseJpaRepository<Post, Long> {

    @Query(
        value = "select p.title as title, array_agg(p.slug) as slugs " +
                "from Post p " +
                "group by p.title",
        nativeQuery = true)
    @Nullable
    List<TestProjection> findAllSlugGroupedByTitle();


    interface TestProjection {
        @Nullable
        String getTitle();

        @Nullable
        List<String> getSlugs();
    }
}
