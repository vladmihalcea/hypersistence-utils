package io.hypersistence.utils.hibernate.type.json;

import io.hypersistence.utils.hibernate.type.model.BaseEntity;
import io.hypersistence.utils.hibernate.util.AbstractPostgreSQLIntegrationTest;
import io.hypersistence.utils.jdbc.validator.SQLStatementCountValidator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.Type;
import org.hibernate.envers.Audited;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;

/**
 * @author Vlad Mihalcea
 */
public class PostgreSQLJsonBinaryTypeAuditedTest extends AbstractPostgreSQLIntegrationTest {

    @Override
    protected Class<?>[] entities() {

        return new Class<?>[]{User.class};
    }

    private User _user;

    @Override
    protected void afterInit() {

        doInJPA(entityManager -> {
            User user = new User();

            user.setId(1L);
            user.setPhones(new HashSet<>(asList("7654321", "1234567")));

            entityManager.persist(user);
            _user = user;
        });
    }

    @Test
    public void test() {

        doInJPA(entityManager -> {
            User user = entityManager.find(User.class, _user.getId());
            assertEquals(new HashSet<>(asList("7654321", "1234567")), user.getPhones());
            assertEquals(Integer.valueOf(0), user.getVersion());

            final Set<?> phones = entityManager.createQuery(
                            "select phones from " + User.class.getName() + "_AUD where originalId.id=:id", Set.class)
                    .setParameter("id", _user.getId())
                    .getSingleResultOrNull();
            assertEquals(new HashSet<>(asList("7654321", "1234567")), phones);
        });
    }

    @Test
    public void testLoad() {

        SQLStatementCountValidator.reset();

        doInJPA(entityManager -> {
            User user = entityManager.find(User.class, _user.getId());
            assertEquals(new HashSet<>(asList("1234567", "7654321")), user.getPhones());
            assertEquals(Integer.valueOf(0), user.getVersion());
        });

        SQLStatementCountValidator.assertTotalCount(1);
        SQLStatementCountValidator.assertSelectCount(1);
        SQLStatementCountValidator.assertUpdateCount(0);
    }

    @Entity(name = "User")
    @Table(name = "users")
    @Audited
    public static class User extends BaseEntity {

        private String name;

        @Type(JsonBinaryType.class)
        @Column(columnDefinition = "jsonb")
        private Set<String> phones;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Set<String> getPhones() {
            return phones;
        }

        public void setPhones(Set<String> phones) {
            this.phones = phones;
        }
    }
}
