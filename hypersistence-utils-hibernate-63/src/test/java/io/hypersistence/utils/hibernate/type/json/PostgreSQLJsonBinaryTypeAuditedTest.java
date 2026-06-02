package io.hypersistence.utils.hibernate.type.json;

import com.fasterxml.jackson.core.type.TypeReference;
import io.hypersistence.utils.hibernate.type.model.BaseEntity;
import io.hypersistence.utils.hibernate.util.AbstractPostgreSQLIntegrationTest;
import io.hypersistence.utils.jdbc.validator.SQLStatementCountValidator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.Type;
import org.hibernate.envers.AuditReader;
import org.hibernate.envers.AuditReaderFactory;
import org.hibernate.envers.Audited;
import org.junit.Test;

import java.io.Serializable;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
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
            user.setAddresses(List.of(new Address("My Road 1"), new Address("Another Street 5")));
            user.setCars(List.of(new Car("Skoda"), new Car("BMW")));
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
            assertEquals(List.of(new Address("My Road 1"), new Address("Another Street 5")), user.getAddresses());
            assertEquals(List.of(new Car("Skoda"), new Car("BMW")), user.getCars());

            final Set<?> phones = entityManager.createQuery(
                            "select phones from " + User.class.getName() + "_AUD where originalId.id=:id", Set.class)
                    .setParameter("id", _user.getId())
                    .getSingleResult();
            assertEquals(new HashSet<>(asList("7654321", "1234567")), phones);
        });
    }


    @Test
    public void collectionsOfReferenceTypesCanBeUnmarshalledInTheAuditReader() {

        doInJPA(entityManager -> {
            AuditReader auditReader = AuditReaderFactory.get(entityManager);

            List<Number> revisions = auditReader.getRevisions(User.class,  _user.getId());
            assertEquals(1, revisions.size());
            User rev1 = auditReader.find(User.class, _user.getId(), revisions.get(0));
            assertEquals("My Road 1", rev1.getAddresses().get(0).getStreet());
            assertEquals("Another Street 5", rev1.getAddresses().get(1).getStreet());
            assertEquals("Skoda", rev1.getCars().get(0).getBrand());
            assertEquals("BMW", rev1.getCars().get(1).getBrand());
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

    public static class CarListJsonBinaryType extends JsonBinaryType {
        public CarListJsonBinaryType() {
            super(new TypeReference<List<Car>>() {}.getType());
        }
    }

    public static class Car implements Serializable {
        private String brand;

        public Car() {}

        public Car(String brand) {
            this.brand = brand;
        }

        public String getBrand() {
            return brand;
        }

        public void setBrand(String brand) {
            this.brand = brand;
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass())
                return false;
            Car car = (Car) o;
            return Objects.equals(brand, car.brand);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(brand);
        }
    }



    public static class Address implements Serializable {
        private String street;

        public Address() {}

        public Address(String street) {
            this.street = street;
        }

        public String getStreet() {
            return street;
        }

        public void setStreet(String street) {
            this.street = street;
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass())
                return false;
            Address address = (Address) o;
            return Objects.equals(street, address.street);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(street);
        }
    }


    @Entity(name = "User")
    @Table(name = "users")
    @Audited
    public static class User extends BaseEntity {

        private String name;

        @Type(JsonBinaryType.class)
        @Column(columnDefinition = "jsonb")
        private Set<String> phones;

        @Type(JsonBinaryType.class)
        @Column(columnDefinition = "jsonb")
        private List<Address> addresses;


        @Type(CarListJsonBinaryType.class)
        @Column(columnDefinition = "jsonb")
        private List<Car> cars;


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

        public List<Address> getAddresses() {
            return addresses;
        }

        public void setAddresses(List<Address> addresses) {
            this.addresses = addresses;
        }

        public List<Car> getCars() {
            return cars;
        }

        public void setCars(List<Car> cars) {
            this.cars = cars;
        }
    }
}
