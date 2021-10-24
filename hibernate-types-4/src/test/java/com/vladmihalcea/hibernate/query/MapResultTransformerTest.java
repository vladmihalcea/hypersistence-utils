package com.vladmihalcea.hibernate.query;

import com.vladmihalcea.hibernate.type.util.AbstractPostgreSQLIntegrationTest;
import com.vladmihalcea.hibernate.type.util.transaction.JPATransactionFunction;
import org.junit.Test;

import javax.persistence.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;

import static org.junit.Assert.assertEquals;

/**
 * @author Vlad Mihalcea
 */
public class MapResultTransformerTest extends AbstractPostgreSQLIntegrationTest {

    private SimpleDateFormat DATE_FORMATTER = new SimpleDateFormat("yyyy-MM-dd");

    @Override
    protected Class<?>[] entities() {
        return new Class<?>[]{
            Post.class
        };
    }

    private Date parseDate(String dateString) {
        try {
            return DATE_FORMATTER.parse(dateString);
        } catch (ParseException e) {
            throw new IllegalArgumentException(e);
        }
    }

    @Override
    public void afterInit() {
        doInJPA(new JPATransactionFunction<Void>() {

            @Override
            public Void apply(EntityManager entityManager) {
                entityManager.persist(
                    new Post()
                        .setId(1L)
                        .setTitle("High-Performance Java Persistence eBook has been released!")
                        .setCreatedOn(parseDate("2016-8-30"))
                );

                entityManager.persist(
                    new Post()
                        .setId(2L)
                        .setTitle("High-Performance Java Persistence paperback has been released!")
                        .setCreatedOn(parseDate("2016-10-12"))
                );

                entityManager.persist(
                    new Post()
                        .setId(3L)
                        .setTitle("High-Performance Java Persistence Mach 1 video course has been released!")
                        .setCreatedOn(parseDate("2018-1-30"))
                );

                entityManager.persist(
                    new Post()
                        .setId(4L)
                        .setTitle("High-Performance Java Persistence Mach 2 video course has been released!")
                        .setCreatedOn(parseDate("2018-5-8"))
                );

                entityManager.persist(
                    new Post()
                        .setId(5L)
                        .setTitle("Hypersistence Optimizer has been released!")
                        .setCreatedOn(parseDate("2019-3-19"))
                );

                return null;
            }
        });
    }

    @Test
    public void testMapResultTransformerImplicitAlias() {
        doInJPA(new JPATransactionFunction<Void>() {

            @Override
            public Void apply(EntityManager entityManager) {
                Map<Number, Number> postCountByYearMap = (Map<Number, Number>) entityManager
                    .createQuery(
                        "select " +
                        "   YEAR(p.createdOn) as year, " +
                        "   count(p) as postCount " +
                        "from Post p " +
                        "group by " +
                        "   YEAR(p.createdOn)")
                    .unwrap(org.hibernate.Query.class)
                    .setResultTransformer(
                        new MapResultTransformer<Number, Number>()
                    )
                    .uniqueResult();

                assertEquals(2, postCountByYearMap.get(2016).intValue());
                assertEquals(2, postCountByYearMap.get(2018).intValue());
                assertEquals(1, postCountByYearMap.get(2019).intValue());

                return null;
            }
        });
    }

    @Test
    public void testMapResultTransformerExplicitAlias() {
        doInJPA(new JPATransactionFunction<Void>() {

            @Override
            public Void apply(EntityManager entityManager) {
                Map<Number, Number> postCountByYearMap = (Map<Number, Number>) entityManager
                    .createQuery(
                        "select " +
                        "   count(p) as map_value, " +
                        "   YEAR(p.createdOn) as map_key " +
                        "from Post p " +
                        "group by " +
                        "   YEAR(p.createdOn)")
                    .unwrap(org.hibernate.Query.class)
                    .setResultTransformer(
                        new MapResultTransformer<Number, Number>()
                    )
                    .uniqueResult();

                assertEquals(2, postCountByYearMap.get(2016).intValue());
                assertEquals(2, postCountByYearMap.get(2018).intValue());
                assertEquals(1, postCountByYearMap.get(2019).intValue());

                return null;
            }
        });
    }

    @Entity(name = "Post")
    @Table(name = "post")
    public static class Post {

        @Id
        private Long id;

        private String title;

        @Column(name = "created_on")
        private Date createdOn;

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

        public Date getCreatedOn() {
            return createdOn;
        }

        public Post setCreatedOn(Date createdOn) {
            this.createdOn = createdOn;
            return this;
        }
    }
}
