package com.vladmihalcea.hibernate.type.jsonp;

import com.vladmihalcea.hibernate.type.jsonp.internal.JsonbUtil;
import com.vladmihalcea.hibernate.type.model.BaseEntity;
import com.vladmihalcea.hibernate.type.model.Location;
import com.vladmihalcea.hibernate.type.model.Ticket;
import com.vladmihalcea.hibernate.type.util.AbstractMySQLIntegrationTest;
import com.vladmihalcea.hibernate.type.util.transaction.JPATransactionFunction;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.Type;
import org.junit.Test;

import javax.json.JsonValue;
import javax.persistence.*;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * @author Vlad Mihalcea
 * @author Jan-Willem Gmelig Meyling
 */
public class EhcacheMySQLJsonBinaryTypeTest extends AbstractMySQLIntegrationTest {

    @Override
    protected Class<?>[] entities() {
        return new Class<?>[]{
            Event.class,
        };
    }

    @Override
    protected String[] packages() {
        return new String[]{
            Event.class.getPackage().getName()
        };
    }

    protected void additionalProperties(Properties properties) {
        properties.setProperty("hibernate.cache.use_second_level_cache", "true");
        properties.setProperty("hibernate.cache.use_query_cache", "true");
        properties.setProperty("hibernate.cache.region.factory_class", "ehcache");
    }

    @Test
    public void test() {
        final AtomicReference<Event> eventHolder = new AtomicReference<>();

        doInJPA((JPATransactionFunction<Void>) entityManager -> {
            Event nullEvent = new Event();
            nullEvent.setId(0L);
            entityManager.persist(nullEvent);

            Location location = new Location();
            location.setCountry("Romania");
            location.setCity("Cluj-Napoca");

            Event event = new Event();
            event.setId(1L);

            event.setProperties(
                JsonbUtil.toJsonNode(
                    "{" +
                    "   \"title\": \"High-Performance Java Persistence\"," +
                    "   \"author\": \"Vlad Mihalcea\"," +
                    "   \"publisher\": \"Amazon\"," +
                    "   \"price\": 44.99" +
                    "}"
                )
            );
            entityManager.persist(event);

            Ticket ticket = new Ticket();
            ticket.setPrice(12.34d);
            ticket.setRegistrationCode("ABC123");

            eventHolder.set(event);

            return null;
        });
        doInJPA((JPATransactionFunction<Void>) entityManager -> {
            Event event = entityManager.find(Event.class, eventHolder.get().getId());
            assertNotNull(event.getProperties());

            List<String> properties = entityManager.createNativeQuery(
                "select CAST(e.properties AS CHAR(1000)) " +
                "from event e " +
                "where JSON_EXTRACT(e.properties, \"$.price\") > 1 ")
            .getResultList();

            assertEquals(1, properties.size());
            JsonValue jsonNode = JsonbUtil.toJsonNode(properties.get(0));
            assertEquals("High-Performance Java Persistence", jsonNode.asJsonObject().get("title").toString());

            return null;
        });
    }

    @Entity(name = "Event")
    @Table(name = "event")
    @Cacheable(true)
    @org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
    public static class Event extends BaseEntity {

        @Type(type = "json-p-value")
        @Column(columnDefinition = "json")
        private JsonValue properties;

        public JsonValue getProperties() {
            return properties;
        }

        public void setProperties(JsonValue properties) {
            this.properties = properties;
        }
    }
}
