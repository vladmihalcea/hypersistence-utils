package com.vladmihalcea.hibernate.type.json;

import com.vladmihalcea.hibernate.util.AbstractPostgreSQLIntegrationTest;
import jakarta.persistence.*;
import net.ttddyy.dsproxy.QueryCount;
import net.ttddyy.dsproxy.QueryCountHolder;
import org.hibernate.annotations.Type;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Vlad Mihalcea
 */
public class PostgreSQLJsonBinaryTypeNestedCollectionNoEqualsTest extends AbstractPostgreSQLIntegrationTest {

    @Override
    protected Class<?>[] entities() {
        return new Class<?>[]{
            Post.class,
        };
    }

    private Post _post;

    @Override
    protected void afterInit() {
        QueryCountHolder.clear();

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

        QueryCount queryCount = QueryCountHolder.getGrandTotal();
        assertEquals(1, queryCount.getTotal());
        assertEquals(1, queryCount.getInsert());
    }

    @Test
    public void testLoad() {
        QueryCountHolder.clear();

        doInJPA(entityManager -> {
            Post post = entityManager.find(Post.class, _post.getId());
            List<String> attributes = post.getCustomAttributes().getAttributes();

            assertTrue(attributes.contains("JPA"));
            assertTrue(attributes.contains("Hibernate"));
        });

        QueryCount queryCount = QueryCountHolder.getGrandTotal();
        assertEquals(1, queryCount.getTotal());
        assertEquals(1, queryCount.getSelect());
        assertEquals(0, queryCount.getUpdate());
    }

    @Entity(name = "Post")
    @Table(name = "post")
    public static class Post {

        @GeneratedValue(strategy = GenerationType.IDENTITY)
        @Id
        private Long id;

        @Type(JsonBinaryType.class)
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
    }

}
