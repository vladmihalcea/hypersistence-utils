package build.jenesis;

import module java.base;

public record BuildStepContext(Path previous, Path next, Path supplement, Path persistent) {
}
