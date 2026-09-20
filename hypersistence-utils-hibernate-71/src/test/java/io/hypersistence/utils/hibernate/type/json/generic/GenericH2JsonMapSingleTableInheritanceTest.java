package io.hypersistence.utils.hibernate.type.json.generic;

import io.hypersistence.utils.hibernate.type.json.JsonType;
import io.hypersistence.utils.hibernate.util.AbstractTest;
import io.hypersistence.utils.hibernate.util.providers.H2DataSourceProvider;
import io.hypersistence.utils.test.providers.DataSourceProvider;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.Table;
import org.hibernate.Session;
import org.hibernate.annotations.Type;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

/**
 * Regression test for <a href="https://github.com/vladmihalcea/hypersistence-utils/issues/739">#739</a>.
 */
public class GenericH2JsonMapSingleTableInheritanceTest extends AbstractTest {

    @Override
    protected Class<?>[] entities() {
        return new Class<?>[]{
            Book.class,
            Fiction.class,
            NonFiction.class
        };
    }

    @Override
    protected DataSourceProvider dataSourceProvider() {
        return new H2DataSourceProvider();
    }

    @Override
    protected void afterInit() {
        doInJPA(entityManager -> {
            Fiction fiction = new Fiction();
            fiction.id = 1L;
            fiction.isbn = "978-9730228236";
            fiction.properties.put("title", "Fiction");
            entityManager.persist(fiction);

            NonFiction nonFiction = new NonFiction();
            nonFiction.id = 2L;
            nonFiction.isbn = "978-9730228237";
            nonFiction.properties.put("title", "Nonfiction");
            entityManager.persist(nonFiction);
        });
    }

    @Test
    public void testNativeQueryWithAliasInjection() {
        doInJPA(entityManager -> {
            List<?> books = entityManager.unwrap(Session.class)
                .createNativeQuery("select {b.*} from book b order by b.id")
                .addEntity("b", Book.class)
                .getResultList();

            assertBooks(books);
        });
    }

    @Test
    public void testNativeQueryWithoutAliasInjection() {
        doInJPA(entityManager -> {
            assertBooks(entityManager.createNativeQuery(
                "select b.* from book b order by b.id", Book.class
            ).getResultList());
        });
    }

    @Test
    public void testJpqlQuery() {
        doInJPA(entityManager -> {
            assertBooks(entityManager.createQuery("select b from Book b order by b.id", Book.class).getResultList());
        });
    }

    private void assertBooks(List<?> books) {
        assertEquals(2, books.size());
        Fiction fiction = Fiction.class.cast(books.get(0));
        NonFiction nonFiction = NonFiction.class.cast(books.get(1));
        assertEquals("978-9730228236", fiction.isbn);
        assertEquals("978-9730228237", nonFiction.isbn);
        assertEquals("Fiction", fiction.properties.get("title"));
        assertEquals("Nonfiction", nonFiction.properties.get("title"));
    }

    @Entity(name = "Book")
    @Table(name = "book")
    @Inheritance(strategy = InheritanceType.SINGLE_TABLE)
    @DiscriminatorColumn(name = "book_type")
    public static class Book {

        @Id
        protected Long id;

        protected String isbn;
    }

    @Entity(name = "Fiction")
    @DiscriminatorValue("FICTION")
    public static class Fiction extends Book {

        @Type(JsonType.class)
        @Column(columnDefinition = "json")
        private Map<String, String> properties = new HashMap<>();
    }

    @Entity(name = "NonFiction")
    @DiscriminatorValue("NONFICTION")
    public static class NonFiction extends Book {

        @Type(JsonType.class)
        @Column(columnDefinition = "json")
        private Map<String, String> properties = new HashMap<>();
    }
}
