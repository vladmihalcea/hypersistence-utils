package io.hypersistence.utils.hibernate.type.json;

import io.hypersistence.utils.hibernate.type.model.BaseEntity;
import io.hypersistence.utils.hibernate.util.AbstractMySQLIntegrationTest;
import io.hypersistence.utils.hibernate.util.transaction.JPATransactionFunction;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.Type;
import org.junit.Test;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EntityManager;
import javax.persistence.Table;
import java.beans.ConstructorProperties;
import java.io.Serializable;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;

/**
 * @author Sergei Poznanski
 */
public class MySQLJsonTypeSetTest extends AbstractMySQLIntegrationTest {

    @Override
    protected Class<?>[] entities() {
        return new Class<?>[]{
              User.class
        };
    }

    @Test
    public void test() {
        final AtomicReference<User> userHolder = new AtomicReference<User>();

        doInJPA(new JPATransactionFunction<Void>() {

            @Override
            public Void apply(EntityManager entityManager) {
                User user = new User();

                user.setId(1L);
                user.setPhones(new HashSet<String>(asList("7654321", "1234567")));
                user.setRoles(EnumSet.of(Role.ADMIN, Role.USER));
                user.setChildren(new HashSet<Child>(asList(
                        new Child("Jane", 2, addElementsToSet(new HashSet<String>(), "toy1", "toy2")),
                        new Child("John", 1, addElementsToSet(new HashSet<String>(), "toy3", "toy4")))
                ));

                entityManager.persist(user);
                userHolder.set(user);

                return null;
            }
        });

        doInJPA(new JPATransactionFunction<Void>() {

            @Override
            public Void apply(EntityManager entityManager) {
                User user = entityManager.find(User.class, userHolder.get().getId());
                assertEquals(new HashSet<String>(asList("1234567", "7654321")), user.getPhones());
                assertEquals(EnumSet.of(Role.USER, Role.ADMIN), user.getRoles());
                assertEquals(new HashSet<Child>(asList(
                        new Child("John", 1, addElementsToSet(new HashSet<String>(), "toy4", "toy3")),
                        new Child("Jane", 2, addElementsToSet(new HashSet<String>(), "toy2", "toy1"))
                )), user.getChildren());
                assertEquals(Integer.valueOf(0), user.getVersion());

                return null;
            }
        });

        doInJPA(new JPATransactionFunction<Void>() {

            @Override
            public Void apply(EntityManager entityManager) {
                User user = entityManager.find(User.class, userHolder.get().getId());
                user.setPhones(new HashSet<String>(asList("1592637", "9518473")));
                user.setRoles(EnumSet.of(Role.USER, Role.DEV));
                user.setChildren(new HashSet<Child>(asList(
                        new Child("Jinny", 1, addElementsToSet(new HashSet<String>(), "toy5", "toy6")),
                        new Child("Jenny", 2, addElementsToSet(new HashSet<String>(), "toy7", "toy8"))
                )));

                return null;
            }
        });

        doInJPA(new JPATransactionFunction<Void>() {

            @Override
            public Void apply(EntityManager entityManager) {
                User user = entityManager.find(User.class, userHolder.get().getId());
                assertEquals(new HashSet<String>(asList("9518473", "1592637")), user.getPhones());
                assertEquals(EnumSet.of(Role.DEV, Role.USER), user.getRoles());
                assertEquals(new HashSet<Child>(asList(
                        new Child("Jenny", 2, addElementsToSet(new HashSet<String>(), "toy8", "toy7")),
                        new Child("Jinny", 1, addElementsToSet(new HashSet<String>(), "toy6", "toy5"))
                )), user.getChildren());
                assertEquals(Integer.valueOf(1), user.getVersion());

                return null;
            }
        });
    }

    protected <T> Set<T> addElementsToSet(Set<T> set, T... elements) {
        for (T element : elements) {
            set.add(element);
        }

        return set;
    }

    @Entity
    @Table(name = "users")
    @DynamicUpdate
    public static class User extends BaseEntity {

        @Type(type = "json")
        @Column(nullable = false, columnDefinition = "json")
        private Set<String> phones;

        @Type(type = "json")
        @Column(nullable = false, columnDefinition = "json")
        private EnumSet<Role> roles;

        @Type(type = "json")
        @Column(nullable = false, columnDefinition = "json")
        private Set<Child> children;

        public Set<String> getPhones() {
            return phones;
        }

        public void setPhones(Set<String> phones) {
            this.phones = phones;
        }

        public EnumSet<Role> getRoles() {
            return roles;
        }

        public void setRoles(EnumSet<Role> roles) {
            this.roles = roles;
        }

        public Set<Child> getChildren() {
            return children;
        }

        public void setChildren(final Set<Child> children) {
            this.children = children;
        }
    }

    public enum Role {
        USER, ADMIN, DEV
    }

    public static class Child implements Serializable {

        private String name;
        private Integer age;
        private Set<String> toys;

        @ConstructorProperties({"name", "age", "toys"})
        public Child(String name, Integer age, final Set<String> toys) {
            this.name = name;
            this.age = age;
            this.toys = toys;
        }

        public Child() {
        }

        public String getName() {
            return name;
        }

        public Integer getAge() {
            return age;
        }

        public Set<String> getToys() {
            return toys;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            final Child child = (Child) o;
            if (!name.equals(child.name)) return false;
            if (!age.equals(child.age)) return false;
            return toys.equals(child.toys);
        }

        @Override
        public int hashCode() {
            int result = name.hashCode();
            result = 31 * result + age.hashCode();
            result = 31 * result + toys.hashCode();
            return result;
        }
    }}
