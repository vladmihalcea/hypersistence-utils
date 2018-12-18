package com.vladmihalcea.hibernate.type.basic;

import com.vladmihalcea.hibernate.type.util.AbstractPostgreSQLIntegrationTest;
import org.hamcrest.CoreMatchers;
import org.hibernate.annotations.TypeDef;
import org.junit.Test;

import javax.persistence.*;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

/**
 * @author Edgar Asatryan
 */
public class PostgreSQLHstoreTypeTest extends AbstractPostgreSQLIntegrationTest {

    @Override
    protected Class<?>[] entities() {
        return new Class<?>[]{
                TestHstoreEntity.class
        };
    }

    @Override
    protected void afterInit() {
        doInJDBC(connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("CREATE EXTENSION IF NOT EXISTS hstore");
            } catch (SQLException e) {
                fail(e.getMessage());
            }
        });
    }

    @Test
    public void findById() {
        Map<String, String> attributes = new HashMap<>();
        attributes.put("a", "1");
        attributes.put("b", "2");
        attributes.put("c", "3");

        TestHstoreEntity entity = doInJPA(entityManager -> {
            entityManager.persist(new TestHstoreEntity());

            TestHstoreEntity e = new TestHstoreEntity();

            e.setAttributes(attributes);
            entityManager.persist(e);

            return e;
        });

        doInJPA(entityManager -> {
            TestHstoreEntity e = entityManager.find(TestHstoreEntity.class, entity.id);

            assertEquals(attributes, e.getAttributes());
        });
    }

    @Entity(name = "TestHstoreEntity")
    @Table(name = "test_hstore")
    @TypeDef(name = "hstore45", typeClass = PostgreSQLHstoreType.class, defaultForType = Map.class)
    public static class TestHstoreEntity {

        @Id
        @GeneratedValue
        private Long id;

        @Column(name = "attributes", columnDefinition = "hstore")
        private Map<String, String> attributes;

        Map<String, String> getAttributes() {
            return attributes;
        }

        void setAttributes(Map<String, String> attributes) {
            this.attributes = attributes;
        }
    }
}