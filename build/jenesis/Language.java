package build.jenesis;

import module java.base;

public record Language(SequencedSet<String> extensions,
                       Order order,
                       BuildExecutorModule compiler,
                       BuildExecutorModule documentation) {

    public enum Order {

        BEFORE_JAVAC, AFTER_JAVAC
    }
}
