package io.hypersistence.utils.hibernate.query;

import org.hibernate.query.ResultListTransformer;

import java.util.List;
import java.util.stream.Collectors;

/**
 * The {@link DistinctListTransformer} removes duplicates from the {@link List}
 * of elements that were transformed by the {@link org.hibernate.query.TupleTransformer}.
 * <p>
 * This is similar to the {@code DistinctResultTransformer} that was available in Hibernate 5.
 *
 * @author Vlad Mihalcea
 * @since 2.21.0
 */
public class DistinctListTransformer<T> implements ResultListTransformer<T> {

    public static final DistinctListTransformer INSTANCE = new DistinctListTransformer();

    /**
     * Deduplicates the provided List.
     *
     * @param collection collections to be deduplicated
     * @return deduplicated List
     */
    @Override
    public List<T> transformList(List collection) {
        return (List<T>) collection.stream().distinct().collect(Collectors.toList());
    }
}
