package com.vladmihalcea.hibernate.type.basic;

import com.vladmihalcea.hibernate.type.util.AbstractPostgreSQLIntegrationTest;
import com.vladmihalcea.hibernate.type.util.transaction.ConnectionVoidCallable;
import com.vladmihalcea.hibernate.type.util.transaction.JPATransactionFunction;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;
import org.junit.Test;

import javax.persistence.*;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.Assert.*;

/**
 * @author Andrei Akinchev
 */
public class NonBlankStringTypeTest extends AbstractPostgreSQLIntegrationTest {

    @Override
    protected Class<?>[] entities() {
        return new Class<?>[]{
                Book.class
        };
    }

    @Override
    public void init() {
        super.init();
        doInJDBC(new ConnectionVoidCallable() {
            @Override
            public void execute(Connection connection) throws SQLException {
                Statement statement = null;
                try {
                    statement = connection.createStatement();

                    statement.executeUpdate("INSERT INTO books (id, title, description) VALUES (1, 'a', 'a')");
                    statement.executeUpdate("INSERT INTO books (id, title, description) VALUES (2, 'b', NULL)");
                    statement.executeUpdate("INSERT INTO books (id, title, description) VALUES (3, 'c', '')");
                } catch (SQLException e) {
                    fail(e.getMessage());
                } finally {
                    if (statement != null) {
                        statement.close();
                    }
                }
            }
        });
    }

    @Test
    public void testSelect() {
        doInJPA(new JPATransactionFunction<Void>() {

            @Override
            public Void apply(EntityManager entityManager) {
                Book bookA = entityManager.find(Book.class, 1L);
                assertEquals(bookA.getDescription(), "a");

                Book bookB = entityManager.find(Book.class, 2L);
                assertNull(bookB.getDescription());

                Book bookC = entityManager.find(Book.class, 3L);
                assertNull(bookC.getDescription());
                return null;
            }
        });
    }

    @Test
    public void testInsert() {
        doInJPA(new JPATransactionFunction<Void>() {

            @Override
            public Void apply(EntityManager entityManager) {
                Book bookD = new Book();
                bookD.setId(4L);
                bookD.setTitle("d");
                bookD.setDescription("d");
                entityManager.persist(bookD);

                Book bookE = new Book();
                bookE.setId(5L);
                bookE.setTitle("e");
                bookE.setDescription(null);
                entityManager.persist(bookE);

                Book bookF = new Book();
                bookF.setId(6L);
                bookF.setTitle("b");
                bookF.setDescription("");
                entityManager.persist(bookF);

                return null;
            }
        });

        doInJDBC(new ConnectionVoidCallable() {
            @Override
            public void execute(Connection connection) throws SQLException {
                Statement statement = null;
                ResultSet resultSet = null;
                try {
                    statement = connection.createStatement();
                    resultSet = statement.executeQuery("SELECT description FROM books WHERE id IN (4, 5, 6) ORDER BY id");
                    resultSet.next();
                    assertEquals(resultSet.getString(1), "d");
                    resultSet.next();
                    assertNull(resultSet.getString(1));
                    resultSet.next();
                    assertNull(resultSet.getString(1));
                } catch (SQLException e) {
                    fail(e.getMessage());
                } finally {
                    if (resultSet != null) {
                        resultSet.close();
                    }
                    if (statement != null) {
                        statement.close();
                    }
                }
            }
        });
    }

    @Table(name = "books")
    @Entity(name = "Book")
    @TypeDef(name = "non_blank_string", typeClass = NonBlankStringType.class)
    public static class Book {

        @Id
        private Long id;

        private String title;

        @Type(type = "non_blank_string")
        @Column(columnDefinition = "varchar")
        private String description;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }
    }
}
