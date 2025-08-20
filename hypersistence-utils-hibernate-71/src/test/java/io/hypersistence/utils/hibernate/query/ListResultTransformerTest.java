package io.hypersistence.utils.hibernate.query;

import io.hypersistence.utils.hibernate.util.AbstractPostgreSQLIntegrationTest;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.transform.ResultTransformer;
import org.junit.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * @author Vlad Mihalcea
 */
public class ListResultTransformerTest extends AbstractPostgreSQLIntegrationTest {

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
                        "High-Performance Java Persistence " +
                        "eBook has been released!")
                    .setCreatedOn(LocalDate.of(2016, 8, 30))
            );

            entityManager.persist(
                new Post()
                    .setId(2L)
                    .setTitle(
                        "High-Performance Java Persistence " +
                        "paperback has been released!")
                    .setCreatedOn(LocalDate.of(2016, 10, 12))
            );

            entityManager.persist(
                new Post()
                    .setId(3L)
                    .setTitle(
                        "High-Performance Java Persistence " +
                        "Mach 1 video course has been released!")
                    .setCreatedOn(LocalDate.of(2018, 1, 30))
            );

            entityManager.persist(
                new Post()
                    .setId(4L)
                    .setTitle(
                        "High-Performance Java Persistence " +
                        "Mach 2 video course has been released!")
                    .setCreatedOn(LocalDate.of(2018, 5, 8))
            );

            entityManager.persist(
                new Post()
                    .setId(5L)
                    .setTitle(
                        "Hypersistence Optimizer has been released!")
                    .setCreatedOn(LocalDate.of(2019, 3, 19))
            );
        });
    }

    public static class PostCountByYear {

        private final int year;

        private final int postCount;

        public PostCountByYear(int year, int postCount) {
            this.year = year;
            this.postCount = postCount;
        }

        public int getYear() {
            return year;
        }

        public int getPostCount() {
            return postCount;
        }
    }

    @Test
    public void testTransformer() {
        doInJPA(entityManager -> {
            List<PostCountByYear> postCountByYearMap = (List<PostCountByYear>) entityManager
            .createQuery(
                "select " +
                "   YEAR(p.createdOn) as year, " +
                "   count(p) as postCount " +
                "from Post p " +
                "group by " +
                "   YEAR(p.createdOn) " +
                "order by " +
                "   YEAR(p.createdOn)")
            .unwrap(org.hibernate.query.Query.class)
            .setResultTransformer(
                new ResultTransformer() {
                    @Override
                    public Object transformTuple(Object[] tuple, String[] aliases) {
                        return new PostCountByYear(
                            ((Number) tuple[0]).intValue(),
                            ((Number) tuple[1]).intValue()
                        );
                    }

                    @Override
                    public List transformList(List tuples) {
                        return tuples;
                    }
                }
            )
            .getResultList();

            assertEquals(2016, postCountByYearMap.get(0).getYear());
            assertEquals(2, postCountByYearMap.get(0).getPostCount());

            assertEquals(2018, postCountByYearMap.get(1).getYear());
            assertEquals(2, postCountByYearMap.get(1).getPostCount());

            assertEquals(2019, postCountByYearMap.get(2).getYear());
            assertEquals(1, postCountByYearMap.get(2).getPostCount());
        });
    }

    @Test
    public void testListResultTransformer() {
        doInJPA(entityManager -> {
            List<PostCountByYear> postCountByYearMap = (List<PostCountByYear>) entityManager
                .createQuery(
                    "select " +
                    "   YEAR(p.createdOn) as year, " +
                    "   count(p) as postCount " +
                    "from Post p " +
                    "group by " +
                    "   YEAR(p.createdOn) " +
                    "order by " +
                    "   YEAR(p.createdOn)")
                .unwrap(org.hibernate.query.Query.class)
                .setResultTransformer(
                    (ListResultTransformer) (tuple, aliases) -> new PostCountByYear(
                        ((Number) tuple[0]).intValue(),
                        ((Number) tuple[1]).intValue()
                    )
                )
                .getResultList();

            assertEquals(2016, postCountByYearMap.get(0).getYear());
            assertEquals(2, postCountByYearMap.get(0).getPostCount());

            assertEquals(2018, postCountByYearMap.get(1).getYear());
            assertEquals(2, postCountByYearMap.get(1).getPostCount());

            assertEquals(2019, postCountByYearMap.get(2).getYear());
            assertEquals(1, postCountByYearMap.get(2).getPostCount());
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
