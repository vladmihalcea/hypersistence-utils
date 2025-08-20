package io.hypersistence.utils.hibernate.naming;

import io.hypersistence.utils.hibernate.util.AbstractTest;
import jakarta.persistence.*;
import org.hibernate.Session;
import org.hibernate.annotations.NaturalId;
import org.junit.Test;

import java.time.LocalDate;
import java.util.Properties;

import static org.junit.Assert.assertEquals;

/**
 * @author Vlad Mihalcea
 */
public class CamelCaseToSnakeCaseNamingStrategyTest extends AbstractTest {

    @Override
    protected Class<?>[] entities() {
        return new Class<?>[] {
                BookAuthor.class,
                PaperBackBook.class,
        };
    }

    @Override
    protected void additionalProperties(Properties properties) {
        properties.put(
            "hibernate.physical_naming_strategy",
            io.hypersistence.utils.hibernate.naming.CamelCaseToSnakeCaseNamingStrategy.INSTANCE
        );
    }

    @Test
    public void test() {
        doInJPA(entityManager -> {
            BookAuthor author = new BookAuthor();
            author.setId(1L);
            author.setFirstName("Vlad");
            author.setLastName("Mihalcea");

            entityManager.persist(author);

            PaperBackBook book = new PaperBackBook();
            book.setISBN("978-9730228236");
            book.setTitle("High-Performance Java Persistence");
            book.setPublishedOn(LocalDate.of(2016, 10, 12));
            book.setPublishedBy(author);

            entityManager.persist(book);
        });

        doInJPA(entityManager -> {
            Session session = entityManager.unwrap(Session.class);

            PaperBackBook book = session.bySimpleNaturalId(PaperBackBook.class).load("978-9730228236");
            assertEquals("High-Performance Java Persistence", book.getTitle());

            assertEquals("Vlad Mihalcea", book.getPublishedBy().getFullName());
        });
    }

    @Entity(name = "BookAuthor")
    public static class BookAuthor {

        @Id
        private Long id;

        private String firstName;

        private String lastName;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getFirstName() {
            return firstName;
        }

        public void setFirstName(String firstName) {
            this.firstName = firstName;
        }

        public String getLastName() {
            return lastName;
        }

        public void setLastName(String lastName) {
            this.lastName = lastName;
        }

        public String getFullName() {
            return firstName + " " + lastName;
        }
    }

    @Entity(name = "PaperBackBook")
    public static class PaperBackBook {

        @Id
        @GeneratedValue(strategy = GenerationType.SEQUENCE)
        private Long id;

        @NaturalId
        private String ISBN;

        private String title;

        private LocalDate publishedOn;

        @ManyToOne(fetch = FetchType.LAZY)
        private BookAuthor publishedBy;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getISBN() {
            return ISBN;
        }

        public void setISBN(String ISBN) {
            this.ISBN = ISBN;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public LocalDate getPublishedOn() {
            return publishedOn;
        }

        public void setPublishedOn(LocalDate publishedOn) {
            this.publishedOn = publishedOn;
        }

        public BookAuthor getPublishedBy() {
            return publishedBy;
        }

        public void setPublishedBy(BookAuthor publishedBy) {
            this.publishedBy = publishedBy;
        }
    }
}
