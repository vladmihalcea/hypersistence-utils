package io.hypersistence.utils.spring.domain;

import io.hypersistence.utils.spring.commons.Codes.Publisher;

import java.util.Map;

public final class SharedDomainSharedDataFixture {

    private SharedDomainSharedDataFixture() {
    }

    public static Map<String, Publisher> standardBook() {
        return Map.of("Testing", Publisher.PUBLISHER_1);
    }
}
