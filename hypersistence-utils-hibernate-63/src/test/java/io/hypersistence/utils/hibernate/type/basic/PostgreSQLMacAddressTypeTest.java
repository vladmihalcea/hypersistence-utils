package io.hypersistence.utils.hibernate.type.basic;

import io.hypersistence.utils.hibernate.util.AbstractPostgreSQLIntegrationTest;
import jakarta.persistence.*;
import org.hibernate.Session;
import org.hibernate.annotations.Type;
import org.junit.Test;

import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static org.junit.Assert.assertEquals;

public class PostgreSQLMacAddressTypeTest extends AbstractPostgreSQLIntegrationTest {

    @Override
    protected Class<?>[] entities() {
        return new Class<?>[] {
            Event.class
        };
    }

    private Event _event;

    @Override
    public void afterInit() {
        _event = doInJPA(entityManager -> {
            entityManager.persist(new Event());

            Event event = new Event();
            event.setMac("08:00:2b:01:02:03");
            entityManager.persist(event);

            return event;
        });
    }

    @Test
    public void testFindById() {
        Event updatedEvent = doInJPA(entityManager -> {
            Event event = entityManager.find(Event.class, _event.getId());

            assertEquals("08:00:2b:01:02:03", event.getMac().getAddress());

            event.setMac("08:00:2b:01:02:04");

            return event;
        });

        assertEquals("08:00:2b:01:02:04", updatedEvent.getMac().getAddress());
    }

    @Test
    public void testJPQLQuery() {
        doInJPA(entityManager -> {
            Event event = entityManager.createQuery(
                "select e " +
                "from Event e " +
                "where " +
                "   mac is not null", Event.class)
            .getSingleResult();

            assertEquals("08:00:2b:01:02:03", event.getMac().getAddress());
        });
    }

    @Test
    public void testNativeQuery() {
        doInJPA(entityManager -> {
            Event event = (Event) entityManager.createNativeQuery(
                "SELECT e.* " +
                "FROM event e " +
                "WHERE " +
                "   e.mac = CAST(:mac AS macaddr)", Event.class)
            .setParameter("mac", "08:00:2b:01:02:03")
            .getSingleResult();

            assertEquals("08:00:2b:01:02:03", event.getMac().getAddress());
        });
    }

    @Test
    public void testJDBCQuery() {
        doInJPA(entityManager -> {
            Session session = entityManager.unwrap(Session.class);
            session.doWork(connection -> {
                try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT * " +
                    "FROM event e " +
                    "WHERE " +
                    "   e.mac = ?::macaddr"
                )) {
                    ps.setObject(1, "08:00:2b:01:02:03");
                    ResultSet rs = ps.executeQuery();
                    while (rs.next()) {
                        String mac = rs.getString(2);
                        assertEquals("08:00:2b:01:02:03", mac);
                    }
                }
            });
        });
    }

    @Entity(name = "Event")
    @Table(name = "event")
    public static class Event {

        @Id
        @GeneratedValue
        private Long id;

        @Type(PostgreSQLMacAddressType.class)
        @Column(name = "mac", columnDefinition = "macaddr")
        private MacAddress mac;

        public Long getId() {
            return id;
        }

        public MacAddress getMac() {
            return mac;
        }

        public void setMac(String address) {
            this.mac = new MacAddress(address);
        }
    }
}
