package com.vladmihalcea.hibernate.type.basic;

import com.vladmihalcea.hibernate.util.AbstractPostgreSQLIntegrationTest;
import com.vladmihalcea.hibernate.util.transaction.ConnectionVoidCallable;
import com.vladmihalcea.hibernate.util.transaction.JPATransactionFunction;
import org.hibernate.Session;
import org.hibernate.annotations.TypeDef;
import org.hibernate.jdbc.Work;
import org.junit.Test;

import javax.persistence.*;
import java.sql.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * @author Vlad Mihalcea
 */
public class PostgreSQLInetTypeTest extends AbstractPostgreSQLIntegrationTest {

    @Override
    protected Class<?>[] entities() {
        return new Class<?>[] {
            Event.class
        };
    }

    private Event _event;

    @Override
    public void afterInit() {
        doInJDBC(new ConnectionVoidCallable() {
            @Override
            public void execute(Connection connection) throws SQLException {
                Statement statement = null;

                try  {
                    statement = connection.createStatement();
                    statement.executeUpdate("CREATE INDEX ON event USING gist (ip inet_ops)");
                } catch (SQLException e) {
                    fail(e.getMessage());
                } finally {
                    if (statement != null) {
                        statement.close();
                    }
                }
            }
        });

        _event = doInJPA(new JPATransactionFunction<Event>() {

            @Override
            public Event apply(EntityManager entityManager) {
                entityManager.persist(new Event());

                Event event = new Event();
                event.setIp("192.168.0.123/24");
                entityManager.persist(event);

                return event;
            }
        });
    }

    @Test
    public void testFindById() {
        Event updatedEvent = doInJPA(new JPATransactionFunction<Event>() {

            @Override
            public Event apply(EntityManager entityManager) {
                Event event = entityManager.find(Event.class, _event.getId());

                assertEquals("192.168.0.123/24", event.getIp().getAddress());
                assertEquals("192.168.0.123", event.getIp().toInetAddress().getHostAddress());

                event.setIp("192.168.0.231/24");

                return event;
            }
        });

        assertEquals("192.168.0.231/24", updatedEvent.getIp().getAddress());
    }

    @Test
    public void testJPQLQuery() {
        doInJPA(new JPATransactionFunction<Void>() {

            @Override
            public Void apply(EntityManager entityManager) {
                Event event = entityManager.createQuery(
                    "select e " +
                    "from Event e " +
                    "where " +
                    "   ip is not null", Event.class)
                .getSingleResult();

                assertEquals("192.168.0.123/24", event.getIp().getAddress());

                return null;
            }
        });
    }

    @Test
    public void testNativeQuery() {
        doInJPA(new JPATransactionFunction<Void>() {

            @Override
            public Void apply(EntityManager entityManager) {
                Event event = (Event) entityManager.createNativeQuery(
                    "SELECT e.* " +
                    "FROM event e " +
                    "WHERE " +
                    "   e.ip && CAST(:network AS inet) = true", Event.class)
                .setParameter("network", "192.168.0.1/24")
                .getSingleResult();

                assertEquals("192.168.0.123/24", event.getIp().getAddress());

                return null;
            }
        });
    }


    @Test
    public void testJDBCQuery() {
        doInJPA(new JPATransactionFunction<Void>() {

            @Override
            public Void apply(EntityManager entityManager) {
                Session session = entityManager.unwrap(Session.class);
                session.doWork(new Work() {
                    @Override
                    public void execute(Connection connection) throws SQLException {
                        PreparedStatement ps = null;
                        try {

                            ps = connection.prepareStatement(
                                "SELECT * " +
                                "FROM Event e " +
                                "WHERE " +
                                "   e.ip && ?::inet = true"
                            );

                            ps.setObject(1, "192.168.0.1/24");
                            ResultSet rs = ps.executeQuery();
                            while(rs.next()) {
                                Long id = rs.getLong(1);
                                String ip = rs.getString(2);
                                assertEquals("192.168.0.123/24", ip);
                            }
                        } finally {
                            if (ps != null) {
                                ps.close();
                            }
                        }
                    }
                });

                return null;
            }
        });
    }

    @Entity(name = "Event")
    @Table(name = "event")
    @TypeDef(name = "ipv4", typeClass = PostgreSQLInetType.class, defaultForType = Inet.class)
    public static class Event {

        @Id
        @GeneratedValue
        private Long id;

        @Column(name = "ip", columnDefinition = "inet")
        private Inet ip;

        public Long getId() {
            return id;
        }

        public Inet getIp() {
            return ip;
        }

        public void setIp(String address) {
            this.ip = new Inet(address);
        }
    }
}
