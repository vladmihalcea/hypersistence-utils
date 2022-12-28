package io.hypersistence.utils.hibernate.type.json;

import io.hypersistence.utils.hibernate.util.AbstractPostgreSQLIntegrationTest;
import io.hypersistence.utils.jdbc.validator.SQLStatementCountValidator;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;
import org.hibernate.annotations.TypeDefs;
import org.junit.Test;

import javax.persistence.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Vlad Mihalcea
 */
public class PostgreSQLJsonBinaryTypeNestedCollectionExplicitEqualsTest extends AbstractPostgreSQLIntegrationTest {

    @Override
    protected Class<?>[] entities() {
        return new Class<?>[]{
            Post.class,
        };
    }

    private Post _post;

    @Override
    protected void afterInit() {
        SQLStatementCountValidator.reset();

        doInJPA(entityManager -> {
            List<String> attributes = new ArrayList<>();
            attributes.add("JPA");
            attributes.add("Hibernate");

            PostAttributes customAttributes = new PostAttributes();
            customAttributes.setAttributes(attributes);

            Post post = new Post();
            post.setCustomAttributes(customAttributes);

            entityManager.persist(post);

            _post = post;
        });

        SQLStatementCountValidator.assertTotalCount(1);
        SQLStatementCountValidator.assertInsertCount(1);
    }

    @Test
    public void testLoad() {
        SQLStatementCountValidator.reset();

        doInJPA(entityManager -> {
            Post post = entityManager.find(Post.class, _post.getId());
            List<String> attributes = post.getCustomAttributes().getAttributes();

            assertTrue(attributes.contains("JPA"));
            assertTrue(attributes.contains("Hibernate"));
        });

        SQLStatementCountValidator.assertTotalCount(1);
        SQLStatementCountValidator.assertSelectCount(1);
        SQLStatementCountValidator.assertUpdateCount(0);
    }

    @TypeDefs({@TypeDef(name = "jsonb", typeClass = JsonBinaryType.class)})
    @Entity(name = "Post")
    @Table(name = "post")
    public static class Post {

        @GeneratedValue(strategy = GenerationType.IDENTITY)
        @Id
        private Long id;

        @Type(type = "jsonb")
        @Column(columnDefinition = "jsonb")
        private PostAttributes customAttributes;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public PostAttributes getCustomAttributes() {
            return customAttributes;
        }

        public void setCustomAttributes(PostAttributes customAttributes) {
            this.customAttributes = customAttributes;
        }
    }

    public static class PostAttributes {

        private List<String> attributes;

        public List<String> getAttributes() {
            return attributes;
        }

        public void setAttributes(List<String> attributes) {
            this.attributes = attributes;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof PostAttributes)) return false;
            PostAttributes that = (PostAttributes) o;
            return Objects.equals(attributes, that.attributes);
        }

        @Override
        public int hashCode() {
            return Objects.hash(attributes);
        }
    }

}
