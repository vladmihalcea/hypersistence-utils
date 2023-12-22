package io.hypersistence.utils.hibernate.type.array;

import io.hypersistence.utils.hibernate.util.AbstractPostgreSQLIntegrationTest;
import jakarta.persistence.*;
import org.hibernate.annotations.Type;
import org.junit.Before;
import org.junit.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.fail;

/**
 * @author Vlad Mihalcea
 */
public class PostgreSQLMultipleEnumArrayTypeTest extends AbstractPostgreSQLIntegrationTest {

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

        try (Connection connection = dataSource.getConnection()) {
            try (Statement statement = connection.createStatement()){
                statement.executeUpdate("DROP TYPE IF EXISTS user_role;");
                statement.executeUpdate("CREATE TYPE user_role AS ENUM ('ROLE_ADMIN', 'ROLE_USER');");
            }

            try (Statement statement = connection.createStatement()){
                statement.executeUpdate("DROP TYPE IF EXISTS user_type;");
                statement.executeUpdate("CREATE TYPE user_type AS ENUM ('SUPER_USER', 'REGULAR');");
            }
        } catch (SQLException e) {
            fail(e.getMessage());
        }
        super.init();
    }

    @Test
    public void test() {
        final UserRole[] userRoles = {
                UserRole.ROLE_ADMIN,
                UserRole.ROLE_USER
        };

        final UserType[] userTypes = {
                UserType.SUPER_USER,
                UserType.REGULAR
        };

        doInJPA(entityManager -> {
            UserAccount account = new UserAccount();
            account.setUsername("vladmihalcea.com");
            account.setRoles(userRoles);
            account.setTypes(userTypes);
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
            assertArrayEquals(userTypes, singleResult.getTypes());
        });
    }

    public enum UserRole {
        ROLE_ADMIN,
        ROLE_USER,
    }

    public enum UserType {
        SUPER_USER,
        REGULAR,
    }

    @Entity(name = "UserAccountEntity")
    @Table(name = "users")
    public static class UserAccount {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
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

        @Type(
            value = EnumArrayType.class,
            parameters = @org.hibernate.annotations.Parameter(
                name = "sql_array_type",
                value = "user_type"
            )
        )
        @Column(
            name = "types",
            columnDefinition = "user_type[]"
        )
        private UserType types[];

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

        public UserType[] getTypes() {
            return types;
        }

        public void setTypes(UserType[] types) {
            this.types = types;
        }
    }
}
