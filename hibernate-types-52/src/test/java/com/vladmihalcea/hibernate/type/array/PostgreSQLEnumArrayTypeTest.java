package com.vladmihalcea.hibernate.type.array;

import com.vladmihalcea.hibernate.type.array.EnumArrayType;
import com.vladmihalcea.hibernate.type.util.AbstractPostgreSQLIntegrationTest;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;
import org.hibernate.annotations.TypeDefs;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import javax.persistence.*;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * @author Nikita Konev
 */
public class PostgreSQLEnumArrayTypeTest extends AbstractPostgreSQLIntegrationTest {

    @Override
    protected Class<?>[] entities() {
        return new Class<?>[]{
                UserAccount.class
        };
    }

    @Before
    public void init() {
        DataSource dataSource = newDataSource();
        try (Connection connection = dataSource.getConnection();) {
            try (Statement statement = connection.createStatement();){
                statement.executeUpdate("DROP SCHEMA IF EXISTS auth CASCADE;");
                statement.executeUpdate("CREATE SCHEMA IF NOT EXISTS auth;");
                statement.executeUpdate("DROP TYPE IF EXISTS auth.user_role;");
                statement.executeUpdate("CREATE TYPE auth.user_role AS ENUM ('ROLE_ADMIN', 'ROLE_USER');");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        super.init();
    }

    @Test
    public void test() {
        UserRole[] userRoles = {UserRole.ROLE_ADMIN, UserRole.ROLE_USER};

        doInJPA(entityManager -> {
            UserAccount account = new UserAccount("newbie", userRoles);
            entityManager.persist(account);
        });

        doInJPA(entityManager -> {
            UserAccount singleResult = entityManager
                    .createQuery("select ua from UserAccountEntity ua where ua.username = :un", UserAccount.class)
                    .setParameter("un", "newbie").getSingleResult();
            Assert.assertNotNull(singleResult);

            Assert.assertArrayEquals(userRoles, singleResult.getRoles());
        });
    }

    public enum UserRole {
        ROLE_ADMIN,
        ROLE_USER,
    }

    @TypeDefs({
            @TypeDef(
                    name = "pgsql_array",
                    typeClass = EnumArrayType.class
            )

    })
    @Entity(name = "UserAccountEntity")
    @Table(name = "users", schema = "auth")
    public static class UserAccount {
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        @Id
        private Long id;
        private String username;


        @Type(type = "pgsql_array", parameters = {@org.hibernate.annotations.Parameter(name = "sql_array_type", value = "auth.user_role")})
        @Column(
                name = "roles",
                columnDefinition = "\"auth\".\"_user_role\""
        )
        private UserRole roles[];

        public UserAccount() { }

        public UserAccount(String username, UserRole[] roles) {
            this.username = username;
            this.roles = roles;
        }

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
