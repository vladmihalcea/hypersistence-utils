package build.jenesis;

import module java.base;

@FunctionalInterface
public interface ObservabilityFactory {

    Observation create(Environment environment,
                       SequencedMap<String, String> properties,
                       Permits permits,
                       String group,
                       Map<String, Repository> repositories,
                       Map<String, Resolver> resolvers) throws IOException;
}
