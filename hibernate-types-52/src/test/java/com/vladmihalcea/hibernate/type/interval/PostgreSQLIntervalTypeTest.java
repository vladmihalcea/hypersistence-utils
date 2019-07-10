package com.vladmihalcea.hibernate.type.interval;

import com.vladmihalcea.hibernate.type.model.BaseEntity;
import com.vladmihalcea.hibernate.type.util.AbstractPostgreSQLIntegrationTest;
import org.hibernate.annotations.TypeDef;
import org.junit.Assert;
import org.junit.Test;

import javax.persistence.Column;
import javax.persistence.Entity;
import java.time.Duration;

import static org.junit.Assert.assertEquals;

/**
 * Tests for {@see PostgreSQLIntervalType} Hibernate type.
 *
 * @author Jan-Willem Gmelig Meyling
 */
public class PostgreSQLIntervalTypeTest extends AbstractPostgreSQLIntegrationTest {

    @Override
    protected Class<?>[] entities() {
        return new Class[]{WorkShift.class};
    }

    @Test
    public void test() {
        Duration duration = Duration.ofDays(1).plusHours(2).plusMinutes(3).plusSeconds(4);

        doInJPA(entityManager -> {
            WorkShift intervalEntity = new WorkShift();
            intervalEntity.setId(1L);
            intervalEntity.setInterval(duration);

            entityManager.persist(intervalEntity);
        });

        doInJPA(entityManager -> {
            WorkShift result = entityManager.find(WorkShift.class, 1L);
            assertEquals(duration, result.getInterval());
        });
    }

    @Entity(name = "WorkShift")
    @TypeDef(typeClass = PostgreSQLIntervalType.class, defaultForType = Duration.class)
    public static class WorkShift extends BaseEntity {

        @Column(columnDefinition = "interval")
        private Duration interval;

        public Duration getInterval() {
            return interval;
        }

        public void setInterval(Duration interval) {
            this.interval = interval;
        }
    }
}
