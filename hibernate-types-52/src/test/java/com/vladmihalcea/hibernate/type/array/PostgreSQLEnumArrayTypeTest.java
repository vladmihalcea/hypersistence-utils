package com.vladmihalcea.hibernate.type.array;

import com.vladmihalcea.hibernate.type.array.EnumArrayType;
import com.vladmihalcea.hibernate.type.util.AbstractPostgreSQLIntegrationTest;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;
import org.hibernate.annotations.TypeDefs;
import org.junit.Assert;
import org.junit.Test;
import javax.persistence.*;

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
        ROLE_ADMIN, // 0
        ROLE_USER, // 1
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
                columnDefinition = "\"auth\".\"_user_role\"" // first _ means array - this returns from JDBC Driver and SQL queries created him.
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
