package io.hypersistence.utils.spring.repo.base;

import io.hypersistence.utils.jdbc.validator.SQLStatementCountValidator;
import io.hypersistence.utils.spring.domain.Post;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author Vlad Mihalcea
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = SpringDataJPABaseConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class SpringDataJPABaseTest {

    protected final Logger LOGGER = LoggerFactory.getLogger(getClass());

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private PostRepository postRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    public void testPersistAndMerge() {
        String slug = "high-performance-java-persistence";

        transactionTemplate.execute((TransactionCallback<Void>) transactionStatus -> {
            postRepository.persist(
                new Post()
                    .setId(1L)
                    .setTitle("High-Performance Java Persistence")
                    .setSlug("high-performance-java-persistence")
            );

            postRepository.persistAndFlush(
                new Post()
                    .setId(2L)
                    .setTitle("Hypersistence Optimizer")
                    .setSlug("hypersistence-optimizer")
            );

            postRepository.persistAllAndFlush(
                LongStream.range(3, 1000)
                    .mapToObj(i -> new Post()
                        .setId(i)
                        .setTitle(String.format("Post %d", i))
                        .setSlug(String.format("post-%d", i))
                    )
                    .collect(Collectors.toList())
            );

            return null;
        });

        List<Post> posts = transactionTemplate.execute(transactionStatus ->
            entityManager.createQuery(
                "select p " +
                "from Post p " +
                "where p.id < 10", Post.class)
            .getResultList()
        );

        posts.forEach(post -> post.setTitle(post.getTitle() + " rocks!"));

        transactionTemplate.execute(transactionStatus ->
            postRepository.updateAll(posts)
        );
    }

    @Test
    public void testUpdateAllBatching() {
        final List<Post> posts = new ArrayList<>();

        transactionTemplate.execute((TransactionCallback<Void>) transactionStatus -> {
            for (long i = 1; i <= 10; i++) {
                Post post = new Post()
                    .setId(i)
                    .setTitle("High-Performance Java Persistence, page " + i)
                    .setSlug("high-performance-java-persistence-" + i);
                posts.add(post);
                postRepository.persist(post);
            }

            return null;
        });

        posts.forEach(post -> {
            post.setTitle(post.getTitle() + " is great!");
        });

        SQLStatementCountValidator.reset();

        // The updates go through the managed Session, so they are flushed (and batched,
        // since hibernate.jdbc.batch_size is enabled) when the transaction commits.
        transactionTemplate.execute((TransactionCallback<Void>) transactionStatus -> {
            postRepository.updateAll(posts);
            return null;
        });

        SQLStatementCountValidator.assertUpdateCount(1);
    }

    @Test
    public void testUpdateAllBatchingWithinActiveTransaction() {
        final List<Post> posts = new ArrayList<>();

        transactionTemplate.execute((TransactionCallback<Void>) transactionStatus -> {
            for (long i = 1; i <= 10; i++) {
                Post post = new Post()
                    .setId(i)
                    .setTitle("High-Performance Java Persistence, page " + i)
                    .setSlug("high-performance-java-persistence-" + i);
                posts.add(post);
                postRepository.persist(post);
            }

            return null;
        });

        posts.forEach(post -> {
            post.setTitle(post.getTitle() + " is great!");
        });

        SQLStatementCountValidator.reset();

        transactionTemplate.execute((TransactionCallback<Void>) transactionStatus -> {
            assertTrue(
                "A Spring transaction must be active so that the managed Session batches the update at flush time",
                TransactionSynchronizationManager.isActualTransactionActive()
            );

            postRepository.updateAll(posts);
            return null;
        });

        SQLStatementCountValidator.assertUpdateCount(1);
    }

    @Test
    public void testLockById() {
        transactionTemplate.execute((TransactionCallback<Void>) transactionStatus -> {
            postRepository.persist(
                new Post()
                    .setId(1L)
                    .setTitle("High-Performance Java Persistence")
                    .setSlug("high-performance-java-persistence")
            );
            return null;
        });

        transactionTemplate.execute((TransactionCallback<Void>) transactionStatus -> {
            Post post = postRepository.lockById(1L, LockModeType.PESSIMISTIC_WRITE);

            assertEquals(LockModeType.PESSIMISTIC_WRITE, entityManager.getLockMode(post));

            return null;
        });
    }

    @Test
    public void testGetReferenceById() {
        transactionTemplate.execute((TransactionCallback<Void>) transactionStatus -> {
            postRepository.persist(
                new Post()
                    .setId(1L)
                    .setTitle("High-Performance Java Persistence")
                    .setSlug("high-performance-java-persistence")
            );
            return null;
        });

        Post post = transactionTemplate.execute(transactionStatus -> postRepository.getReferenceById(1L));

        assertEquals(Long.valueOf(1L), post.getId());

        try {
            post.getTitle();

            fail("Should have thrown LazyInitializationException");
        } catch (Exception expected) {
        }
    }
}

