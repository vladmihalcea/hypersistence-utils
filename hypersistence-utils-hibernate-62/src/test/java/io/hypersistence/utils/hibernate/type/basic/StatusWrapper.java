package io.hypersistence.utils.hibernate.type.basic;

public class StatusWrapper {
    private PostgreSQLEnumTest.PostStatus status;

    public StatusWrapper(PostgreSQLEnumTest.PostStatus status) {
        this.status = status;
    }
}
