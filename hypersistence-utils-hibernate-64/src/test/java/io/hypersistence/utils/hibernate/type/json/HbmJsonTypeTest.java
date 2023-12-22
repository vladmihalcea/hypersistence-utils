package io.hypersistence.utils.hibernate.type.json;

import io.hypersistence.utils.hibernate.type.model.Location;
import io.hypersistence.utils.hibernate.util.AbstractPostgreSQLIntegrationTest;
import org.junit.Test;

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
        doInJPA(entityManager -> {
            Location location = new Location();
            location.setCountry("Romania");
            location.setCity("Cluj-Napoca");
            
            Event event = new Event();
            event.setId(1L);
            event.setLocation(location);
            entityManager.persist(event);
        });

        doInJPA(entityManager -> {
            Event event = entityManager.find(Event.class, 1L);
            assertEquals("Romania", event.getLocation().getCountry());
            assertEquals("Cluj-Napoca", event.getLocation().getCity());
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