package io.hypersistence.utils.logging;

import net.ttddyy.dsproxy.ExecutionInfo;
import net.ttddyy.dsproxy.QueryInfo;
import net.ttddyy.dsproxy.listener.logging.DefaultQueryLogEntryCreator;

import java.util.*;

/**
 * @author Vlad Mihalcea
 */
public class InlineQueryLogEntryCreator extends DefaultQueryLogEntryCreator {
    @Override
    protected void writeParamsEntry(StringBuilder sb, ExecutionInfo execInfo, List<QueryInfo> queryInfoList) {
        sb.append("Params:[");
        for (QueryInfo queryInfo : queryInfoList) {
            boolean firstArg = true;
            for (Map<String, Object> paramMap : queryInfo.getQueryArgsList()) {

                if (!firstArg) {
                    sb.append(", ");
                } else {
                    firstArg = false;
                }

                SortedMap<String, Object> sortedParamMap = new TreeMap<String, Object>(new CustomStringAsIntegerComparator());
                sortedParamMap.putAll(paramMap);

                sb.append("(");
                boolean firstParam = true;
                for (Map.Entry<String, Object> paramEntry : sortedParamMap.entrySet()) {
                    if (!firstParam) {
                        sb.append(", ");
                    } else {
                        firstParam = false;
                    }
                    Object parameter = paramEntry.getValue();
                    if (parameter != null && parameter.getClass().isArray()) {
                        sb.append(arrayToString(parameter));
                    } else {
                        sb.append(parameter);
                    }
                }
                sb.append(")");
            }
        }
        sb.append("]");
    }

    private String arrayToString(Object object) {
        if (object.getClass().isArray()) {
            if (object instanceof byte[]) {
                return Arrays.toString((byte[]) object);
            }
            if (object instanceof short[]) {
                return Arrays.toString((short[]) object);
            }
            if (object instanceof char[]) {
                return Arrays.toString((char[]) object);
            }
            if (object instanceof int[]) {
                return Arrays.toString((int[]) object);
            }
            if (object instanceof long[]) {
                return Arrays.toString((long[]) object);
            }
            if (object instanceof float[]) {
                return Arrays.toString((float[]) object);
            }
            if (object instanceof double[]) {
                return Arrays.toString((double[]) object);
            }
            if (object instanceof boolean[]) {
                return Arrays.toString((boolean[]) object);
            }
            if (object instanceof Object[]) {
                return Arrays.toString((Object[]) object);
            }
        }
        throw new UnsupportedOperationException("Array type not supported: " + object.getClass());
    }

    private static class CustomStringAsIntegerComparator extends StringAsIntegerComparator {
    }
}
