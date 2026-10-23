package build.jenesis;

import java.io.IOException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;
import java.util.SequencedMap;
import java.util.stream.Collectors;

@FunctionalInterface
public interface BuildExecutorModule {

    String PREVIOUS = "../";

    static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8)
                .replace("+", "%20")
                .replace("*", "%2A");
    }

    static String encodePath(String path) {
        return Arrays.stream(path.split("/", -1))
                .map(BuildExecutorModule::encode)
                .collect(Collectors.joining("+"));
    }

    static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    default Optional<String> resolve(String path) {
        return Optional.of(path);
    }

    void accept(BuildExecutor buildExecutor, SequencedMap<String, Path> inherited) throws IOException;
}
