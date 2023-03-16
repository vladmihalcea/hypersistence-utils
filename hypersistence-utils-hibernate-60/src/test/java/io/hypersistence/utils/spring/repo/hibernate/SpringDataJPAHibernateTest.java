package io.hypersistence.utils.spring.repo.hibernate;

import io.hypersistence.utils.spring.domain.Post;
import jakarta.persistence.EntityManager;
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
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import static org.junit.Assert.fail;

/**
 * @author Vlad Mihalcea
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = SpringDataJPAHibernateConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class SpringDataJPAHibernateTest {

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
    public void testSave() {
        try {
            transactionTemplate.execute((TransactionCallback<Void>) transactionStatus -> {
                postRepository.save(
                    new Post()
                        .setId(1L)
                        .setTitle("High-Performance Java Persistence")
                        .setSlug("high-performance-java-persistence")
                );
                return null;
            });

            fail("Should throw UnsupportedOperationException!");
        } catch (UnsupportedOperationException expected) {
            LOGGER.warn("You shouldn't call the JpaRepository save method!");
        }
    }

    @Test
    public void testFindAll() {
        try {
            transactionTemplate.execute((TransactionCallback<Void>) transactionStatus -> {
                postRepository.findAll();
                return null;
            });

            fail("Should throw UnsupportedOperationException!");
        } catch (UnsupportedOperationException expected) {
            LOGGER.warn("You shouldn't call the JpaRepository findAll method!");
        }
    }
}

