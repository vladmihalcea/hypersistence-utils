package io.hypersistence.utils.hibernate.query;

import io.hypersistence.utils.hibernate.util.AbstractPostgreSQLIntegrationTest;
import jakarta.persistence.*;
import org.junit.Test;

import java.time.LocalDate;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

/**
 * @author Vlad Mihalcea
 */
public class MapResultTransformerTest extends AbstractPostgreSQLIntegrationTest {

    @Override
    protected Class<?>[] entities() {
        return new Class<?>[]{
            Post.class
        };
    }

    @Override
    public void afterInit() {
        doInJPA(entityManager -> {
            entityManager.persist(
                new Post()
                    .setId(1L)
                    .setTitle(
                        "High-Performance Java Persistence eBook " +
                        "has been released!"
                    )
                    .setCreatedOn(LocalDate.of(2016, 8, 30))
            );

            entityManager.persist(
                new Post()
                    .setId(2L)
                    .setTitle(
                        "High-Performance Java Persistence paperback " +
                        "has been released!"
                    )
                    .setCreatedOn(LocalDate.of(2016, 10, 12))
            );

            entityManager.persist(
                new Post()
                    .setId(3L)
                    .setTitle(
                        "High-Performance Java Persistence Mach 1 video course " +
                        "has been released!"
                    )
                    .setCreatedOn(LocalDate.of(2018, 1, 30))
            );

            entityManager.persist(
                new Post()
                    .setId(4L)
                    .setTitle(
                        "High-Performance Java Persistence Mach 2 video course " +
                        "has been released!"
                    )
                    .setCreatedOn(LocalDate.of(2018, 5, 8))
            );

            entityManager.persist(
                new Post()
                    .setId(5L)
                    .setTitle(
                        "Hypersistence Optimizer " +
                        "has been released!"
                    )
                    .setCreatedOn(LocalDate.of(2019, 3, 19))
            );
        });
    }

    @Test
    public void testGroupByStreamCollector() {
        doInJPA(entityManager -> {
            Map<Integer, Integer> postCountByYearMap = entityManager
            .createQuery(
                "select " +
                "   YEAR(p.createdOn) as year, " +
                "   count(p) as postCount " +
                "from " +
                "   Post p " +
                "group by " +
                "   YEAR(p.createdOn)", Tuple.class)
            .getResultStream()
            .collect(
                Collectors.toMap(
                    tuple -> ((Number) tuple.get("year")).intValue(),
                    tuple -> ((Number) tuple.get("postCount")).intValue()
                )
            );

            assertEquals(2, postCountByYearMap.get(2016).intValue());
            assertEquals(2, postCountByYearMap.get(2018).intValue());
            assertEquals(1, postCountByYearMap.get(2019).intValue());
        });
    }

    @Test
    public void testGroupByListStreamCollector() {
        doInJPA(entityManager -> {
            Map<Integer, Integer> postCountByYearMap = entityManager
            .createQuery(
                "select " +
                "   YEAR(p.createdOn) as year, " +
                "   count(p) as postCount " +
                "from " +
                "   Post p " +
                "group by " +
                "   YEAR(p.createdOn)", Tuple.class)
            .getResultList()
            .stream()
            .collect(
                Collectors.toMap(
                    tuple -> ((Number) tuple.get("year")).intValue(),
                    tuple -> ((Number) tuple.get("postCount")).intValue()
                )
            );

            assertEquals(2, postCountByYearMap.get(2016).intValue());
            assertEquals(2, postCountByYearMap.get(2018).intValue());
            assertEquals(1, postCountByYearMap.get(2019).intValue());
        });
    }

    @Test
    public void testMapResultTransformerImplicitAlias() {
        doInJPA(entityManager -> {
            Map<Number, Number> postCountByYearMap = (Map<Number, Number>) entityManager
            .createQuery(
                "select " +
                "   YEAR(p.createdOn) as year, " +
                "   count(p) as postCount " +
                "from " +
                "   Post p " +
                "group by " +
                "   YEAR(p.createdOn)")
            .unwrap(org.hibernate.query.Query.class)
            .setResultTransformer(
                new MapResultTransformer<Number, Number>()
            )
            .getSingleResult();

            assertEquals(2, postCountByYearMap.get(2016).intValue());
            assertEquals(2, postCountByYearMap.get(2018).intValue());
            assertEquals(1, postCountByYearMap.get(2019).intValue());
        });
    }

    @Test
    public void testMapResultTransformerExplicitAlias() {
        doInJPA(entityManager -> {
            Map<Number, Number> postCountByYearMap = (Map<Number, Number>) entityManager
            .createQuery(
                "select " +
                "   count(p) as map_value, " +
                "   YEAR(p.createdOn) as map_key " +
                "from Post p " +
                "group by " +
                "   YEAR(p.createdOn)")
            .unwrap(org.hibernate.query.Query.class)
            .setResultTransformer(
                new MapResultTransformer<Number, Number>()
            )
            .getSingleResult();

            assertEquals(2, postCountByYearMap.get(2016).intValue());
            assertEquals(2, postCountByYearMap.get(2018).intValue());
            assertEquals(1, postCountByYearMap.get(2019).intValue());
        });
    }

    @Entity(name = "Post")
    @Table(name = "post")
    public static class Post {

        @Id
        private Long id;

        private String title;

        @Column(name = "created_on")
        private LocalDate createdOn;

        public Long getId() {
            return id;
        }

        public Post setId(Long id) {
            this.id = id;
            return this;
        }

        public String getTitle() {
            return title;
        }

        public Post setTitle(String title) {
            this.title = title;
            return this;
        }

        public LocalDate getCreatedOn() {
            return createdOn;
        }

        public Post setCreatedOn(LocalDate createdOn) {
            this.createdOn = createdOn;
            return this;
        }
    }
}
