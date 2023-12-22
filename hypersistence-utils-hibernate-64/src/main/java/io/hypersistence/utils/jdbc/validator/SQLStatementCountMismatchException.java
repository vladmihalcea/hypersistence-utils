package io.hypersistence.utils.jdbc.validator;

/**
 * SQLStatementCountMismatchException - Thrown whenever there is a mismatch between
 * the expected statements count and the ones being executed.
 *
 * @author Vlad Mihalcea
 * @since 3.0.2
 */
public class SQLStatementCountMismatchException extends RuntimeException {

    private final long expected;
    private final long recorded;

    public SQLStatementCountMismatchException(
            StatementType statementType,
            long expected,
            long recorded) {
        super(statementType.toString(expected, recorded));
        this.expected = expected;
        this.recorded = recorded;
    }

    public long getExpected() {
        return expected;
    }

    public long getRecorded() {
        return recorded;
    }
}
