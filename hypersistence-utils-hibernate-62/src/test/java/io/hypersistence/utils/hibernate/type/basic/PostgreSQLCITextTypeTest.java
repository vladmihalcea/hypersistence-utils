package io.hypersistence.utils.hibernate.type.basic;

import io.hypersistence.utils.hibernate.util.AbstractPostgreSQLIntegrationTest;
import io.hypersistence.utils.test.transaction.EntityManagerTransactionFunction;
import jakarta.persistence.*;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import org.hibernate.annotations.Type;
import org.junit.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * @author Sergei Portnov
 */
public class PostgreSQLCITextTypeTest extends AbstractPostgreSQLIntegrationTest {

    @Override
    protected Class<?>[] entities() {
        return new Class<?>[]{
                Country.class
        };
    }

    @Override
    public void init() {
        DataSource dataSource = newDataSource();
        Connection connection = null;
        Statement statement = null;
        try {
            connection = dataSource.getConnection();
            statement = connection.createStatement();

            statement.executeUpdate("CREATE EXTENSION IF NOT EXISTS citext");
        } catch (SQLException e) {
            fail(e.getMessage());
        } finally {
            if(statement != null) {
                try {
                    statement.close();
                } catch (SQLException e) {
                    fail(e.getMessage());
                }
            }
            if(connection != null) {
                try {
                    connection.close();
                } catch (SQLException e) {
                    fail(e.getMessage());
                }
            }
        }
        super.init();
    }

    @Test
    public void test() {
        Country countryWithNullName = new Country();
        countryWithNullName.setId(1L);
        persist(countryWithNullName);
        testFindById(countryWithNullName.getId(), countryWithNullName.getName());

        Country countryWithName = new Country();
        countryWithName.setId(2L);
        countryWithName.setName("Test");
        persist(countryWithName);
        testFindById(countryWithName.getId(), countryWithName.getName());

        testFindCountryByName(countryWithName.getName(), countryWithName);
        testFindCountryByName(countryWithName.getName().toUpperCase(), countryWithName);
        testFindCountryByName(countryWithName.getName().toLowerCase(), countryWithName);
    }

    private void persist(final Country country) {
        doInJPA(new EntityManagerTransactionFunction<Void>() {
            @Override
            public Void apply(EntityManager entityManager) {
                entityManager.persist(country);

                return null;
            }
        });
    }

    private void testFindById(final Long countryId, final String expectedName) {
        doInJPA(new EntityManagerTransactionFunction<Void>() {
            @Override
            public Void apply(EntityManager entityManager) {
                Country country = entityManager.find(Country.class, countryId);

                assertEquals(expectedName, country.getName());

                return null;
            }
        });
    }

    private void testFindCountryByName(final String searchableName, final Country expectedCountry) {
        doInJPA(new EntityManagerTransactionFunction<Void>() {
            @Override
            public Void apply(EntityManager entityManager) {
                CriteriaBuilder builder = entityManager.getCriteriaBuilder();

                CriteriaQuery<Country> criteria = builder.createQuery(Country.class);

                Root<Country> root = criteria.from(Country.class);

                criteria.where(
                        builder.equal(root.get("name"), searchableName)
                );

                List<Country> countries = entityManager
                        .createQuery(criteria).getResultList();

                assertEquals(1, countries.size());

                Country country = countries.iterator().next();

                assertEquals(expectedCountry.getId(), country.getId());
                assertEquals(expectedCountry.getName(), country.getName());

                return null;
            }
        });
    }

    @Table(name = "country")
    @Entity(name = "Country")
    public static class Country {

        @Id
        private Long id;

        @Type(PostgreSQLCITextType.class)
        @Column(columnDefinition = "citext")
        private String name;

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
    }
}