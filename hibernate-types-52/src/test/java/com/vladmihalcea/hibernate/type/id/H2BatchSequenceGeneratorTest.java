package com.vladmihalcea.hibernate.type.id;

import static org.junit.Assert.assertEquals;

import java.util.List;
import java.util.Properties;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Table;

import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Parameter;
import org.hibernate.cfg.AvailableSettings;
import org.junit.Test;

import com.vladmihalcea.hibernate.type.util.AbstractTest;
import com.vladmihalcea.hibernate.type.util.providers.DataSourceProvider;
import com.vladmihalcea.hibernate.type.util.providers.H2DataSourceProvider;
import com.vladmihalcea.hibernate.type.util.transaction.JPATransactionVoidFunction;

import net.ttddyy.dsproxy.QueryCount;
import net.ttddyy.dsproxy.QueryCountHolder;

/**
 * @author Philippe Marschall
 */
public class H2BatchSequenceGeneratorTest extends AbstractTest {

    private static final int BATCH_SIZE = 50;

    @Override
    protected Class<?>[] entities() {
        return new Class<?>[]{
            Post.class
        };
    }

    @Override
    protected DataSourceProvider dataSourceProvider() {
        return new H2DataSourceProvider();
    }

    @Override
    protected void additionalProperties(Properties properties) {
        properties.put(AvailableSettings.BATCH_VERSIONED_DATA, true);
        properties.put(AvailableSettings.STATEMENT_BATCH_SIZE, BATCH_SIZE);
        properties.put(AvailableSettings.ORDER_UPDATES, true);
        properties.put(AvailableSettings.ORDER_INSERTS, true);
    }

    @Test
    public void test() {

        QueryCountHolder.clear();
        doInJPA((JPATransactionVoidFunction) entityManager -> {
            for (int i = 0; i < BATCH_SIZE; i++) {
                Post post = new Post();

                post.setTitle("Post " + i + 1);
                entityManager.persist(post);
            }
        });
        
        QueryCount queryCount = QueryCountHolder.getGrandTotal();
        assertEquals(1L, queryCount.getInsert());
        // Recursive CTEs are recognized as OTHER instead of SELECT by datasource-proxy
        // https://github.com/ttddyy/datasource-proxy/issues/76
        assertEquals(1L, queryCount.getOther());
        assertEquals(0L, queryCount.getSelect());
        assertEquals(2L, queryCount.getTotal());

        doInJPA(entityManager -> {
            List<Post> posts = entityManager.createQuery("SELECT p FROM Post p", Post.class).getResultList();
            assertEquals(BATCH_SIZE, posts.size());
        });
    }

    @Entity(name = "Post")
    @Table(name = "post")
    public static class Post {

        @Id
        @GenericGenerator(
                        name = "post_sequence",
                        strategy = "com.vladmihalcea.hibernate.type.id.BatchSequenceGenerator",
                        parameters = {
                                @Parameter(name = "sequence", value = "SEQ_PARENT_ID"),
                                @Parameter(name = "fetch_size", value = "" + BATCH_SIZE)
                        })
        @GeneratedValue(generator = "post_sequence")
        private Long id;

        private String title;

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
    }

}
