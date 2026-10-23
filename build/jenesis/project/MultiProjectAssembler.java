package build.jenesis.project;

import module java.base;
import build.jenesis.Repository;
import build.jenesis.Resolver;

@FunctionalInterface
public interface MultiProjectAssembler<D extends ProjectModule> {

    AssemblyDescriptor apply(D descriptor,
                             Map<String, Repository> repositories,
                             Map<String, Resolver> resolvers) throws IOException;
}
