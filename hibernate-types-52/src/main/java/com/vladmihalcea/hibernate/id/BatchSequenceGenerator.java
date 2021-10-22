package com.vladmihalcea.hibernate.id;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.hibernate.HibernateException;
import org.hibernate.MappingException;
import org.hibernate.boot.model.relational.Database;
import org.hibernate.boot.model.relational.QualifiedNameParser;
import org.hibernate.dialect.Dialect;
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment;
import org.hibernate.engine.jdbc.spi.JdbcCoordinator;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.id.BulkInsertionCapableIdentifierGenerator;
import org.hibernate.id.Configurable;
import org.hibernate.id.IdentifierGenerationException;
import org.hibernate.id.PersistentIdentifierGenerator;
import org.hibernate.id.enhanced.DatabaseStructure;
import org.hibernate.id.enhanced.SequenceStructure;
import org.hibernate.internal.util.config.ConfigurationHelper;
import org.hibernate.service.ServiceRegistry;
import org.hibernate.type.Type;

/**
 * A sequence generator that uses a recursive query to fetch multiple
 * values from a sequence in a single database access.
 *
 * <h2>Parameters</h2>
 * The following configuration parameters are supported:
 * <dl>
 * <dt>{@value #SEQUENCE_PARAM}</dt>
 * <dd><strong>mandatory</strong>, name of the sequence to use</dd>
 * <dt>{@value #FETCH_SIZE_PARAM}</dt>
 * <dd>optional, how many sequence numbers should be fetched at a time,
 * default is {@value #DEFAULT_FETCH_SIZE}</dd>
 * </dl>
 *
 * <h2>SQL</h2>
 * Per default the generated SELECT will look something like this
 * <pre><code>
 * WITH RECURSIVE t(n, level_num) AS (
 *   SELECT nextval(seq_xxx) AS n, 1 AS level_num
 *     UNION ALL
 *   SELECT nextval(seq_xxx) AS n, level_num + 1 AS level_num
 *   FROM t
 *   WHERE level_num &lt; ?)
 * SELECT n
 *   FROM t;
 * </code></pre>
 *
 * <h3>DB2</h3>
 * For DB2 the generated SELECT will look something like this
 * <pre><code>
 * WITH t(n) AS (
 *   SELECT 1 AS n
 *     FROM (VALUES 1)
 *       UNION ALL
 *     SELECT n + 1 AS n
 *       FROM t
 *      WHERE n &lt; ?)
 * SELECT next value for SEQ_CHILD_ID AS n
 *   FROM t;
 * </code></pre>
 *
 * <h3>HSQLDB</h3>
 * For HSQLDB the generated SELECT will look something like this
 * <pre><code>
 * SELECT next value for seq_parent_id
 *   FROM UNNEST(SEQUENCE_ARRAY(1, ?, 1));
 * </code></pre>
 *
 * <h3>Oracle</h3>
 * For Oracle the generated SELECT will look something like this
 * because Oracle does not support using recursive common table
 * expressions to fetch multiple values from a sequence.
 * <pre><code>
 * SELECT seq_xxx.nextval
 * FROM dual
 * CONNECT BY rownum &lt;= ?
 * </code></pre>
 *
 * <h3>SQL Server</h3>
 * For SQL Server the generated SELECT will look something like this
 * <pre><code>
 * WITH t(n) AS (
 *   SELECT 1 AS n
 *     UNION ALL
 *   SELECT n + 1 AS n
 *     FROM t
 *    WHERE n &lt; ?)
 * SELECT NEXT VALUE FOR seq_xxx AS n
 *   FROM t
 * </code></pre>
 *
 * <h3>Firebird</h3>
 * For Firebird the generated SELECT will look something like this
 * <pre><code>
 * WITH RECURSIVE t(n, level_num) AS (
 *   SELECT NEXT VALUE FOR seq_xxx AS n, 1 AS level_num
 *   FROM rdb$database
 *     UNION ALL
 *   SELECT NEXT VALUE FOR seq_xxx AS n, level_num + 1 AS level_num
 *     FROM t
 *    WHERE level_num &lt; ?)
 * SELECT n
 *   FROM t
 * </code></pre>
 *
 * <h2>Database Support</h2>
 * The following RDBMS have been verified to work
 * <ul>
 *  <li>DB2</li>
 *  <li>Firebird</li>
 *  <li>Oracle</li>
 *  <li>H2</li>
 *  <li>HSQLDB</li>
 *  <li>MariaDB</li>
 *  <li>Postgres</li>
 *  <li>SQL Sever</li>
 * </ul>
 * In theory any RDBMS that supports {@code WITH RECURSIVE} and
 * sequences is supported.
 *
 * @author Philippe Marschall
 * @since 2.14.0
 */
public class BatchSequenceGenerator implements BulkInsertionCapableIdentifierGenerator, PersistentIdentifierGenerator, Configurable {

    /**
     * Indicates the name of the sequence to use, mandatory.
     */
    public static final String SEQUENCE_PARAM = "sequence";

    /**
     * Indicates how many sequence values to fetch at once. The default value is {@link #DEFAULT_FETCH_SIZE}.
     */
    public static final String FETCH_SIZE_PARAM = "fetch_size";

    /**
     * The default value for {@link #FETCH_SIZE_PARAM}.
     */
    public static final int DEFAULT_FETCH_SIZE = 10;

    private final Lock lock = new ReentrantLock();

    private String select;
    private int fetchSize;
    private IdentifierPool identifierPool;
    private IdentifierExtractor identifierExtractor;
    private DatabaseStructure databaseStructure;

    @Override
    public void configure(Type type, Properties params, ServiceRegistry serviceRegistry)
                    throws MappingException {

        JdbcEnvironment jdbcEnvironment = serviceRegistry.getService(JdbcEnvironment.class);
        Dialect dialect = jdbcEnvironment.getDialect();
        String sequenceName = determineSequenceName(params);
        this.fetchSize = determineFetchSize(params);

        this.select = buildSelect(sequenceName, dialect);
        this.identifierExtractor = IdentifierExtractor.getIdentifierExtractor(type.getReturnedClass());
        this.identifierPool = IdentifierPool.empty();

        this.databaseStructure = this.buildDatabaseStructure(type, sequenceName, jdbcEnvironment);
    }

    private static String buildSelect(String sequenceName, Dialect dialect) {
        if (dialect instanceof org.hibernate.dialect.Oracle8iDialect) {
            return "SELECT " + dialect.getSelectSequenceNextValString(sequenceName) + " FROM dual CONNECT BY rownum <= ?";
        }
        if (dialect instanceof org.hibernate.dialect.SQLServerDialect) {
            // No RECURSIVE
            return "WITH t(n) AS ( "
            + "SELECT 1 AS n "
            + "UNION ALL "
            +"SELECT n + 1 AS n FROM t WHERE n < ?) "
            // sequence generation outside of WITH
            + "SELECT " + dialect.getSelectSequenceNextValString(sequenceName) + " AS n FROM t";
        }
        if (dialect instanceof org.hibernate.dialect.DB2Dialect) {
            // No RECURSIVE
            return "WITH t(n) AS ( "
            + "SELECT 1 AS n "
            // difference
            + "FROM (VALUES 1) "
            + "UNION ALL "
            +"SELECT n + 1 AS n FROM t WHERE n < ?) "
            // sequence generation outside of WITH
            + "SELECT " + dialect.getSelectSequenceNextValString(sequenceName) + " AS n FROM t";
        }
        if (dialect instanceof org.hibernate.dialect.HSQLDialect) {
            // https://stackoverflow.com/questions/44472280/cte-based-sequence-generation-with-hsqldb/52329862
            return "SELECT " + dialect.getSelectSequenceNextValString(sequenceName) + " FROM UNNEST(SEQUENCE_ARRAY(1, ?, 1))";
        }
        if (dialect instanceof org.hibernate.dialect.FirebirdDialect) {
            return "WITH RECURSIVE t(n, level_num) AS ("
                            + "SELECT " + dialect.getSelectSequenceNextValString(sequenceName) + " AS n, 1 AS level_num "
                            // difference
                            + " FROM rdb$database "
                            + "UNION ALL "
                            + "SELECT " + dialect.getSelectSequenceNextValString(sequenceName) + " AS n, level_num + 1 AS level_num "
                            + " FROM t "
                            + " WHERE level_num < ?) "
                            + "SELECT n FROM t";
        }
        return "WITH RECURSIVE t(n, level_num) AS ("
        + "SELECT " + dialect.getSelectSequenceNextValString(sequenceName) + " AS n, 1 AS level_num "
        + "UNION ALL "
        + "SELECT " + dialect.getSelectSequenceNextValString(sequenceName) + " AS n, level_num + 1 AS level_num "
        + " FROM t "
        + " WHERE level_num < ?) "
        + "SELECT n FROM t";
    }

    private SequenceStructure buildDatabaseStructure(Type type, String sequenceName, JdbcEnvironment jdbcEnvironment) {
        return new SequenceStructure(jdbcEnvironment,
                        QualifiedNameParser.INSTANCE.parse(sequenceName), 1, 1, type.getReturnedClass());
    }

    private  static String determineSequenceName(Properties params) {
        String sequenceName = params.getProperty(SEQUENCE_PARAM);
        if (sequenceName == null) {
            throw new MappingException("no squence name specified");
        }
        return sequenceName;
    }

    private static int determineFetchSize(Properties params) {
        int fetchSize = ConfigurationHelper.getInt(FETCH_SIZE_PARAM, params, DEFAULT_FETCH_SIZE);
        if (fetchSize <= 0) {
            throw new MappingException("fetch size must be positive");
        }
        return fetchSize;
    }

    @Override
    public boolean supportsBulkInsertionIdentifierGeneration() {
        return true;
    }

    @Override
    public String determineBulkInsertionIdentifierGenerationSelectFragment(Dialect dialect) {
        return dialect.getSelectSequenceNextValString(this.getSequenceName());
    }

    @Override
    public Serializable generate(SharedSessionContractImplementor session, Object object) throws HibernateException {
        this.lock.lock();
        try {
            if (this.identifierPool.isEmpty()) {
                this.identifierPool = this.replenishIdentifierPool(session);
            }
            return this.identifierPool.next();
        } finally {
            this.lock.unlock();
        }
    }

    @Override
    public Object generatorKey() {
        return this.getSequenceName();
    }

    private String getSequenceName() {
        return this.databaseStructure.getName();
    }

    @Override
    @Deprecated
    public String[] sqlCreateStrings(Dialect dialect) {
        return this.databaseStructure.sqlCreateStrings(dialect);
    }

    @Override
    @Deprecated
    public String[] sqlDropStrings(Dialect dialect) {
        return this.databaseStructure.sqlDropStrings(dialect);
    }

    @Override
    public void registerExportables(Database database) {
        this.databaseStructure.registerExportables(database);
    }

    private IdentifierPool replenishIdentifierPool(SharedSessionContractImplementor session)
                    throws HibernateException {
        JdbcCoordinator coordinator = session.getJdbcCoordinator();
        List<Serializable> identifiers = new ArrayList<>(this.fetchSize);
        try (PreparedStatement statement = coordinator.getStatementPreparer().prepareStatement(this.select)) {
            statement.setFetchSize(this.fetchSize);
            statement.setInt(1, this.fetchSize);
            try (ResultSet resultSet = coordinator.getResultSetReturn().extract(statement)) {
                while (resultSet.next()) {
                    identifiers.add(this.identifierExtractor.extractIdentifier(resultSet));
                }
            }
        } catch (SQLException e) {
            throw session.getJdbcServices().getSqlExceptionHelper().convert(
                            e, "could not get next sequence value", this.select);
        }
        if (identifiers.size() != this.fetchSize) {
            throw new IdentifierGenerationException("expected " + this.fetchSize + " values from " + this.getSequenceName()
            + " but got " + identifiers.size());
        }
        return IdentifierPool.forList(identifiers);
    }

    /**
     * Holds a number of prefetched identifiers.
     */
    static final class IdentifierPool {

        private final Iterator<Serializable> iterator;

        private IdentifierPool (List<Serializable> identifiers) {
            this.iterator = identifiers.iterator();
        }

        static IdentifierPool forList(List<Serializable> identifiers) {
            return new IdentifierPool(identifiers);
        }

        static IdentifierPool empty() {
            return new IdentifierPool(Collections.emptyList());
        }

        boolean isEmpty() {
            return !this.iterator.hasNext();
        }

        Serializable next() {
            return this.iterator.next();
        }

    }

    /**
     * Extracts a {@link Serializable} identifier from a {@link ResultSet}.
     *
     * @see org.hibernate.id.IntegralDataTypeHolder
     */
    enum IdentifierExtractor {

        INTEGER_IDENTIFIER_EXTRACTOR {
            @Override
            Serializable extractIdentifier(ResultSet resultSet) throws SQLException {
                int intValue = resultSet.getInt(1);
                if (resultSet.wasNull()) {
                    throw new IdentifierGenerationException("sequence returned null");
                }
                return intValue;
            }
        },

        LONG_IDENTIFIER_EXTRACTOR {
            @Override
            Serializable extractIdentifier(ResultSet resultSet) throws SQLException {
                long longValue = resultSet.getLong(1);
                if (resultSet.wasNull()) {
                    throw new IdentifierGenerationException("sequence returned null");
                }
                return longValue;
            }
        },

        BIG_INTEGER_IDENTIFIER_EXTRACTOR {
            @Override
            Serializable extractIdentifier(ResultSet resultSet) throws SQLException {
                BigDecimal bigDecimal = resultSet.getBigDecimal(1);
                if (resultSet.wasNull()) {
                    throw new IdentifierGenerationException("sequence returned null");
                }
                return bigDecimal.setScale(0, BigDecimal.ROUND_UNNECESSARY).toBigInteger();
            }
        },

        BIG_DECIMAL_IDENTIFIER_EXTRACTOR {
            @Override
            Serializable extractIdentifier(ResultSet resultSet) throws SQLException {
                BigDecimal bigDecimal = resultSet.getBigDecimal(1);
                if (resultSet.wasNull()) {
                    throw new IdentifierGenerationException("sequence returned null");
                }
                return bigDecimal;
            }
        };

        abstract Serializable extractIdentifier(ResultSet resultSet) throws SQLException;

        static IdentifierExtractor getIdentifierExtractor(Class<?> integralType) {
            if ((integralType == Integer.class) || (integralType == int.class)) {
                return INTEGER_IDENTIFIER_EXTRACTOR;
            }
            if ((integralType == Long.class) || (integralType == long.class)) {
                return LONG_IDENTIFIER_EXTRACTOR;
            }
            if (integralType == BigInteger.class) {
                return BIG_INTEGER_IDENTIFIER_EXTRACTOR;
            }
            if (integralType == BigDecimal.class) {
                return BIG_DECIMAL_IDENTIFIER_EXTRACTOR;
            }
            throw new IdentifierGenerationException("unsupported integral type: " + integralType);
        }

    }

    @Override
    public String toString() {
        // for debugging only
        return this.getClass().getSimpleName() + '(' + this.getSequenceName() + ')';
    }

}
