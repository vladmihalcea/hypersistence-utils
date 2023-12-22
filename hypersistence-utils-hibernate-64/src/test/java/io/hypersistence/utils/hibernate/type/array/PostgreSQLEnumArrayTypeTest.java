package io.hypersistence.utils.hibernate.type.array;

import io.hypersistence.utils.hibernate.util.AbstractPostgreSQLIntegrationTest;
import jakarta.persistence.*;
import org.hibernate.annotations.Type;
import org.hibernate.query.Query;
import org.hibernate.query.TypedParameterValue;
import org.junit.Before;
import org.junit.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.fail;

/**
 * @author Nikita Konev
 */
public class PostgreSQLEnumArrayTypeTest extends AbstractPostgreSQLIntegrationTest {

    public static final EnumArrayType ROLE_TYPE = new EnumArrayType(UserRole.class, "user_role");

    @Override
    protected Class<?>[] entities() {
        return new Class<?>[]{
                UserAccount.class
        };
    }

    @Before
    public void init() {
        DataSource dataSource = newDataSource();
        try (Connection connection = dataSource.getConnection()) {
            try (Statement statement = connection.createStatement()){
                statement.executeUpdate("DROP TYPE IF EXISTS user_role;");
                statement.executeUpdate("CREATE TYPE user_role AS ENUM ('ROLE_ADMIN', 'ROLE_USER');");
            }
        } catch (SQLException e) {
            fail(e.getMessage());
        }
        super.init();
    }

    @Test
    public void test() {
        UserRole[] userRoles = {UserRole.ROLE_ADMIN, UserRole.ROLE_USER};

        doInJPA(entityManager -> {
            UserAccount account = new UserAccount();
            account.setUsername("vladmihalcea.com");
            account.setRoles(userRoles);
            entityManager.persist(account);
        });

        doInJPA(entityManager -> {
            UserAccount singleResult = entityManager
            .createQuery(
                "select ua " +
                "from UserAccountEntity ua " +
                "where ua.username = :username", UserAccount.class)
            .setParameter("username", "vladmihalcea.com")
            .getSingleResult();

            assertArrayEquals(userRoles, singleResult.getRoles());
        });
    }

    @Test
    public void testSetParameterWithType() {
        UserRole[] userRoles = {UserRole.ROLE_ADMIN, UserRole.ROLE_USER};
        UserRole[] requiredRoles = {UserRole.ROLE_USER};

        doInJPA(entityManager -> {
            UserAccount account = new UserAccount();
            account.setUsername("vladmihalcea.com");
            account.setRoles(userRoles);
            entityManager.persist(account);
        });

        doInJPA(entityManager -> {
            entityManager
            .createQuery(
                "select ua " +
                "from UserAccountEntity ua " +
                "where ua.roles = :roles", UserAccount.class)
            .unwrap(Query.class)
            .setParameter("roles", requiredRoles, new EnumArrayType(UserRole[].class, "user_role"))
            .getResultList();
        });
    }

    @Test
    public void testTypedParameterValue() {
        UserRole[] userRoles = {UserRole.ROLE_ADMIN, UserRole.ROLE_USER};
        UserRole[] requiredRoles = {UserRole.ROLE_USER};

        doInJPA(entityManager -> {
            UserAccount account = new UserAccount();
            account.setUsername("vladmihalcea.com");
            account.setRoles(userRoles);
            entityManager.persist(account);
        });

        doInJPA(entityManager -> {
            entityManager
            .createQuery(
                "select ua " +
                "from UserAccountEntity ua " +
                "where ua.roles = :roles", UserAccount.class)
            .setParameter("roles", new TypedParameterValue(new EnumArrayType(UserRole[].class, "user_role"), requiredRoles))
            .getResultList();
        });
    }

    public enum UserRole {
        ROLE_ADMIN,
        ROLE_USER,
    }

    @Entity(name = "UserAccountEntity")
    @Table(name = "users")
    public static class UserAccount {

        @Id
        @GeneratedValue
        private Long id;

        private String username;

        @Type(
            value = EnumArrayType.class,
            parameters = @org.hibernate.annotations.Parameter(
                name = "sql_array_type",
                value = "user_role"
            )
        )
        @Column(
            name = "roles",
            columnDefinition = "user_role[]"
        )
        private UserRole roles[];

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public UserRole[] getRoles() {
            return roles;
        }

        public void setRoles(UserRole[] roles) {
            this.roles = roles;
        }
    }
}
