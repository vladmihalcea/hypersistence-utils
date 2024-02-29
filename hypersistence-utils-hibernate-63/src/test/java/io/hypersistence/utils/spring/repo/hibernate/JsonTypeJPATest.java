package io.hypersistence.utils.spring.repo.hibernate;

import com.google.common.collect.HashMultimap;
import io.hypersistence.utils.spring.domain.Movie;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.Optional;
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = SpringDataJPAHibernateConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class JsonTypeJPATest {

    protected final Logger LOGGER = LoggerFactory.getLogger(getClass());

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private MovieRepository movieRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    public void test() {
        transactionTemplate.execute((TransactionCallback<Void>) transactionStatus -> {
            movieRepository.persist(
                    new Movie()
                            .setName("No Time to Die")
                            .addCast("actor", "Daniel Craig")
                            .addCast("actor", "Rami Malek")
            );
            movieRepository.persistAndFlush(
                    new Movie()
                            .setName("Thunderball")
            );
            return null;
        });

        transactionTemplate.execute(transactionStatus -> {
            HashMultimap<String, String> cast = HashMultimap.create();
            cast.put("actor", "Sean Connery");
            cast.put("actor", "Adolfo Celi");
            movieRepository.updateActors("Thunderball", cast);
            return null;
        });

        Optional<Movie> movie = transactionTemplate.execute(transactionStatus ->
                movieRepository.findById("Thunderball")
        );

        assertNotNull(movie);
        assertTrue(movie.isPresent());
        Movie thunderball = movie.get();
        HashMultimap<String, String> cast = thunderball.getCast();
        assertNotNull(cast);
        assertEquals(2, cast.size());
    }
}

