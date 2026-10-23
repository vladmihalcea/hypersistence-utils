package build.jenesis;

import module java.base;

@FunctionalInterface
public interface BuildExecutorCallback {

    BiConsumer<Boolean, Throwable> step(String identity, SequencedSet<String> keys);

    default void run(String run) {
    }

    default Consumer<Throwable> module(String identity) {
        return _ -> {
        };
    }

    default void loaded(String identity, long duration) {
    }

    default void stored(String identity, long duration) {
    }

    default BuildExecutorCallback andThen(BuildExecutorCallback other) {
        BuildExecutorCallback first = this;
        return new BuildExecutorCallback() {
            @Override
            public BiConsumer<Boolean, Throwable> step(String identity, SequencedSet<String> keys) {
                return first.step(identity, keys).andThen(other.step(identity, keys));
            }

            @Override
            public void run(String run) {
                first.run(run);
                other.run(run);
            }

            @Override
            public Consumer<Throwable> module(String identity) {
                return first.module(identity).andThen(other.module(identity));
            }

            @Override
            public void loaded(String identity, long duration) {
                first.loaded(identity, duration);
                other.loaded(identity, duration);
            }

            @Override
            public void stored(String identity, long duration) {
                first.stored(identity, duration);
                other.stored(identity, duration);
            }
        };
    }

    static BuildExecutorCallback nop() {
        return (_, _) -> (_, _) -> {
        };
    }

    static BuildExecutorCallback printing(Consumer<String> out,
                                          Palette palette,
                                          boolean verbose,
                                          boolean cache,
                                          Path target) {
        return printing(out, palette, verbose, cache, target, false);
    }

    static BuildExecutorCallback printing(Consumer<String> out,
                                          Palette palette,
                                          boolean verbose,
                                          boolean cache,
                                          Path target,
                                          boolean events) {
        return new BuildExecutorCallback() {
            @Override
            public BiConsumer<Boolean, Throwable> step(String identity, SequencedSet<String> keys) {
                long started = System.nanoTime();
                if (identity == null) {
                    out.accept("%s%-11s%s Building in '%s'...".formatted(
                            palette.status(), "[STARTED]", palette.reset(), target));
                    if (events) {
                        out.accept("%s%-11s%s Recording each step's outcome as a JSON line in '%s'".formatted(
                                palette.status(), "[EVENTS]", palette.reset(), target.resolve(BuildExecutor.EVENTS)));
                        out.accept("%s%-11s%s A coding agent passes the selector skill/start for its briefing on this build"
                                .formatted(palette.status(), "[AGENTS]", palette.reset()));
                    }
                    return (_, throwable) -> {
                        double time = ((double) (System.nanoTime() - started) / 1_000_000) / 1_000;
                        out.accept("%s%-11s%s Finished %sin %.2f seconds%s".formatted(
                                throwable == null ? palette.status() : palette.failure(),
                                throwable == null ? "[COMPLETED]" : "[FAILED]",
                                palette.reset(),
                                palette.detail(),
                                time,
                                palette.reset()));
                    };
                }
                return (executed, throwable) -> {
                    if (throwable != null) {
                        out.accept("%s%-11s%s %s: %s".formatted(palette.failure(), "[FAILED]", palette.reset(), identity,
                                throwable instanceof BuildExecutorException
                                        ? throwable.getCause().getMessage()
                                        : throwable.getMessage()));
                    } else if (executed) {
                        double time = ((double) (System.nanoTime() - started) / 1_000_000) / 1_000;
                        synchronized (out) {
                            out.accept("%s%-11s%s %s %sin %.2f seconds%s".formatted(
                                    palette.status(), "[EXECUTED]", palette.reset(),
                                    identity, palette.detail(), time, palette.reset()));
                            if (verbose) {
                                Path checksums = target.resolve(identity)
                                        .resolve("checksum")
                                        .resolve("output.properties");
                                if (Files.isRegularFile(checksums)) {
                                    try {
                                        HashFunction.read(checksums).forEach((file, hash) -> out.accept(
                                                "            %s  %s".formatted(
                                                        HexFormat.of().formatHex(hash),
                                                        file)));
                                    } catch (IOException e) {
                                        out.accept("            Failed to list files: %s".formatted(e.getMessage()));
                                    }
                                }
                            }
                        }
                    } else {
                        out.accept("%s%-11s%s %s".formatted(palette.skipped(), "[SKIPPED]", palette.reset(), identity));
                    }
                };
            }

            @Override
            public Consumer<Throwable> module(String identity) {
                long started = System.nanoTime();
                return throwable -> {
                    if (throwable == null) {
                        double time = ((double) (System.nanoTime() - started) / 1_000_000) / 1_000;
                        out.accept("%s%-11s%s %s %sin %.2f seconds%s".formatted(
                                palette.status(), "[RESOLVED]", palette.reset(),
                                identity, palette.detail(), time, palette.reset()));
                    }
                };
            }

            @Override
            public void loaded(String identity, long duration) {
                if (cache) {
                    out.accept("%s%-11s%s %s %sin %.2f seconds%s".formatted(
                            palette.info(), "[LOADED]", palette.reset(),
                            identity, palette.detail(), ((double) duration / 1_000_000) / 1_000, palette.reset()));
                }
            }

            @Override
            public void stored(String identity, long duration) {
                if (cache) {
                    out.accept("%s%-11s%s %s %sin %.2f seconds%s".formatted(
                            palette.info(), "[STORED]", palette.reset(),
                            identity, palette.detail(), ((double) duration / 1_000_000) / 1_000, palette.reset()));
                }
            }
        };
    }

    static BuildExecutorCallback events(Path target) {
        Path root = target.toAbsolutePath().normalize(),
                file = root.resolve(BuildExecutor.EVENTS),
                directory = Path.of("").toAbsolutePath();
        return new BuildExecutorCallback() {

            private Writer writer;
            private String run;
            private long begun;
            private int executed, skipped, failed;

            @Override
            public void run(String run) {
                this.run = run;
            }

            @Override
            public BiConsumer<Boolean, Throwable> step(String identity, SequencedSet<String> keys) {
                long started = System.nanoTime();
                if (identity == null) {
                    synchronized (this) {
                        close();
                        try {
                            writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8);
                        } catch (IOException e) {
                            throw new UncheckedIOException("Cannot write the build events to " + file, e);
                        }
                        begun = started;
                        executed = 0;
                        skipped = 0;
                        failed = 0;
                        write("{\"status\":\"started\",\"target\":\"" + Json.escaped(root.toString())
                                + "\",\"directory\":\"" + Json.escaped(directory.toString()) + "\""
                                + (run == null ? "" : ",\"run\":\"" + Json.escaped(run) + "\"") + "}");
                    }
                    return (_, throwable) -> {
                        synchronized (this) {
                            write("{\"status\":\"" + (throwable == null ? "completed" : "failed") + "\""
                                    + ",\"seconds\":" + seconds(System.nanoTime() - started)
                                    + ",\"executed\":" + executed
                                    + ",\"skipped\":" + skipped
                                    + ",\"failed\":" + failed
                                    + (throwable == null ? "" : failure(throwable)) + "}");
                            close();
                        }
                    };
                }
                String step = ",\"step\":\"" + Json.escaped(identity) + "\"" + start(started),
                        folder = ",\"folder\":\"" + Json.escaped(root.resolve(identity).toString()) + "\"";
                Path local = root.resolve(identity).resolve(BuildExecutor.LOCAL),
                        next = root.resolve(identity + BuildExecutor.NEXT);
                return (ran, throwable) -> {
                    synchronized (this) {
                        if (throwable != null) {
                            failed++;
                            write("{\"status\":\"failed\"" + step + failure(throwable)
                                    + (Files.exists(next.resolve(BuildExecutor.FAILED_MARKER))
                                    ? ",\"folder\":\"" + Json.escaped(next.toString()) + "\""
                                    : "") + "}");
                        } else if (ran) {
                            executed++;
                            write("{\"status\":\"executed\"" + step + ",\"seconds\":"
                                    + seconds(System.nanoTime() - started) + folder + "}");
                        } else {
                            skipped++;
                            String producer = null;
                            if (Files.isRegularFile(local)) {
                                try {
                                    producer = SequencedProperties.ofFiles(local).value("run");
                                } catch (IOException _) {
                                }
                            }
                            write("{\"status\":\"skipped\"" + step + folder
                                    + (producer == null ? "" : ",\"run\":\"" + Json.escaped(producer) + "\"") + "}");
                        }
                    }
                };
            }

            @Override
            public Consumer<Throwable> module(String identity) {
                long started = System.nanoTime();
                return throwable -> write("{\"status\":\"" + (throwable == null ? "resolved" : "failed") + "\""
                        + ",\"module\":\"" + Json.escaped(identity) + "\"" + start(started)
                        + (throwable == null
                        ? ",\"seconds\":" + seconds(System.nanoTime() - started)
                        : failure(throwable)) + "}");
            }

            @Override
            public void loaded(String identity, long duration) {
                write("{\"status\":\"loaded\",\"step\":\"" + Json.escaped(identity) + "\",\"seconds\":"
                        + seconds(duration) + "}");
            }

            @Override
            public void stored(String identity, long duration) {
                write("{\"status\":\"stored\",\"step\":\"" + Json.escaped(identity) + "\",\"seconds\":"
                        + seconds(duration) + "}");
            }

            private synchronized String start(long started) {
                return ",\"start\":" + seconds(started - begun);
            }

            private synchronized void write(String line) {
                if (writer == null) {
                    return;
                }
                try {
                    writer.write(line);
                    writer.write('\n');
                    writer.flush();
                } catch (IOException e) {
                    throw new UncheckedIOException("Cannot write the build events to " + file, e);
                }
            }

            private synchronized void close() {
                if (writer == null) {
                    return;
                }
                try {
                    writer.close();
                } catch (IOException e) {
                    throw new UncheckedIOException("Cannot write the build events to " + file, e);
                } finally {
                    writer = null;
                }
            }
        };
    }

    private static String seconds(long nanos) {
        return String.format(Locale.ROOT, "%.3f", nanos / 1_000_000_000d);
    }

    private static String failure(Throwable throwable) {
        Throwable cause = throwable;
        while ((cause instanceof BuildExecutorException || cause instanceof CompletionException)
                && cause.getCause() != null) {
            cause = cause.getCause();
        }
        return ",\"error\":\"" + cause.getClass().getName() + "\",\"message\":\""
                + Json.escaped(cause.getMessage() == null ? cause.toString() : cause.getMessage()) + "\"";
    }
}
