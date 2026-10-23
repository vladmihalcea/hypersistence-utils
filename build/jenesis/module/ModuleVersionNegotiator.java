package build.jenesis.module;

import module java.base;

@FunctionalInterface
public interface ModuleVersionNegotiator extends Serializable {

    CompiledVersion negotiate(String module, CompiledVersion recorded, CompiledVersion declared);

    default void discovered(String module, String version, boolean managed) {
    }

    record CompiledVersion(String version, String origin) implements Serializable {
    }

    @SuppressWarnings("unchecked")
    static <S extends Supplier<ModuleVersionNegotiator> & Serializable> S first() {
        return (S) (Supplier<ModuleVersionNegotiator> & Serializable)
                () -> (_, recorded, declared) -> recorded == null ? declared : recorded;
    }

    @SuppressWarnings("unchecked")
    static <S extends Supplier<ModuleVersionNegotiator> & Serializable> S ignore() {
        return (S) (Supplier<ModuleVersionNegotiator> & Serializable) () -> (_, _, _) -> null;
    }

    @SuppressWarnings("unchecked")
    static <S extends Supplier<ModuleVersionNegotiator> & Serializable> S fail() {
        return (S) (Supplier<ModuleVersionNegotiator> & Serializable) () -> ModuleVersionNegotiator::diverging;
    }

    @SuppressWarnings("unchecked")
    static <S extends Supplier<ModuleVersionNegotiator> & Serializable> S managed() {
        return (S) (Supplier<ModuleVersionNegotiator> & Serializable) () -> new ModuleVersionNegotiator() {
            @Override
            public CompiledVersion negotiate(String module, CompiledVersion recorded, CompiledVersion declared) {
                return diverging(module, recorded, declared);
            }

            @Override
            public void discovered(String module, String version, boolean managed) {
                if (!managed) {
                    throw new IllegalStateException("No version pinned for module "
                            + module
                            + " which resolved to "
                            + (version == null ? "the newest published version" : version)
                            + " as another module requires it"
                            + " (declare a @jenesis.pin for it, or run the pin selector)");
                }
            }
        };
    }

    private static CompiledVersion diverging(String module, CompiledVersion recorded, CompiledVersion declared) {
        if (recorded == null) {
            return declared;
        } else if (!recorded.version().equals(declared.version())) {
            throw new IllegalStateException("Conflicting compiled versions for module " + module + ": "
                    + recorded.origin() + " requires " + recorded.version()
                    + ", " + declared.origin() + " requires " + declared.version());
        }
        return recorded;
    }
}
