package io.hypersistence.utils.spring.repo.projection;

import io.hypersistence.utils.hibernate.type.util.Configuration;
import io.hypersistence.utils.spring.domain.Post;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

/**
 * @author Vlad Mihalcea
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = ProjectionSpringDataJPABaseConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class ProjectionSpringDataJPABaseTest {

    protected final Logger LOGGER = LoggerFactory.getLogger(getClass());

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private PostRepository postRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    public void myTest() {
        // given
        transactionTemplate.execute((TransactionCallback<Void>) transactionStatus -> {
            postRepository.persist(
                    new Post()
                            .setId(1L)
                            .setTitle("test title")
                            .setSlug("slug1")
            );

            postRepository.persistAndFlush(
                    new Post()
                            .setId(2L)
                            .setTitle("test title")
                            .setSlug("slug2")
            );

            return null;
        });

        // when
        List<PostRepository.TestProjection> postsSummary = transactionTemplate.execute(transactionStatus ->
                postRepository.findAllSlugGroupedByTitle()
        );

        // then
        PostRepository.TestProjection result = postsSummary.get(0);
        assertEquals("test title", result.getTitle());

        List<String> expectedSlugs = new ArrayList<>();
        expectedSlugs.add("slug1");
        expectedSlugs.add("slug2");
        assertEquals(expectedSlugs, result.getSlugs());
    }
}

