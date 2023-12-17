package io.hypersistence.utils.jdbc.validator;

import net.ttddyy.dsproxy.QueryCountHolder;

/**
 * SQLStatementCountValidator - Validates recorded statements count.
 *
 * First you execute some operations against your database and then you check how many statements were executed.
 * This is a useful tool against the "N+1" problem or suboptimal DML statements.
 *
 * <p>
 * For more details about how to use it, check out <a href="https://vladmihalcea.com/how-to-detect-the-n-plus-one-query-problem-during-testing/">this article</a> on <a href="https://vladmihalcea.com/">vladmihalcea.com</a>.
 *
 * @author Vlad Mihalcea
 * @since 3.0.2
 */
public class SQLStatementCountValidator {

    private SQLStatementCountValidator() {}

    /**
     * Reset the statement recorder
     */
    public static void reset() {
        QueryCountHolder.clear();
    }

    /**
     * Assert select statement count
     *
     * @param expectedCount expected select statement count
     */
    public static void assertSelectCount(int expectedCount) {
        StatementType.SELECT.validate(expectedCount);
    }

    /**
     * Assert insert statement count
     *
     * @param expectedCount expected insert statement count
     */
    public static void assertInsertCount(int expectedCount) {
        StatementType.INSERT.validate(expectedCount);
    }

    /**
     * Assert update statement count
     *
     * @param expectedCount expected update statement count
     */
    public static void assertUpdateCount(int expectedCount) {
        StatementType.UPDATE.validate(expectedCount);
    }

    /**
     * Assert delete statement count
     *
     * @param expectedCount expected delete statement count
     */
    public static void assertDeleteCount(int expectedCount) {
        StatementType.DELETE.validate(expectedCount);
    }

    /**
     * Assert the total statement count
     *
     * @param expectedCount expected total statement count
     */
    public static void assertTotalCount(int expectedCount) {
        StatementType.TOTAL.validate(expectedCount);
    }
}
