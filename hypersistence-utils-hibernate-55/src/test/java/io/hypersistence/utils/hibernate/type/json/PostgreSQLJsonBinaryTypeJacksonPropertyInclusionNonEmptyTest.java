package io.hypersistence.utils.hibernate.type.json;

import io.hypersistence.utils.hibernate.type.util.Configuration;
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


public class PostgreSQLJsonBinaryTypeJacksonPropertyInclusionNonEmptyTest extends AbstractPostgreSQLIntegrationTest {
    @Override
    protected Class<?>[] entities() {
        return new Class[]{
                MyEntity.class,
        };
    }

    @Override
    public void init() {
        System.setProperty(
                Configuration.PROPERTIES_FILE_PATH,
                "PostgreSQLJsonBinaryTypeJacksonPropertyInclusionNonEmptyTest.properties"
        );
        super.init();
    }

    private MyEntity _myEntity;

    @Override
    protected void afterInit() {
        SQLStatementCountValidator.reset();

        doInJPA(entityManager -> {
            MyEntity entity = new MyEntity();
            entity.addAttr("JPA");
            entityManager.persist(entity);

            _myEntity = entity;
        });

        SQLStatementCountValidator.assertTotalCount(1);
        SQLStatementCountValidator.assertInsertCount(1);
    }

    @Test
    public void testLoad() {
        SQLStatementCountValidator.reset();

        doInJPA(entityManager -> {
            MyEntity myEntity = entityManager.find(MyEntity.class, _myEntity.getId());
            myEntity.addAttr("Hibernate");
            entityManager.createQuery("select id from MyEntity where flag=false").getSingleResult();
        });

        SQLStatementCountValidator.assertTotalCount(3);
        SQLStatementCountValidator.assertSelectCount(2);
        SQLStatementCountValidator.assertUpdateCount(1);

        SQLStatementCountValidator.reset();

        doInJPA(entityManager -> {
            MyEntity myEntity = entityManager.find(MyEntity.class, _myEntity.getId());
            myEntity.clearAttr();
            entityManager.createQuery("select id from MyEntity where flag=false").getSingleResult();
        });

        SQLStatementCountValidator.assertTotalCount(3);
        SQLStatementCountValidator.assertSelectCount(2);
        SQLStatementCountValidator.assertUpdateCount(1);
    }

    @TypeDefs({@TypeDef(name = "jsonb", typeClass = JsonBinaryType.class)})
    @Entity(name = "MyEntity")
    @Table(name = "my_entity")
    public static class MyEntity {
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        @Id
        private Long id;

        @Column
        private boolean flag;

        @Type(type = "jsonb")
        @Column(columnDefinition = "jsonb")
        private Post post;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public Post getPost() {
            return post;
        }

        public void addAttr(String attr) {
            if (post == null) {
                post = new Post();
            }
            post.addAttr(attr);
        }

        public void clearAttr() {
            if (post != null) {
                post.clearAttr();
            }
        }
    }

    public static class Post {
        private List<String> attributes;

        public void addAttr(String attr) {
            if (attributes == null) {
                attributes = new ArrayList<>();
            }
            attributes.add(attr);
        }

        public void clearAttr() {
            if (attributes != null) {
                attributes.clear();
            }
        }

        public List<String> getAttributes() {
            return attributes;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Post outside = (Post) o;
            return Objects.equals(attributes, outside.attributes);
        }

        @Override
        public int hashCode() {
            return Objects.hash(attributes);
        }
    }

}
