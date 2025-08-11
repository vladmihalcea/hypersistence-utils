package io.hypersistence.utils.spring.repo.projection;

import io.hypersistence.utils.spring.domain.Post;
import io.hypersistence.utils.spring.domain.User;
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

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

/**
 * @author Vlad Mihalcea
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = SpringDataProjectionConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class SpringDataProjectionTest {

    protected final Logger LOGGER = LoggerFactory.getLogger(getClass());

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private UserRepository userRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    public void test() {
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

    @Test
    public void testMemberOf() {
        transactionTemplate.execute((TransactionCallback<Void>) transactionStatus -> {
            userRepository.persist(
                new User()
                    .setId(1L)
                    .setFirstName("Vlad")
                    .setLastName("Mihalcea")
                    .addRole(User.Role.ADMIN)
            );

            return null;
        });

        List<User> users = transactionTemplate.execute(transactionStatus ->
            userRepository.findByRole(User.Role.ADMIN)
        );

        assertEquals(1, users.size());
        User user = users.get(0);
        assertEquals(1L, user.getId().longValue());
    }
}

