package io.hypersistence.utils.hibernate.type.basic;

import io.hypersistence.utils.hibernate.util.AbstractTest;
import org.hibernate.annotations.Type;
import org.junit.Test;

import jakarta.persistence.*;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.fail;

/**
 * @author Vlad Mihalcea
 */
public class NullableCharacterTypeTest extends AbstractTest {

    @Override
    protected Class<?>[] entities() {
        return new Class<?>[]{
                Event.class
        };
    }

    @Override
    protected void afterInit() {
        executeStatement("INSERT INTO EVENT (ID, EVENT_TYPE) VALUES (1, 'abc')");
        executeStatement("INSERT INTO EVENT (ID, EVENT_TYPE) VALUES (2, '')");
        executeStatement("INSERT INTO EVENT (ID, EVENT_TYPE) VALUES (3, 'b')");
    }

    @Test
    public void test() {
        final AtomicReference<Event> eventHolder = new AtomicReference<>();
        doInJPA(entityManager -> {
            List<Event> events = entityManager.createQuery("select e from Event e", Event.class).getResultList();
            for (Event event : events) {
                LOGGER.info("Event type: {}", event.getType());
            }
        });
    }

    @Entity(name = "Event")
    @Table(name = "event")
    public static class Event {

        @Id
        @GeneratedValue
        private Long id;

        @Type(NullableCharacterType.class)
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
