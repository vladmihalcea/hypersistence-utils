package io.hypersistence.utils.hibernate.query;

import org.hibernate.HibernateException;
import org.hibernate.transform.ResultTransformer;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The {@link MapResultTransformer} allows us to return
 * a {@link Map} from a JPA {@link jakarta.persistence.Query}.
 * <p>
 * If there are aliases named as {@code key} or {@code value},
 * then those will be used.
 * <p>
 * Otherwise, the first column value is the key while the second one is the Map value.
 * <p>
 * For more details about how to use it, check out <a href="https://vladmihalcea.com/jpa-query-map-result/">this article</a> on <a href="https://vladmihalcea.com/">vladmihalcea.com</a>.
 *
 * @author Vlad Mihalcea
 * @since 2.9.0
 */
public class MapResultTransformer<K, V> implements ResultTransformer {

    public static final String KEY_ALIAS = "map_key";

    public static final String VALUE_ALIAS = "map_value";

    private Map<K, V> result = new HashMap<>();

    /**
     * Transform the tuple into a key/value pair.
     * <p>
     * If there are aliases named as {@code key} or {@code value},
     * then those will be used.
     * <p>
     * Otherwise, the first column value is the key while the second one is the Map value.
     *
     * @param tuple   tuple to be transformed to a key/value pair
     * @param aliases column aliases
     * @return unmodified tuple
     */
    @Override
    public Object transformTuple(Object[] tuple, String[] aliases) {
        int keyOrdinal = -1;
        int valueOrdinal = -1;

        for (int i = 0; i < aliases.length; i++) {
            String alias = aliases[i];
            if (KEY_ALIAS.equalsIgnoreCase(alias)) {
                keyOrdinal = i;
            } else if (VALUE_ALIAS.equalsIgnoreCase(alias)) {
                valueOrdinal = i;
            }
        }

        if (keyOrdinal >= 0) {
            if (valueOrdinal < 0) {
                throw new HibernateException(
                    new IllegalArgumentException("A key column alias was given but no value column alias was found!")
                );
            }
        } else {
            if (valueOrdinal >= 0) {
                throw new HibernateException(
                    new IllegalArgumentException("A value column alias was given but no key column alias was found!")
                );
            } else {
                keyOrdinal = 0;
                valueOrdinal = 1;
            }
        }

        @SuppressWarnings("unchecked")
        K key = (K) tuple[keyOrdinal];
        @SuppressWarnings("unchecked")
        V value = (V) tuple[valueOrdinal];
        result.put(key, value);

        return tuple;
    }

    /**
     * Return the {@link Map} instead of the default {@link List}.
     *
     * @param tuples tuples
     * @return the {@link Map} result set
     */
    @Override
    public List transformList(List tuples) {
        return Collections.singletonList(result);
    }
}
