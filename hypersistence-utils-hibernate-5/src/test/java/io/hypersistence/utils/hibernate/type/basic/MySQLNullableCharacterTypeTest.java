package io.hypersistence.utils.hibernate.type.basic;

import io.hypersistence.utils.hibernate.util.AbstractTest;
import io.hypersistence.utils.hibernate.util.transaction.ConnectionVoidCallable;
import io.hypersistence.utils.hibernate.util.transaction.JPATransactionFunction;
import org.hibernate.annotations.Type;
import org.junit.Test;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EntityManager;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Table;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * @author Vlad Mihalcea
 */
public class MySQLNullableCharacterTypeTest extends AbstractTest {

    @Override
    protected Class<?>[] entities() {
        return new Class<?>[]{
                Event.class
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

                    statement.executeUpdate("INSERT INTO EVENT (ID, EVENT_TYPE) VALUES (1, 'abc')");
                    statement.executeUpdate("INSERT INTO EVENT (ID, EVENT_TYPE) VALUES (2, '')");
                    statement.executeUpdate("INSERT INTO EVENT (ID, EVENT_TYPE) VALUES (3, 'b')");
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
    public void test() {
        Map<Long, String> TEST_VALUES = new HashMap<>();
        TEST_VALUES.put(1L, "a");
        TEST_VALUES.put(2L, " ");
        TEST_VALUES.put(3L, "b");
        TEST_VALUES.put(4L, "\\");

        doInJPA(new JPATransactionFunction<Void>() {

            @Override
            public Void apply(EntityManager entityManager) {
                List<Event> events = entityManager.createQuery("select e from Event e", Event.class).getResultList();
                for (Event event : events) {
                    LOGGER.info("Event type: {}", event.getType());
                    assertEquals(event.getType().toString(), TEST_VALUES.get(event.getId()));
                }
                return null;
            }
        });
    }

    @Entity(name = "Event")
    @Table(name = "event")
    public static class Event {

        @Id
        @GeneratedValue
        private Long id;

        @Type(type = "io.hypersistence.utils.hibernate.type.basic.MySQLNullableCharacterType")
        @Column(name = "event_type")
        private Character type;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public Character getType() {
            return type;
        }

        public void setType(Character type) {
            this.type = type;
        }
    }
}
