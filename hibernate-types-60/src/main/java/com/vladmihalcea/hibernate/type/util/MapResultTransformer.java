package com.vladmihalcea.hibernate.type.util;

import java.util.Map;

/**
 * The {@link com.vladmihalcea.hibernate.type.util.MapResultTransformer} allows us to return
 * a {@link Map} from a JPA {@link jakarta.persistence.Query}.
 * <p>
 * If there are aliases named as {@code key} or {@code value},
 * then those will be used.
 * <p>
 * Otherwise, the first column value is the key while the second one is the Map value.
 * <p>
 * For more details about how to use it, check out <a href="https://vladmihalcea.com/jpa-query-map-result/">this article</a> on <a href="https://vladmihalcea.com/">vladmihalcea.com</a>.
 *
 * @deprecated use {@link com.vladmihalcea.hibernate.query.MapResultTransformer} instead
 *
 * @author Vlad Mihalcea
 * @since 2.9.0
 */
@Deprecated
public class MapResultTransformer<K, V> extends com.vladmihalcea.hibernate.query.MapResultTransformer {

    public static final String KEY_ALIAS = "map_key";

    public static final String VALUE_ALIAS = "map_value";
}
