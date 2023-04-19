package io.hypersistence.utils.spring.domain;

import io.hypersistence.utils.spring.commons.Codes.BookTypes;
import io.hypersistence.utils.spring.commons.Codes.Publisher;
import io.hypersistence.utils.spring.domain.Books.BookInventory;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class SharedDomainSharedDataFixture {

    private SharedDomainSharedDataFixture() {
    }

    public static Map<Publisher, Map<BookTypes, BookInventory>> standardBook() {
        Map<Publisher, Map<BookTypes, BookInventory>> bookDetails = new HashMap<>();
        Map<BookTypes, BookInventory> bookTypeMap = new HashMap<>();
        bookTypeMap.put(BookTypes.FICTION,
                new BookInventory(new BookInventory.Inventory(10, 1),
                        Collections.singletonMap(501L, new BookInventory.Inventory(1, 1))));
        bookDetails.put(Publisher.PUBLISHER_1, bookTypeMap);

        return bookDetails;
    }

}
