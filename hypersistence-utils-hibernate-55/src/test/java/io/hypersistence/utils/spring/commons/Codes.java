package io.hypersistence.utils.spring.commons;

public class Codes {
    public enum Publisher implements BaseEnum {
        PUBLISHER_1 ("Publisher 1"),
        PUBLISHER_2("Publisher 2");

        private final String value;

        Publisher(String value) {
            this.value = value;
        }

        @Override
        public String getValue() {
            return value;
        }

        @Override
        public String toString() {
            return value;
        }
    }

    public enum BookTypes implements BaseEnum {
        FICTION ("Fiction"),
        NON_FICTION ("Non-fiction");

        private final String value;

        BookTypes(String value) {
            this.value = value;
        }

        @Override
        public String getValue() {
            return value;
        }

        @Override
        public String toString() {
            return value;
        }
    }
}
