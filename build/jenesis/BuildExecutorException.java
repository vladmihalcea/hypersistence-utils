package build.jenesis;

import module java.base;

public class BuildExecutorException extends CompletionException {

    public BuildExecutorException(String identity, Throwable cause) {
        super("Failed to execute " + identity, cause);
    }
}
