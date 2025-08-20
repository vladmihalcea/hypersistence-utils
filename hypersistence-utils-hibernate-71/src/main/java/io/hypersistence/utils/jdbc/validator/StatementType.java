package io.hypersistence.utils.jdbc.validator;

import net.ttddyy.dsproxy.QueryCount;
import net.ttddyy.dsproxy.QueryCountHolder;

/**
 * StatementType - The type of statements that got validated.
 *
 * @author Vlad Mihalcea
 * @since 3.0.2
 */
enum StatementType {
    SELECT {
        @Override
        protected long recordedCount() {
            QueryCount queryCount = QueryCountHolder.getGrandTotal();
            return queryCount.getSelect();
        }
    },
    INSERT {
        @Override
        protected long recordedCount() {
            QueryCount queryCount = QueryCountHolder.getGrandTotal();
            return queryCount.getInsert();
        }
    },
    UPDATE {
        @Override
        protected long recordedCount() {
            QueryCount queryCount = QueryCountHolder.getGrandTotal();
            return queryCount.getUpdate();
        }
    },
    DELETE {
        @Override
        protected long recordedCount() {
            QueryCount queryCount = QueryCountHolder.getGrandTotal();
            return queryCount.getDelete();
        }
    },
    TOTAL {
        @Override
        protected long recordedCount() {
            QueryCount queryCount = QueryCountHolder.getGrandTotal();
            return queryCount.getTotal();
        }

        @Override
        protected String toString(long expectedCount, long recordedCount) {
            return String.format(
                "Expected a total of [%d] statement(s) but recorded [%d] instead!",
                expectedCount,
                recordedCount
            );
        }
    };

    protected abstract long recordedCount();

    protected String toString(long expectedCount, long recordedCount) {
        return String.format(
            "Expected [%d] %s statement%s but recorded [%d] instead!",
            expectedCount,
            name(),
            expectedCount > 1 ? "s" : "",
            recordedCount
        );
    }

    /**
     * Validate if the expected statements match the record ones.
     *
     * @param expectedCount expected SQL statements
     */
    void validate(long expectedCount) {
        long recordedCount = recordedCount();
        if (expectedCount != recordedCount) {
            throw new SQLStatementCountMismatchException(
                this,
                expectedCount,
                recordedCount
            );
        }
    }
}
