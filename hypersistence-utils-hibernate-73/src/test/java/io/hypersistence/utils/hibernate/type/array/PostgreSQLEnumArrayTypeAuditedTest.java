package io.hypersistence.utils.hibernate.type.array;

import io.hypersistence.utils.hibernate.util.AbstractPostgreSQLIntegrationTest;
import jakarta.persistence.*;
import org.hibernate.annotations.Type;
import org.hibernate.envers.AuditReader;
import org.hibernate.envers.AuditReaderFactory;
import org.hibernate.envers.Audited;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Regression test for <a href="https://github.com/vladmihalcea/hypersistence-utils/issues/863">#863</a>.
 */
public class PostgreSQLEnumArrayTypeAuditedTest extends AbstractPostgreSQLIntegrationTest {

    @Override
    protected Class<?>[] entities() {
        return new Class<?>[]{
            UserAccountAudited.class
        };
    }

    @Override
    protected void beforeInit() {
        executeStatement("DROP TYPE IF EXISTS user_role;");
        executeStatement("CREATE TYPE user_role AS ENUM ('ROLE_ADMIN', 'ROLE_USER');");
    }

    @Test
    public void testAuditedRevisionCanBeLoaded() {
        UserRole[] userRoles = {UserRole.ROLE_ADMIN, UserRole.ROLE_USER};

        doInJPA(entityManager -> {
            UserAccountAudited account = new UserAccountAudited();
            account.setUsername("vladmihalcea.com");
            account.setRoles(userRoles);
            entityManager.persist(account);
        });

        doInJPA(entityManager -> {
            UserAccountAudited singleResult = entityManager.createQuery(
                    """
                    select ua
                    from UserAccountAudited ua
                    where ua.username = :username
                    """,
                    UserAccountAudited.class)
                .setParameter("username", "vladmihalcea.com")
                .getSingleResult();

            assertNotNull(singleResult);
            assertArrayEquals(userRoles, singleResult.getRoles());

            AuditReader auditReader = AuditReaderFactory.get(entityManager);
            List<Number> revisions = auditReader.getRevisions(UserAccountAudited.class, singleResult.getId());
            assertEquals(1, revisions.size());

            UserAccountAudited revisioned = auditReader.find(
                UserAccountAudited.class,
                singleResult.getId(),
                revisions.get(0)
            );

            assertNotNull(revisioned);
            assertArrayEquals(userRoles, revisioned.getRoles());
        });
    }

    public enum UserRole {
        ROLE_ADMIN,
        ROLE_USER,
    }

    @Entity(name = "UserAccountAudited")
    @Table(name = "users")
    @Audited
    public static class UserAccountAudited {

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
        private UserRole[] roles = new UserRole[0];

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
