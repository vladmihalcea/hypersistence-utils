package build.jenesis;

import module java.base;

@FunctionalInterface
public interface RepositoryItem {

    default Optional<Path> file() {
        return Optional.empty();
    }

    default boolean internal() {
        return false;
    }

    default boolean local() {
        return false;
    }

    InputStream toInputStream() throws IOException;

    default RepositoryItem spill(Path target) throws IOException {
        if (!Files.exists(target)) {
            Path temporary = Files.createTempFile(target.getParent(), "spill", ".tmp");
            try (InputStream inputStream = toInputStream()) {
                Files.copy(inputStream, temporary, StandardCopyOption.REPLACE_EXISTING);
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (FileAlreadyExistsException _) {
                Files.deleteIfExists(temporary);
            } catch (Throwable t) {
                Files.deleteIfExists(temporary);
                throw t;
            }
        }
        return ofFile(target);
    }

    static RepositoryItem ofFile(Path file) {
        return ofFile(file, false);
    }

    static RepositoryItem ofLocalFile(Path file) {
        return new RepositoryItem() {
            @Override
            public Optional<Path> file() {
                return Optional.of(file);
            }

            @Override
            public boolean local() {
                return true;
            }

            @Override
            public InputStream toInputStream() throws IOException {
                return Files.newInputStream(file);
            }
        };
    }

    static RepositoryItem ofFile(Path file, boolean internal) {
        return new RepositoryItem() {
            @Override
            public Optional<Path> file() {
                return Optional.of(file);
            }

            @Override
            public boolean internal() {
                return internal;
            }

            @Override
            public InputStream toInputStream() throws IOException {
                return Files.newInputStream(file);
            }
        };
    }
}
