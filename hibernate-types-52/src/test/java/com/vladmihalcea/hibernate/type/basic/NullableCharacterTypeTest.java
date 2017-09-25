package com.vladmihalcea.hibernate.type.basic;

import com.vladmihalcea.hibernate.type.util.AbstractTest;
import org.hibernate.annotations.Type;
import org.junit.Test;

import javax.persistence.*;
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
            }
        });
    }

    @Entity(name = "Event")
    @Table(name = "event")
    public static class Event {

        @Id
        @GeneratedValue
        private Long id;

        @Type(type = "com.vladmihalcea.hibernate.type.basic.NullableCharacterType")
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
