package io.hypersistence.utils.hibernate.type.interval;

import io.hypersistence.utils.hibernate.type.array.EnumArrayType;
import io.hypersistence.utils.hibernate.type.array.ListArrayTypeTest;
import io.hypersistence.utils.hibernate.type.model.BaseEntity;
import io.hypersistence.utils.hibernate.util.AbstractPostgreSQLIntegrationTest;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import org.hibernate.annotations.Type;
import org.hibernate.jpa.boot.spi.TypeContributorList;
import org.hibernate.query.NativeQuery;
import org.junit.Test;

import java.time.Period;
import java.util.Collections;
import java.util.Properties;

import static org.junit.Assert.assertEquals;

/**
 * Tests for {@see PostgreSQLIntervalType} Hibernate type.
 *
 * @author Jan-Willem Gmelig Meyling
 */
public class PostgreSQLPeriodTypeTest extends AbstractPostgreSQLIntegrationTest {

    @Override
    protected Class<?>[] entities() {
        return new Class[]{WorkShift.class};
    }

    @Override
    protected void additionalProperties(Properties properties) {
        properties.put("hibernate.type_contributors",
            (TypeContributorList) () -> Collections.singletonList(
                (typeContributions, serviceRegistry) -> {
                    typeContributions.contributeType(PostgreSQLPeriodType.INSTANCE);
                }
            ));
    }

    @Test
    public void test() {
        Period duration = Period.of(1, 2, 3);

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

        doInJPA(entityManager -> {
            Object result = entityManager.createNativeQuery(
                "select duration " +
                "from WorkShift " +
                "where id = :id")
            .setParameter("id", 1L)
            .unwrap(NativeQuery.class)
            .addScalar("duration", Period.class)
            .getSingleResult();
            assertEquals(duration, result);
        });
    }

    @Entity(name = "WorkShift")
    public static class WorkShift extends BaseEntity {

        @Type(PostgreSQLPeriodType.class)
        @Column(columnDefinition = "interval")
        private Period duration;

        public Period getDuration() {
            return duration;
        }

        public void setDuration(Period duration) {
            this.duration = duration;
        }
    }
}
