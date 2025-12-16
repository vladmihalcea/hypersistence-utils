package io.hypersistence.utils.hibernate.type.array;

import io.hypersistence.utils.common.ReflectionUtils;
import io.hypersistence.utils.hibernate.util.AbstractPostgreSQLIntegrationTest;
import jakarta.persistence.*;
import org.hibernate.annotations.Type;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.jpa.boot.spi.TypeContributorList;
import org.hibernate.metamodel.model.domain.BasicDomainType;
import org.hibernate.type.CustomType;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import static org.junit.Assert.assertEquals;

/**
 * @author Vlad Mihalcea
 */
public class MultiDimensionalIntegerArrayTypeTest extends AbstractPostgreSQLIntegrationTest {

    @Override
    protected Class<?>[] entities() {
        return new Class<?>[] {
            Plane.class,
        };
    }

    @Override
    protected void additionalProperties(Properties properties) {
        properties.put("hibernate.type_contributors",
            (TypeContributorList) () -> Collections.singletonList(
                (typeContributions, serviceRegistry) -> {
                    typeContributions.contributeType(
                        new EnumArrayType(
                            ReflectionUtils.getField(MultiDimensionalArrayTypeTest.Plane.class, "seatGrid").getType(),
                            "seat_status"
                        )
                    );
                }
            ));
    }

    @Test
    public void test() {
        doInJPA(entityManager -> {
            entityManager.persist(
                new Plane()
                    .setId(1L)
                    .setName("ATR-42")
                    .setSeatGrid(
                        new Integer[][] {
                            {1, 1, 1, 1},
                            {0, 0, 2, 0},
                            {2, 2, 2, 2},
                            {2, 2, 2, 2},
                            {2, 2, 2, 2},
                            {2, 2, 2, 2},
                            {2, 2, 2, 2},
                            {2, 2, 2, 2},
                            {2, 2, 2, 2},
                            {2, 2, 2, 2},
                            {2, 2, 2, 2},
                            {1, 1, 1, 1}
                        }
                    )
            );
        });

        doInJPA(entityManager -> {
            Plane plane = entityManager.find(Plane.class, 1L);

            assertEquals("ATR-42", plane.getName());
            assertEquals(1, plane.getSeatStatus(1, 'A'));
            assertEquals(1, plane.getSeatStatus(1, 'B'));
            assertEquals(1, plane.getSeatStatus(1, 'C'));
            assertEquals(1, plane.getSeatStatus(1, 'D'));
            assertEquals(0, plane.getSeatStatus(2, 'A'));
            assertEquals(0, plane.getSeatStatus(2, 'B'));
            assertEquals(2, plane.getSeatStatus(2, 'C'));
            assertEquals(0, plane.getSeatStatus(2, 'D'));
        });

        doInJPA(entityManager -> {
            List<Tuple> tuples = entityManager.createNativeQuery("""
                SELECT
                   id,
                   name,
                   seat_grid
                FROM plane
                WHERE id >= :id
                """, Tuple.class)
                .setParameter("id", 0)
                .unwrap(org.hibernate.query.NativeQuery.class)
                .addScalar("id")
                .addScalar("name")
                .addScalar(
                    "seat_grid",
                    (BasicDomainType) Arrays.stream(entityManager.getEntityManagerFactory().unwrap(SessionFactoryImplementor.class).getMappingMetamodel().getEntityDescriptor(Plane.class).getPropertyTypes()).filter(t -> t instanceof CustomType<?>).findFirst().orElse(null)
                )
                .getResultList();

            Tuple plane = tuples.get(0);
            assertEquals("ATR-42", plane.get("name"));
        });
    }

    @Entity(name = "Plane")
    @Table(name = "plane")
    public static class Plane {

        @Id
        private Long id;

        private String name;

        @Type(MultiDimensionalArrayType.class)
        @Column(name = "seat_grid", columnDefinition = "int[][]")
        private Integer[][] seatGrid;

        public Long getId() {
            return id;
        }

        public Plane setId(Long id) {
            this.id = id;
            return this;
        }

        public String getName() {
            return name;
        }

        public Plane setName(String name) {
            this.name = name;
            return this;
        }

        public Integer[][] getSeatGrid() {
            return seatGrid;
        }

        public Plane setSeatGrid(Integer[][] seatGrid) {
            this.seatGrid = seatGrid;
            return this;
        }

        public int getSeatStatus(int row, char letter) {
            return seatGrid[row - 1][letter - 65];
        }
    }

}