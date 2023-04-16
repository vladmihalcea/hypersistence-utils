package io.hypersistence.utils.hibernate.type.json;

import io.hypersistence.utils.hibernate.type.model.Location;
import io.hypersistence.utils.hibernate.util.AbstractPostgreSQLIntegrationTest;
import io.hypersistence.utils.hibernate.util.transaction.JPATransactionFunction;
import org.junit.Test;

import javax.persistence.EntityManager;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;

/**
 * @author Vlad Mihalcea
 */
public class HbmJsonTypeTest extends AbstractPostgreSQLIntegrationTest {

    @Override
    protected Class<?>[] entities() {
        return new Class<?>[]{
        };
    }

    protected String[] resources() {
        return new String[]{
            "hbm/type/json/HbmJsonTypeTest.hbm.xml"
        };
    }

    @Test
    public void test() {
        doInJPA(new JPATransactionFunction<Void>() {

            @Override
            public Void apply(EntityManager entityManager) {
                Location location = new Location();
                location.setCountry("Romania");
                location.setCity("Cluj-Napoca");

                Event event = new Event();
                event.setId(1L);
                event.setLocation(location);
                entityManager.persist(event);

                return null;
            }
        });

        doInJPA(new JPATransactionFunction<Void>() {

            @Override
            public Void apply(EntityManager entityManager) {
                Event event = entityManager.find(Event.class, 1L);
                assertEquals("Romania", event.getLocation().getCountry());
                assertEquals("Cluj-Napoca", event.getLocation().getCity());

                return null;
            }
        });
    }
    
    public static class Event {
        
        private Long id;
        
        private Location location;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public Location getLocation() {
            return location;
        }

        public void setLocation(Location location) {
            this.location = location;
        }
    }
}