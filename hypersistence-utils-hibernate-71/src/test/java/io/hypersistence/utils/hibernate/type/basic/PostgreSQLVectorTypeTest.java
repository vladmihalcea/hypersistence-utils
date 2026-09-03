package io.hypersistence.utils.hibernate.type.basic;

import io.hypersistence.utils.hibernate.util.AbstractPostgreSQLIntegrationTest;
import io.hypersistence.utils.hibernate.util.providers.PgVectorPostgreSQLDataSourceProvider;
import io.hypersistence.utils.test.providers.DataSourceProvider;
import jakarta.persistence.*;
import org.hibernate.Session;
import org.hibernate.annotations.Type;
import org.junit.Test;

import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static org.junit.Assert.assertArrayEquals;

/**
 * @author Chirag Gupta
 */
public class PostgreSQLVectorTypeTest extends AbstractPostgreSQLIntegrationTest {

    @Override
    protected Class<?>[] entities() {
        return new Class<?>[] {
            Item.class
        };
    }

    @Override
    protected DataSourceProvider dataSourceProvider() {
        return new PgVectorPostgreSQLDataSourceProvider();
    }

    @Override
    protected void beforeInit() {
        executeStatement("CREATE EXTENSION IF NOT EXISTS vector");
    }

    private Item _item;

    @Override
    public void afterInit() {
        _item = doInJPA(entityManager -> {
            entityManager.persist(new Item());

            Item item = new Item();
            item.setEmbedding(new float[] {1.0f, 2.0f, 3.0f});
            entityManager.persist(item);

            return item;
        });
    }

    @Test
    public void testFindById() {
        Item updatedItem = doInJPA(entityManager -> {
            Item item = entityManager.find(Item.class, _item.getId());

            assertArrayEquals(new float[] {1.0f, 2.0f, 3.0f}, item.getEmbedding(), 0.0f);

            item.setEmbedding(new float[] {4.0f, 5.0f, 6.0f});

            return item;
        });

        assertArrayEquals(new float[] {4.0f, 5.0f, 6.0f}, updatedItem.getEmbedding(), 0.0f);
    }

    @Test
    public void testJPQLQuery() {
        doInJPA(entityManager -> {
            Item item = entityManager.createQuery(
                "select i " +
                "from Item i " +
                "where " +
                "   embedding is not null", Item.class)
            .getSingleResult();

            assertArrayEquals(new float[] {1.0f, 2.0f, 3.0f}, item.getEmbedding(), 0.0f);
        });
    }

    @Test
    public void testNativeQuery() {
        doInJPA(entityManager -> {
            Item item = (Item) entityManager.createNativeQuery(
                "SELECT i.* " +
                "FROM item i " +
                "WHERE " +
                "   i.embedding = CAST(:embedding AS vector)", Item.class)
            .setParameter("embedding", "[1.0,2.0,3.0]")
            .getSingleResult();

            assertArrayEquals(new float[] {1.0f, 2.0f, 3.0f}, item.getEmbedding(), 0.0f);
        });
    }

    @Test
    public void testJDBCQuery() {
        doInJPA(entityManager -> {
            Session session = entityManager.unwrap(Session.class);
            session.doWork(connection -> {
                try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT * " +
                    "FROM item i " +
                    "WHERE " +
                    "   i.embedding = ?::vector"
                )) {
                    ps.setObject(1, "[1.0,2.0,3.0]");
                    ResultSet rs = ps.executeQuery();
                    while (rs.next()) {
                        String embedding = rs.getString(2);
                        assertArrayEquals(
                            new float[] {1.0f, 2.0f, 3.0f},
                            PostgreSQLVectorType.INSTANCE.fromStringValue(embedding),
                            0.0f
                        );
                    }
                }
            });
        });
    }

    @Entity(name = "Item")
    @Table(name = "item")
    public static class Item {

        @Id
        @GeneratedValue
        private Long id;

        @Type(PostgreSQLVectorType.class)
        @Column(name = "embedding", columnDefinition = "vector(3)")
        private float[] embedding;

        public Long getId() {
            return id;
        }

        public float[] getEmbedding() {
            return embedding;
        }

        public void setEmbedding(float[] embedding) {
            this.embedding = embedding;
        }
    }
}
