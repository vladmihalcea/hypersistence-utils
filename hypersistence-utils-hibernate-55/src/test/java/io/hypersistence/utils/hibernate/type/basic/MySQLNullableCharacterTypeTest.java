package io.hypersistence.utils.hibernate.type.basic;

import io.hypersistence.utils.hibernate.util.AbstractTest;
import org.hibernate.annotations.Type;
import org.junit.Test;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Table;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * @author Wim Wintmolders
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
                assertEquals(event.getType().toString(), expectedValue(event.getId()));
            }
        });
    }

    private String expectedValue(Long id) {
        Map<Long, String> expectedValues = new HashMap<>();
        expectedValues.put(1L, "a");
        expectedValues.put(2L, " ");
        expectedValues.put(3L, "b");
        expectedValues.put(4L, "\\");
        return expectedValues.get(id);
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
