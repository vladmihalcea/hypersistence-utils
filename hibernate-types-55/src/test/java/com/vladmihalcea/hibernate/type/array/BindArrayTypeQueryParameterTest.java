package com.vladmihalcea.hibernate.type.array;

import com.vladmihalcea.hibernate.type.util.AbstractPostgreSQLIntegrationTest;
import com.vladmihalcea.hibernate.type.util.ExceptionUtil;
import com.vladmihalcea.hibernate.type.util.providers.DataSourceProvider;
import com.vladmihalcea.hibernate.type.util.providers.PostgreSQLDataSourceProvider;
import org.hibernate.annotations.TypeDef;
import org.hibernate.jpa.TypedParameterValue;
import org.hibernate.query.Query;
import org.junit.Test;

import javax.persistence.*;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.ParameterExpression;
import javax.persistence.criteria.Root;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.junit.Assert.*;

/**
 * @author Stanislav Gubanov
 */
public class BindArrayTypeQueryParameterTest extends AbstractPostgreSQLIntegrationTest {

    @Override
    protected Class<?>[] entities() {
        return new Class<?>[]{
                Event.class,
        };
    }

    @Override
    public void init() {
        DataSource dataSource = newDataSource();
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate(
                "CREATE OR REPLACE FUNCTION " +
                "    fn_array_contains(" +
                "       left_array integer[], " +
                "       right_array integer[]" +
                ") RETURNS " +
                "       boolean AS " +
                "$$ " +
                "BEGIN " +
                "  return left_array @> right_array; " +
                "END; " +
                "$$ LANGUAGE 'plpgsql';"
            );
        } catch (SQLException e) {
            fail(e.getMessage());
        }
        super.init();
    }

    @Override
    protected DataSourceProvider dataSourceProvider() {
        return new PostgreSQLDataSourceProvider() {
            @Override
            public String hibernateDialect() {
                return PostgreSQL95ArrayDialect.class.getName();
            }
        };
    }

    @Override
    protected void afterInit() {
        doInJPA(entityManager -> {
            Event event = new Event();
            event.setId(1L);
            event.setName("Temperature");
            event.setValues(new int[]{1, 2, 3});
            entityManager.persist(event);
        });
    }

    @Test
    public void testJPQLWithDefaultParameterBiding() {
        try {
            doInJPA(entityManager -> {
                Event event = entityManager
                .createQuery(
                    "select e " +
                    "from Event e " +
                    "where " +
                    "   fn_array_contains(e.values, :arrayValues) = true", Event.class)
                .setParameter("arrayValues", new int[]{2, 3})
                .getSingleResult();
            });
        } catch (Exception e) {
            Exception rootCause = ExceptionUtil.rootCause(e);
            assertTrue(rootCause.getMessage().contains("ERROR: function fn_array_contains(integer[], bytea) does not exist"));
        }
    }

    @Test
    public void testJPQLWithExplicitParameterTypeBinding() {
        doInJPA(entityManager -> {
            Event event = (Event) entityManager
            .createQuery(
                "select e " +
                "from Event e " +
                "where " +
                "   fn_array_contains(e.values, :arrayValues) = true", Event.class)
            .unwrap(org.hibernate.query.Query.class)
            .setParameter("arrayValues", new int[]{2, 3}, IntArrayType.INSTANCE)
            .getSingleResult();

            assertArrayEquals(new int[]{1, 2, 3}, event.getValues());
        });
    }

    @Test
    public void testJPQLWithTypedParameterValue() {
        doInJPA(entityManager -> {
            Event event = entityManager
            .createQuery(
                "select e " +
                "from Event e " +
                "where " +
                "   fn_array_contains(e.values, :arrayValues) = true", Event.class)
            .setParameter("arrayValues", new TypedParameterValue(IntArrayType.INSTANCE, new int[]{2, 3}))
            .getSingleResult();

            assertArrayEquals(new int[]{1, 2, 3}, event.getValues());
        });
    }

    @Test
    public void testCriteriaAPI() {
        doInJPA(entityManager -> {
            CriteriaBuilder cb = entityManager.getCriteriaBuilder();
            CriteriaQuery<Event> cq = cb.createQuery(Event.class);
            Root<Event> root = cq.from(Event.class);
            cq.select(root);

            ParameterExpression containValues = cb.parameter(int[].class, "arrayValues");
            cq.where(
                cb.equal(
                    cb.function(
                        "fn_array_contains",
                        Boolean.class,
                        root.get("values"), containValues
                    ),
                    Boolean.TRUE
                )
            );

            Event event = (Event) entityManager
            .createQuery(cq)
            .unwrap(Query.class)
            .setParameter("arrayValues", new int[]{2, 3}, IntArrayType.INSTANCE)
            .getSingleResult();

            assertArrayEquals(new int[]{1, 2, 3}, event.getValues());
        });
    }

    @Entity(name = "Event")
    @Table(name = "event")
    @TypeDef(
        typeClass = IntArrayType.class,
        defaultForType = int[].class
    )
    public static class Event {

        @Id
        private Long id;

        private String name;

        @Column(
            name = "event_values",
            columnDefinition = "integer[]"
        )
        private int[] values;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int[] getValues() {
            return values;
        }

        public void setValues(int[] values) {
            this.values = values;
        }
    }
}
