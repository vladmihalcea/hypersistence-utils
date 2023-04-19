package io.hypersistence.utils.spring.domain;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import io.hypersistence.utils.spring.commons.Codes.Publisher;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;
import org.springframework.util.Assert;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Version;
import java.util.Map;
import java.util.Objects;

@Entity
@Table(name = "books")
@TypeDef(name = "jsonb", typeClass = JsonBinaryType.class)
public class Books {
    @Id
    private Long id;

    @Type(type = "jsonb")
    @Column(columnDefinition = "jsonb")
    private Map<String, Publisher> bookDetails;

    @Version
    private Long version;

    protected Books() {
    }

    public Long getId() {
        return id;
    }

    public Map<String, Publisher> getBookDetails() {
        return bookDetails;
    }

    public Long getVersion() {
        return version;
    }

    @Override
    public String toString() {
        return "Books{" + "id=" + id + ", bookDetails=" + bookDetails + ", version=" + version + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Books books = (Books) o;
        return id.equals(books.id) && bookDetails.equals(books.bookDetails) && version.equals(books.version);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, bookDetails, version);
    }

    public Books(Long id, Map<String, Publisher> bookDetails) {
        Assert.notNull(id, "id must not be null");
        Assert.notNull(bookDetails, "books must not be null");

        this.id = id;
        this.bookDetails = bookDetails;
    }

    public void update(Map<String, Publisher> bookDetails) {
        Assert.notNull(bookDetails, "books must not be null");

        if (this.bookDetails.equals(bookDetails)) {
            return;
        }

        this.bookDetails = bookDetails;
    }

    public record BookInventory(Inventory global, Map<Long, Inventory> info) {

        public BookInventory {
            Assert.notNull(info, "listings must be provided");
        }

        public record Inventory(Integer maxItems, Integer remainingItems) {
        }
    }
}
