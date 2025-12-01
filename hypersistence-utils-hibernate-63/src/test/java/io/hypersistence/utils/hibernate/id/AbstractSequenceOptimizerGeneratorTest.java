package io.hypersistence.utils.hibernate.id;

import io.hypersistence.utils.hibernate.util.AbstractTest;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import net.ttddyy.dsproxy.QueryCount;
import net.ttddyy.dsproxy.QueryCountHolder;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.dialect.Database;
import org.junit.Test;

import java.util.List;
import java.util.Properties;

import static org.junit.Assert.assertEquals;

/**
 * @author Vlad Mihalcea
 */
public abstract class AbstractSequenceOptimizerGeneratorTest<T> extends AbstractTest {

    private static final int BATCH_SIZE = 10;

    private final Class<T> postEntityClass;

    protected AbstractSequenceOptimizerGeneratorTest(Class<T> postEntityClass) {
        this.postEntityClass = postEntityClass;
    }

    @Override
    protected Class<?>[] entities() {
        return new Class<?>[]{
            postEntityClass
        };
    }

    @Override
    protected Database database() {
        return Database.POSTGRESQL;
    }

    @Override
    protected void additionalProperties(Properties properties) {
        properties.put(AvailableSettings.BATCH_VERSIONED_DATA, true);
        properties.put(AvailableSettings.STATEMENT_BATCH_SIZE, BATCH_SIZE);
        properties.put(AvailableSettings.ORDER_INSERTS, true);
        properties.put(AvailableSettings.ORDER_UPDATES, true);
    }

    @Test
    public void test() {
        QueryCountHolder.clear();
        doInJPA(entityManager -> {
            for (int i = 0; i < BATCH_SIZE; i++) {
                entityManager.persist(newPost(i));
            }
        });

        QueryCount queryCount = QueryCountHolder.getGrandTotal();
        assertEquals(1L, queryCount.getInsert());

        doInJPA(entityManager -> {
            CriteriaBuilder builder = entityManager.getCriteriaBuilder();
            CriteriaQuery<T> criteria = builder.createQuery(postEntityClass);
            criteria.from(postEntityClass);
            List<T> posts = entityManager.createQuery(criteria).getResultList();
            assertEquals(BATCH_SIZE, posts.size());
        });
    }

    protected abstract Object newPost(int index);
}
