package io.hypersistence.utils.hibernate.type.basic;

import io.hypersistence.utils.hibernate.util.AbstractTest;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Type;
import org.junit.Test;

import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * @author Wim Wintmolders
 */
public class MySQLNullableCharacterTypeTest extends AbstractTest {
    private static final Map<Long, String> EXPECTED_VALUES = Map.of(1L, "a", 2L, " ", 3L, "b", 4L, "\\");

    @Override
    protected Class<?>[] entities() {
        return new Class<?>[]{
                Event.class
        };
    }

    @Override
    public void init() {
        super.init();
        doInJDBC(connection -> {
            try (
                    Statement statement = connection.createStatement();
            ) {
                statement.executeUpdate("INSERT INTO EVENT (ID, EVENT_TYPE) VALUES (1, 'abc')");
                statement.executeUpdate("INSERT INTO EVENT (ID, EVENT_TYPE) VALUES (2, '')");
                statement.executeUpdate("INSERT INTO EVENT (ID, EVENT_TYPE) VALUES (3, 'b')");
            } catch (SQLException e) {
                fail(e.getMessage());
            }
        });
    }

    @Test
    public void test() {
        final AtomicReference<Event> eventHolder = new AtomicReference<>();
        doInJPA(entityManager -> {
            List<Event> events = entityManager.createQuery("select e from Event e", Event.class).getResultList();
            for (Event event : events) {
                LOGGER.info("Event type: {}", event.getType());
                assertEquals(event.getType().toString(), EXPECTED_VALUES.get(event.getId()));
            }
        });
    }

    @Entity(name = "Event")
    @Table(name = "event")
    public static class Event {

        @Id
        @GeneratedValue
        private Long id;

        @Type(MySQLNullableCharacterType.class)
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
