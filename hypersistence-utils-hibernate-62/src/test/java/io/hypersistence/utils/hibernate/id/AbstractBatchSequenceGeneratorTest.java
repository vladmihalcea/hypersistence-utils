package io.hypersistence.utils.hibernate.id;

import io.hypersistence.utils.hibernate.util.AbstractTest;
import io.hypersistence.utils.test.providers.DataSourceProvider;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import net.ttddyy.dsproxy.QueryCount;
import net.ttddyy.dsproxy.QueryCountHolder;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Parameter;
import org.hibernate.cfg.AvailableSettings;
import org.junit.Test;

import java.util.List;
import java.util.Properties;

import static org.junit.Assert.assertEquals;

/**
 * @author Philippe Marschall
 */
public abstract class AbstractBatchSequenceGeneratorTest extends AbstractTest {

    private static final int BATCH_SIZE = 50;

    @Override
    protected Class<?>[] entities() {
        return new Class<?>[]{
            Post.class
        };
    }

    @Override
    protected abstract DataSourceProvider dataSourceProvider();

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
        doInJPA(entityManager -> {
            for (int i = 0; i < BATCH_SIZE; i++) {
                Post post = new Post();

                post.setTitle("Post " + i + 1);
                entityManager.persist(post);
            }
        });

        QueryCount queryCount = QueryCountHolder.getGrandTotal();
        assertEquals(1L, queryCount.getInsert());
        assertEquals(1L, getSelectCount(queryCount));
        assertEquals(2L, queryCount.getTotal());

        doInJPA(entityManager -> {
            List<Post> posts = entityManager.createQuery("SELECT p FROM Post p", Post.class).getResultList();
            assertEquals(BATCH_SIZE, posts.size());
        });
    }

    /**
     * Recursive CTEs are not always recognized as OTHER instead of SELECT by datasource-proxy.
     * See @link https://github.com/ttddyy/datasource-proxy/issues/76
     *
     * @param queryCount query count
     * @return select statement count
     */
    protected long getSelectCount(QueryCount queryCount) {
        switch (dataSourceProvider().database()) {
            case ORACLE:
            case POSTGRESQL:
                return queryCount.getSelect();
            case SQLSERVER:
            case H2:
                return queryCount.getOther();
        }
        throw new UnsupportedOperationException(
            "Unsupported database: " + dataSourceProvider().database()
        );
    }

    @Entity(name = "Post")
    @Table(name = "post")
    public static class Post {

        @Id
        @GenericGenerator(
            name = "post_sequence",
            strategy = "io.hypersistence.utils.hibernate.id.BatchSequenceGenerator",
            parameters = {
                    @Parameter(name = "sequence", value = "SEQ_PARENT_ID"),
                    @Parameter(name = "fetch_size", value = "" + BATCH_SIZE)
            }
        )
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
