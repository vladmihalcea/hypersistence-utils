package io.hypersistence.utils.hibernate.id;

import io.hypersistence.utils.hibernate.util.AbstractTest;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import net.ttddyy.dsproxy.QueryCount;
import net.ttddyy.dsproxy.QueryCountHolder;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.dialect.Database;
import org.junit.Test;

import java.util.List;
import java.util.Properties;

import static org.junit.Assert.assertEquals;

/**
 * @author Vlad Mihalcea
 */
public class PostgreSQLPooledLoSequenceOptimizerGeneratorTest
    extends AbstractSequenceOptimizerGeneratorTest<PostgreSQLPooledLoSequenceOptimizerGeneratorTest.Post> {

    private static final int BATCH_SIZE = 10;

    public PostgreSQLPooledLoSequenceOptimizerGeneratorTest() {
        super(Post.class);
    }

    @Override
    protected Object newPost(int index) {
        Post post = new Post();
        post.setTitle("Post " + index + 1);
        return post;
    }

    @Entity(name = "Post")
    @Table(name = "post")
    public static class Post {

        @Id
        @SequenceOptimizer(
            sequenceName = "post_id_seq",
            incrementSize = BATCH_SIZE,
            optimizer = "pooled-lo"
        )
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
