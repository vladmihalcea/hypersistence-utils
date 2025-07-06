package io.hypersistence.utils.hibernate.id;

import io.hypersistence.utils.hibernate.util.AbstractTest;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.junit.Test;

import static org.junit.Assert.*;

public class TsidValueGeneratorTest extends AbstractTest {
    @Override
    protected Class<?>[] entities() {
        return new Class<?>[]{
            Log.class
        };
    }

    @Test
    public void testTimestampGeneratedOnFlush() {
        doInJPA(entityManager -> {
            Log log = new Log();
            entityManager.persist(log);
            assertNull("timestamp should still be null after persist", log.getTimestamp());
            entityManager.flush();
            assertNotNull("timestamp should be generated after flush", log.getTimestamp());
            entityManager.clear();

            Log persisted = entityManager.find(Log.class, log.getId());
            assertNotNull("timestamp should be persisted and retrievable", persisted.getTimestamp());
            assertEquals("timestamp in memory and DB should be equal", log.getTimestamp(), persisted.getTimestamp());
        });
    }


    @Test
    public void testIdAndTimestampGeneration() {
        doInJPA(entityManager -> {
            Log log = new Log();
            assertNull(log.getTimestamp());
            entityManager.persist(log);
            entityManager.flush();
            entityManager.clear();

            Log persisted = entityManager.find(Log.class, log.getId());

            assertNotNull("id should not be null", persisted.getId());
            assertNotNull("timestamp should not be null", persisted.getTimestamp());
            assertNotEquals(Long.valueOf(0), persisted.getId());
            assertFalse(persisted.getTimestamp().isEmpty());
        });
    }

    @Test
    public void testTimestampIsGeneratedOnInsertOnly() {
        doInJPA(entityManager -> {
            Log log = new Log();
            entityManager.persist(log);
            entityManager.flush();
            entityManager.clear();

            Log persisted = entityManager.find(Log.class, log.getId());
            assertNotNull("timestamp should be generated on INSERT", persisted.getTimestamp());
        });
    }

    @Test
    public void testTimestampNotRegeneratedOnUpdate() {
        doInJPA(entityManager -> {
            Log log = new Log();
            entityManager.persist(log);
            entityManager.flush();
            entityManager.clear();

            Log persisted = entityManager.find(Log.class, log.getId());

            String newPayload = "update log";
            persisted.setPayload(newPayload);
            entityManager.merge(persisted);
            entityManager.flush();
            entityManager.clear();

            Log reloaded = entityManager.find(Log.class, log.getId());
            assertEquals("payload should be updated", newPayload, reloaded.getPayload());
            assertEquals("timestamp should not be regenerated on update", persisted.getTimestamp(), reloaded.getTimestamp());
        });
    }

    @Entity(name = "Log")
    @Table(name = "log")
    public static class Log {
        @Id
        @Tsid
        private Long id;

        @Tsid
        private String timestamp;

        private String payload;

        public Long getId() {
            return id;
        }

        public Log setId(Long id) {
            this.id = id;
            return this;
        }

        public String getTimestamp() {
            return timestamp;
        }

        public Log setTimestamp(String timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public String getPayload() {
            return payload;
        }

        public Log setPayload(String payload) {
            this.payload = payload;
            return this;
        }
    }
}
