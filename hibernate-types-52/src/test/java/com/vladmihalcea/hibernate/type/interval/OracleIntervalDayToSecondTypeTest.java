package com.vladmihalcea.hibernate.type.interval;

import com.vladmihalcea.hibernate.type.model.BaseEntity;
import com.vladmihalcea.hibernate.type.util.AbstractOracleIntegrationTest;
import org.hibernate.annotations.TypeDef;
import org.junit.Test;

import javax.persistence.Column;
import javax.persistence.Entity;
import java.time.Duration;

import static org.junit.Assert.assertEquals;

/**
 * Tests for {@see OracleIntervalDayToSecondType} Hibernate type.
 *
 * @author Vlad Mihalcea
 */
public class OracleIntervalDayToSecondTypeTest extends AbstractOracleIntegrationTest {

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
            intervalEntity.setDuration(duration);

            entityManager.persist(intervalEntity);
        });

        doInJPA(entityManager -> {
            WorkShift result = entityManager.find(WorkShift.class, 1L);
            assertEquals(duration, result.getDuration());
        });
    }

    @Entity(name = "WorkShift")
    @TypeDef(typeClass = OracleIntervalDayToSecondType.class, defaultForType = Duration.class)
    public static class WorkShift extends BaseEntity {

        @Column(columnDefinition = "INTERVAL DAY TO SECOND")
        private Duration duration;

        public Duration getDuration() {
            return duration;
        }

        public void setDuration(Duration duration) {
            this.duration = duration;
        }
    }
}
