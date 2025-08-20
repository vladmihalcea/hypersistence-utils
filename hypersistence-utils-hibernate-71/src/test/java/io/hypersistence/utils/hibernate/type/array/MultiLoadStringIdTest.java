package io.hypersistence.utils.hibernate.type.array;

import io.hypersistence.utils.hibernate.util.AbstractPostgreSQLIntegrationTest;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.Session;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * @author Vlad Mihalcea
 */
public class MultiLoadStringIdTest extends AbstractPostgreSQLIntegrationTest {

    @Override
    protected Class<?>[] entities() {
        return new Class<?>[]{
            Event.class,
        };
    }

    @Test
    public void test() {
        doInJPA(entityManager -> {
            Event event1 = new Event();
            event1.setId("1");
            entityManager.persist(event1);

            Event event2 = new Event();
            event2.setId("2");

            entityManager.persist(event2);
        });

        doInJPA(entityManager -> {
            List<Event> eventsByIds = entityManager.unwrap(Session.class)
                .byMultipleIds(Event.class)
                .multiLoad(List.of("1", "2"));

            assertEquals(2, eventsByIds.size());
        });
    }

    @Entity(name = "Event")
    @Table(name = "event")
    public static class Event {

        @Id
        private String id;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }
    }
}