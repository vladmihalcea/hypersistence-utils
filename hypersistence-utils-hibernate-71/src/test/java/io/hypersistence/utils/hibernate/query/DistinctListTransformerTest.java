package io.hypersistence.utils.hibernate.query;

import io.hypersistence.utils.hibernate.util.AbstractTest;
import jakarta.persistence.*;
import org.hibernate.query.TupleTransformer;
import org.junit.Test;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.Assert.assertEquals;

/**
 * @author Vlad Mihalcea
 */
@SuppressWarnings("unchecked")
public class DistinctListTransformerTest extends AbstractTest {

    @Override
    protected Class<?>[] entities() {
        return new Class<?>[] {
            Post.class,
            PostComment.class
        };
    }

    @Override
    public void afterInit() {
        doInJPA(entityManager -> {
            entityManager.persist(
                new Post()
                    .setId(1L)
                    .setTitle("High-Performance Java Persistence")
                    .setCreatedBy("Vlad Mihalcea")
                    .setCreatedOn(
                        LocalDateTime.of(2016, 11, 2, 12, 0, 0)
                    )
                    .setUpdatedBy("Vlad Mihalcea")
                    .setUpdatedOn(
                        LocalDateTime.now()
                    )
                    .addComment(
                        new PostComment()
                            .setId(1L)
                            .setReview("Best book on JPA and Hibernate!")
                    )
                    .addComment(
                        new PostComment()
                            .setId(2L)
                            .setReview("A must-read for every Java developer!")
                    )
            );


            entityManager.persist(
                new Post()
                    .setId(2L)
                    .setTitle("Hypersistence Optimizer")
                    .setCreatedBy("Vlad Mihalcea")
                    .setCreatedOn(
                        LocalDateTime.of(2019, 3, 19, 12, 0, 0)
                    )
                    .setUpdatedBy("Vlad Mihalcea")
                    .setUpdatedOn(
                        LocalDateTime.now()
                    )
                    .addComment(
                        new PostComment()
                            .setId(3L)
                            .setReview("It's like pair programming with Vlad!")
                    )
            );
        });
    }

    @Test
    public void testParentChildDTOProjectionNativeQueryTupleTransformer() {
        doInJPA( entityManager -> {
            List<PostDTO> postDTOs = entityManager.createNativeQuery(
                "SELECT p.id AS p_id, " +
                "       p.title AS p_title, " +
                "       pc.id AS pc_id, " +
                "       pc.review AS pc_review " +
                "FROM post p " +
                "JOIN post_comment pc ON p.id = pc.post_id " +
                "ORDER BY pc.id")
            .unwrap(org.hibernate.query.Query.class)
            .setTupleTransformer(new PostDTOTupleTransformer())
            .setResultListTransformer(DistinctListTransformer.INSTANCE)
            .getResultList();

            assertEquals(2, postDTOs.size());
            assertEquals(2, postDTOs.get(0).getComments().size());
            assertEquals(1, postDTOs.get(1).getComments().size());

            PostDTO post1DTO = postDTOs.get(0);

            assertEquals(1L, post1DTO.getId().longValue());
            assertEquals(2, post1DTO.getComments().size());
            assertEquals(1L, post1DTO.getComments().get(0).getId().longValue());
            assertEquals(2L, post1DTO.getComments().get(1).getId().longValue());

            PostDTO post2DTO = postDTOs.get(1);

            assertEquals(2L, post2DTO.getId().longValue());
            assertEquals(1, post2DTO.getComments().size());
            assertEquals(3L, post2DTO.getComments().get(0).getId().longValue());
        } );
    }

    @Entity(name = "Post")
    @Table(name = "post")
    public static class Post {

        @Id
        private Long id;

        private String title;

        @Column(name = "created_on")
        private LocalDateTime createdOn;

        @Column(name = "created_by")
        private String createdBy;

        @Column(name = "updated_on")
        private LocalDateTime updatedOn;

        @Column(name = "updated_by")
        private String updatedBy;

        @Version
        private Short version;

        @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)
        private List<PostComment> comments = new ArrayList<>();

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

        public LocalDateTime getCreatedOn() {
            return createdOn;
        }

        public Post setCreatedOn(LocalDateTime createdOn) {
            this.createdOn = createdOn;
            return this;
        }

        public String getCreatedBy() {
            return createdBy;
        }

        public Post setCreatedBy(String createdBy) {
            this.createdBy = createdBy;
            return this;
        }

        public LocalDateTime getUpdatedOn() {
            return updatedOn;
        }

        public Post setUpdatedOn(LocalDateTime updatedOn) {
            this.updatedOn = updatedOn;
            return this;
        }

        public String getUpdatedBy() {
            return updatedBy;
        }

        public Post setUpdatedBy(String updatedBy) {
            this.updatedBy = updatedBy;
            return this;
        }

        public Short getVersion() {
            return version;
        }

        public Post setVersion(Short version) {
            this.version = version;
            return this;
        }

        public List<PostComment> getComments() {
            return comments;
        }

        public Post addComment(PostComment comment) {
            comments.add(comment);
            comment.setPost(this);
            return this;
        }
    }

    @Entity
    @Table(name = "post_comment")
    public static class PostComment {

        @Id
        private Long id;

        @ManyToOne(fetch = FetchType.LAZY)
        private Post post;

        private String review;

        public Long getId() {
            return id;
        }

        public PostComment setId(Long id) {
            this.id = id;
            return this;
        }

        public Post getPost() {
            return post;
        }

        public PostComment setPost(Post post) {
            this.post = post;
            return this;
        }

        public String getReview() {
            return review;
        }

        public PostComment setReview(String review) {
            this.review = review;
            return this;
        }
    }

    public static class PostDTO {

        public static final String ID_ALIAS = "p_id";
        public static final String TITLE_ALIAS = "p_title";

        private Long id;

        private String title;

        private List<PostCommentDTO> comments = new ArrayList<>();

        public PostDTO() {
        }

        public PostDTO(Long id, String title) {
            this.id = id;
            this.title = title;
        }

        public PostDTO(Object[] tuples, Map<String, Integer> aliasToIndexMap) {
            this.id = longValue(tuples[aliasToIndexMap.get(ID_ALIAS)]);
            this.title = stringValue(tuples[aliasToIndexMap.get(TITLE_ALIAS)]);
        }

        public Long getId() {
            return id;
        }

        public void setId(Number id) {
            this.id = id.longValue();
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public List<PostCommentDTO> getComments() {
            return comments;
        }
    }

    public static class PostCommentDTO {
        public static final String ID_ALIAS = "pc_id";
        public static final String REVIEW_ALIAS = "pc_review";

        private Long id;

        private String review;

        public PostCommentDTO(Long id, String review) {
            this.id = id;
            this.review = review;
        }

        public PostCommentDTO(Object[] tuples, Map<String, Integer> aliasToIndexMap) {
            this.id = longValue(tuples[aliasToIndexMap.get(ID_ALIAS)]);
            this.review = stringValue(tuples[aliasToIndexMap.get(REVIEW_ALIAS)]);
        }

        public Long getId() {
            return id;
        }

        public void setId(Number id) {
            this.id = id.longValue();
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getReview() {
            return review;
        }

        public void setReview(String review) {
            this.review = review;
        }
    }

    public static class PostDTOTupleTransformer implements TupleTransformer {

        private Map<Long, PostDTO> postDTOMap = new LinkedHashMap<>();

        @Override
        public PostDTO transformTuple(Object[] tuple, String[] aliases) {
            Map<String, Integer> aliasToIndexMap = aliasToIndexMap(aliases);
            Long postId = longValue(tuple[aliasToIndexMap.get(PostDTO.ID_ALIAS)]);

            PostDTO postDTO = postDTOMap.computeIfAbsent(
                postId,
                id -> new PostDTO(tuple, aliasToIndexMap)
            );
            postDTO.getComments().add(new PostCommentDTO(tuple, aliasToIndexMap));

            return postDTO;
        }

        private Map<String, Integer> aliasToIndexMap(String[] aliases) {
            Map<String, Integer> aliasToIndexMap = new LinkedHashMap<>();
            for (int i = 0; i < aliases.length; i++) {
                aliasToIndexMap.put(aliases[i].toLowerCase(Locale.ROOT), i);
            }
            return aliasToIndexMap;
        }
    }
}
