package io.hypersistence.utils.hibernate.id;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * @author Vlad Mihalcea
 */
public class PostgreSQLThreadLocalPooledLoSequenceOptimizerGeneratorTest
    extends AbstractSequenceOptimizerGeneratorTest<PostgreSQLThreadLocalPooledLoSequenceOptimizerGeneratorTest.Post> {

    private static final int BATCH_SIZE = 10;

    public PostgreSQLThreadLocalPooledLoSequenceOptimizerGeneratorTest() {
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
            optimizer = "POOLED_LOTL"
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
