package com.vladmihalcea.hibernate.type.list;

import com.vladmihalcea.hibernate.type.util.AbstractPostgreSQLIntegrationTest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Table;
import org.hibernate.Session;
import org.hibernate.annotations.NaturalId;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

/**
 * @author Daniel Hoffmann
 */
public class PostgreSQLIntegerListTypeTest extends AbstractPostgreSQLIntegrationTest {
    @Override
    protected Class<?>[] entities() {
        return new Class<?>[]{
            Book.class
        };
    }

    @Test
    public void testSearch() {

        doInJPA(entityManager -> {
            Book book = new Book();
            book.setIsbn("978-9730228236");
            book.getMissingPageNumbers().add(12);
            book.getMissingPageNumbers().add(15);
            book.getMissingPageNumbers().add(20);

            entityManager.persist(book);
        });

        doInJPA(entityManager -> {
            Book book = entityManager.unwrap(Session.class)
                .bySimpleNaturalId(Book.class)
                .load("978-9730228236");

            assertTrue(book.getMissingPageNumbers().containsAll(Arrays.asList(12, 15, 20)));
        });
    }

    @Test
    public void testUpdate() {

        doInJPA(entityManager -> {
            Book book = new Book();
            book.setIsbn("978-9730228236");
            book.getMissingPageNumbers().add(12);
            book.getMissingPageNumbers().add(15);
            book.getMissingPageNumbers().add(20);

            entityManager.persist(book);
        });

        doInJPA(entityManager -> {
            Book book = entityManager.unwrap(Session.class)
                .bySimpleNaturalId(Book.class)
                .load("978-9730228236");

            book.missingPageNumbers.add(60);

            entityManager.persist(book);
        });

        doInJPA(entityManager -> {
            Book book = entityManager.unwrap(Session.class)
                .bySimpleNaturalId(Book.class)
                .load("978-9730228236");

            assertTrue(book.getMissingPageNumbers().containsAll(Arrays.asList(12, 15, 20, 60)));
        });
    }

    @Entity(name = "Book")
    @Table(name = "book")
    @TypeDef(name = "integer_list", typeClass = PostgreSQLIntegerListType.class)
    public static class Book {

        @Id
        @GeneratedValue
        private Long id;

        @NaturalId
        @Column(length = 15)
        private String isbn;

        @Type(type = "integer_list")
        @Column(columnDefinition = "_int4")
        private List<Integer> missingPageNumbers = new ArrayList<>();

        public String getIsbn() {
            return isbn;
        }

        public void setIsbn(String isbn) {
            this.isbn = isbn;
        }

        public List<Integer> getMissingPageNumbers() {
            return missingPageNumbers;
        }

        public void setMissingPageNumbers(List<Integer> missingPageNumbers) {
            this.missingPageNumbers = missingPageNumbers;
        }
    }
}
