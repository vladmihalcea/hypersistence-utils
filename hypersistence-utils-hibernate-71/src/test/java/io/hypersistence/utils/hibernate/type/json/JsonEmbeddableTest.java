package io.hypersistence.utils.hibernate.type.json;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.hibernate.MappingException;
import org.hibernate.SessionFactory;
import org.hibernate.annotations.Type;
import org.hibernate.cfg.Configuration;
import org.hibernate.dialect.H2Dialect;
import org.junit.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class JsonEmbeddableTest {

    @Test
    public void testEmbeddableList() {
        assertEmbeddableMappingRejected(Grid.class);
    }

    @Test
    public void testEmbeddableValue() {
        bootstrap(SingleEvent.class);
    }

    @Test
    public void testEmbeddableMapValue() {
        assertEmbeddableMappingRejected(EventMap.class);
    }

    @Test
    public void testEmbeddableMapKey() {
        bootstrap(EventKeyMap.class);
    }

    @Test
    public void testEmbeddableArray() {
        assertEmbeddableMappingRejected(EventArray.class);
    }

    @Test
    public void testEmbeddableSet() {
        assertEmbeddableMappingRejected(EventSet.class);
    }

    @Test
    public void testEmbeddableGetter() {
        assertEmbeddableMappingRejected(EventGetter.class);
    }

    private void assertEmbeddableMappingRejected(Class<?> entityClass) {
        MappingException exception = assertThrows(MappingException.class, () -> bootstrap(entityClass));
        assertEquals(
            "The JSON property '" + entityClass.getName() + ".gameEvents" +
            "' uses the @Embeddable type '" + GameEvent.class.getName() + "' as a collection element, array element, or map value. " +
            "Use a POJO without @Embeddable for this JSON mapping, or use @ElementCollection instead of a JSON type.",
            exception.getMessage()
        );
    }

    private void bootstrap(Class<?> entityClass) {
        try (SessionFactory sessionFactory = new Configuration()
            .addAnnotatedClass(entityClass)
            .setProperty("hibernate.dialect", H2Dialect.class.getName())
            .setProperty("hibernate.boot.allow_jdbc_metadata_access", "false")
            .buildSessionFactory()) {
            assertTrue(sessionFactory.isOpen());
        }
    }

    @Entity(name = "Grid")
    public static class Grid {

        @Id
        private Long id;

        @Type(JsonType.class)
        @Column(columnDefinition = "json")
        private List<GameEvent> gameEvents;
    }

    @Entity(name = "SingleEvent")
    public static class SingleEvent {
        @Id
        private Long id;

        @Type(JsonType.class)
        @Column(columnDefinition = "json")
        private GameEvent gameEvent;
    }

    @Entity(name = "EventMap")
    public static class EventMap {
        @Id
        private Long id;

        @Type(JsonBinaryType.class)
        @Column(columnDefinition = "json")
        private Map<String, GameEvent> gameEvents;
    }

    @Entity(name = "EventKeyMap")
    public static class EventKeyMap {
        @Id
        private Long id;

        @Type(JsonType.class)
        @Column(columnDefinition = "json")
        private Map<GameEvent, String> gameEvents;
    }

    @Entity(name = "EventArray")
    public static class EventArray {
        @Id
        private Long id;

        @Type(JsonBlobType.class)
        @Column(columnDefinition = "json")
        private GameEvent[] gameEvents;
    }

    @Entity(name = "EventSet")
    public static class EventSet {
        @Id
        private Long id;

        @Type(JsonStringType.class)
        @Column(columnDefinition = "json")
        private Set<GameEvent> gameEvents;
    }

    @Entity(name = "EventGetter")
    public static class EventGetter {

        private Long id;
        private List<GameEvent> gameEvents;

        @Id
        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        @Type(JsonClobType.class)
        @Column(columnDefinition = "json")
        public List<GameEvent> getGameEvents() {
            return gameEvents;
        }

        public void setGameEvents(List<GameEvent> gameEvents) {
            this.gameEvents = gameEvents;
        }
    }

    @Embeddable
    public static class GameEvent {

        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}
