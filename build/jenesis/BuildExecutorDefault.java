package build.jenesis;

import module java.base;

class BuildExecutorDefault implements BuildExecutor {

    private static final Pattern
            VALIDATE_ORIGINAL = Pattern.compile("[a-zA-Z0-9._%-][a-zA-Z0-9._%+-]*"),
            VALIDATE_RESOLVED = Pattern.compile("[a-zA-Z0-9./_%+-]+");
    private static final String PERSISTENT = "persistent";

    private static final ConcurrentMap<Path, FileChannel> LOCKS = new ConcurrentHashMap<>();

    private final Path target;
    private final Duration timeout;
    private final HashDigestFunction hash;
    private final BuildStepHashFunction stepHash;
    private final BuildExecutorCallback callback;
    private final BuildExecutorCache cache;
    private final boolean aggregate;
    private final Permits permits;
    private final String run;
    private final String location;

    private final Map<String, StepSummary> inherited;
    private final SequencedMap<String, Registration> registrations = new LinkedHashMap<>();

    BuildExecutorDefault(Path target,
                         Duration timeout,
                         HashDigestFunction hash,
                         BuildStepHashFunction stepHash,
                         BuildExecutorCallback callback,
                         BuildExecutorCache cache,
                         boolean aggregate,
                         Permits permits,
                         String run,
                         String location,
                         Map<String, StepSummary> inherited) throws IOException {
        this.target = Files.isDirectory(target) ? target : Files.createDirectory(target);
        this.timeout = timeout;
        this.hash = hash;
        this.stepHash = stepHash;
        this.callback = callback;
        this.cache = cache;
        this.aggregate = aggregate;
        this.permits = permits;
        this.run = run;
        this.location = location;
        this.inherited = inherited;
    }

    @Override
    public void addSource(String identity, Path path) {
        add(identity, bindSource(path), Map.of());
    }

    @Override
    public void addSource(String identity, BuildStep step, SequencedSet<Path> paths) {
        add(identity, bindStep(step).summaries(hash, paths), Map.of());
    }

    @Override
    public void replaceSource(String identity, Path path) {
        replace(identity, bindSource(path));
    }

    @Override
    public void replaceSource(String identity, BuildStep step, SequencedSet<Path> paths) {
        replace(identity, bindStep(step).summaries(hash, paths));
    }

    private Bound bindSource(Path path) {
        return (identity, executor, _, selectors) -> {
            if (!selectors.isEmpty()) {
                selectors.stream().filter(selector -> !selector.lenient()).findFirst().ifPresent(selector -> {
                    throw new IllegalArgumentException("Unknown selector: " + selector.path()
                            + " - " + identity + " is a source, which holds no steps to select");
                });
                return CompletableFuture.completedStage(Map.of(identity, Map.of()));
            }
            CompletableFuture<Map<String, Map<String, StepSummary>>> future = new CompletableFuture<>();
            executor.execute(() -> {
                try {
                    future.complete(Map.of(identity, Map.of(
                            identity,
                            new StepSummary(path, HashFunction.read(path, hash, executor)))));
                } catch (Throwable t) {
                    future.completeExceptionally(t);
                }
            });
            return future;
        };
    }

    @Override
    public void addStep(String identity, BuildStep step, SequencedMap<String, String> dependencies) {
        add(identity, bindStep(step), dependencies);
    }

    @Override
    public void replaceStep(String identity, BuildStep step) {
        replace(identity, bindStep(step));
    }

    @Override
    public void prependStep(String identity, String prepended, BuildStep step) {
        prepend(identity, prepended, bindStep(step));
    }

    @Override
    public void appendStep(String identity, String original, BuildStep step) {
        append(identity, original, bindStep(step));
    }

    private Bound bindStep(BuildStep step) {
        return (identity, executor, summaries, selectors) -> {
            try {
                if (!selectors.isEmpty()) {
                    selectors.stream().filter(selector -> !selector.lenient()).findFirst().ifPresent(selector -> {
                        throw new IllegalArgumentException("Unknown selector: " + selector.path()
                                + " - " + identity + " is a step, which holds no steps to select");
                    });
                    return CompletableFuture.completedStage(Map.of(identity, Map.of()));
                }
                StepFolder previous = StepFolder.of(target.resolve(identity));
                boolean exists = Files.exists(previous.path());
                byte[] currentStepHash = stepHash.hash(step);
                StepRecord record = exists
                        ? completed(previous, currentStepHash, executor)
                        : StepRecord.INCOMPLETE;
                Map<Path, byte[]> current = record.checksums();
                boolean consistent = record.consistent();
                SequencedMap<String, BuildStepArgument> arguments = new LinkedHashMap<>();
                SequencedMap<String, Map<Path, byte[]>> inputs = new LinkedHashMap<>();
                SequencedMap<String, Path> recorded = exists ? previous.arguments() : Collections.emptyNavigableMap();
                for (Map.Entry<String, StepSummary> entry : summaries.entrySet()) {
                    Path checksums = recorded.get(entry.getKey());
                    Map<Path, byte[]> argumentChecksums = entry.getValue().checksums();
                    inputs.put(entry.getKey(), argumentChecksums);
                    arguments.put(entry.getKey(), new BuildStepArgument(
                            entry.getValue().folder(),
                            consistent && checksums != null && Files.exists(checksums)
                                    ? Checksum.diff(HashFunction.read(checksums), argumentChecksums, hash)
                                    : Checksum.added(argumentChecksums, hash)));
                }
                SequencedMap<String, Set<Path>> vanished = consistent
                        ? previous.vanished(recorded, summaries.keySet())
                        : Collections.emptyNavigableMap();
                vanished.forEach((key, paths) -> arguments.put(
                        key,
                        new BuildStepArgument(null, Checksum.removed(paths))));
                BiConsumer<Boolean, Throwable> completion = callback.step(
                        location + identity,
                        new LinkedHashSet<>(summaries.keySet()));
                boolean cacheRemotely = step.shouldCacheRemotely();
                Path next = target.resolve(identity + BuildExecutor.NEXT);
                if (Files.exists(next)) {
                    Files.walkFileTree(next, new RecursiveFolderDeletion(null));
                }
                if (!consistent || step.shouldRun(arguments)) {
                    Files.createDirectory(next);
                    Path nextOutput = Files.createDirectory(next.resolve("output"));
                    Path nextSupplement = Files.createDirectory(next.resolve("supplement"));
                    long fetchStarted = System.nanoTime();
                    Optional<BuildStepResult> cached = cache.fetch(executor,
                            location + identity,
                            currentStepHash,
                            inputs,
                            cacheRemotely,
                            nextOutput);
                    boolean fromCache = cached.isPresent();
                    if (fromCache) {
                        callback.loaded(location + identity, System.nanoTime() - fetchStarted);
                    }
                    Path nextPersistent = next.resolve(PERSISTENT);
                    if (!fromCache && consistent && Files.isDirectory(previous.persistent())) {
                        Files.move(previous.persistent(), nextPersistent);
                    } else {
                        Files.createDirectory(nextPersistent);
                    }
                    CompletionStage<BuildStepResult> stepStage;
                    if (fromCache) {
                        stepStage = CompletableFuture.completedStage(cached.get());
                    } else {
                        BuildStepContext context = new BuildStepContext(
                                consistent ? previous.output() : null,
                                nextOutput,
                                nextSupplement,
                                nextPersistent);
                        if (permits == null) {
                            try {
                                stepStage = step.apply(executor, context, arguments);
                            } catch (Throwable t) {
                                stepStage = CompletableFuture.failedStage(t);
                            }
                        } else {
                            stepStage = permits.acquire().thenComposeAsync(_ -> {
                                try {
                                    return step.apply(executor, context, arguments);
                                } catch (Throwable t) {
                                    return CompletableFuture.failedStage(t);
                                }
                            }, executor).whenCompleteAsync((_, _) -> permits.release(), executor);
                        }
                        if (!timeout.isZero()) {
                            stepStage = stepStage.toCompletableFuture().orTimeout(
                                    timeout.toNanos(),
                                    TimeUnit.NANOSECONDS);
                        }
                    }
                    return stepStage.thenComposeAsync(result -> {
                        try {
                            if (result.next()) {
                                Files.move(next, exists
                                        ? Files.walkFileTree(previous.path(), new RecursiveFolderDeletion(null))
                                        : previous.path());
                                try (Stream<Path> kept = Files.list(previous.persistent())) {
                                    if (kept.findAny().isEmpty()) {
                                        Files.delete(previous.persistent());
                                    }
                                }
                                Files.createDirectory(previous.checksum());
                                SequencedProperties local = new SequencedProperties();
                                local.setProperty("run", run);
                                local.setProperty("cached", Boolean.toString(fromCache));
                                local.store(previous.path().resolve(BuildExecutor.LOCAL));
                            } else if (consistent) {
                                if (!Files.exists(previous.persistent())) {
                                    try (Stream<Path> kept = Files.list(nextPersistent)) {
                                        if (kept.findAny().isPresent()) {
                                            Files.move(nextPersistent, previous.persistent());
                                        }
                                    }
                                }
                                Files.delete(Files.walkFileTree(next, new RecursiveFolderDeletion(next)));
                                Files.deleteIfExists(previous.step());
                                Files.walkFileTree(previous.checksum(), new RecursiveFolderDeletion(previous.checksum()));
                            } else {
                                throw new IllegalStateException("Cannot reuse initial run for " + location + identity);
                            }
                            previous.record(summaries);
                            Map<Path, byte[]> checksums = HashFunction.read(previous.output(), hash, executor);
                            HashFunction.write(previous.checksums(), checksums);
                            SequencedProperties stepProperties = new SequencedProperties();
                            stepProperties.setProperty("serialization", HexFormat.of().formatHex(currentStepHash));
                            stepProperties.storeAtomically(previous.step());
                            if (cache.stores() && !fromCache && result.next()) {
                                String stored = location + identity;
                                try {
                                    executor.execute(() -> {
                                        long storeStarted = System.nanoTime();
                                        try {
                                            cache.store(executor,
                                                    location + identity,
                                                    currentStepHash,
                                                    inputs,
                                                    cacheRemotely,
                                                    previous.output(),
                                                    hash.algorithm(),
                                                    checksums);
                                        } catch (IOException _) {
                                        }
                                        callback.stored(stored, System.nanoTime() - storeStarted);
                                    });
                                } catch (RejectedExecutionException _) {
                                }
                            }
                            completion.accept(result.next(), null);
                            return CompletableFuture.completedStage(Map.of(
                                    identity,
                                    Map.of(identity, new StepSummary(previous.output(), checksums))));
                        } catch (Throwable t) {
                            return CompletableFuture.failedStage(new BuildExecutorException(location + identity, t));
                        }
                    }, executor).exceptionallyComposeAsync(t -> {
                        BuildExecutorException wrapped = switch (t) {
                            case BuildExecutorException e -> e;
                            case CompletionException e -> new BuildExecutorException(location + identity, e.getCause());
                            default -> new BuildExecutorException(location + identity, t);
                        };
                        try {
                            if (Files.isDirectory(next)) {
                                Files.createFile(next.resolve(BuildExecutor.FAILED_MARKER));
                            }
                        } catch (IOException e) {
                            wrapped.addSuppressed(e);
                        }
                        completion.accept(null, t);
                        return CompletableFuture.failedStage(wrapped);
                    }, executor);
                } else {
                    if (!vanished.isEmpty()) {
                        previous.forget(recorded, vanished.keySet());
                    }
                    completion.accept(false, null);
                    try {
                        cache.touch(executor, location + identity, currentStepHash, inputs, cacheRemotely);
                    } catch (IOException _) {
                    }
                    return CompletableFuture.completedStage(Map.of(identity, Map.of(
                            identity,
                            new StepSummary(previous.output(), current))));
                }
            } catch (Throwable t) {
                return CompletableFuture.failedFuture(new BuildExecutorException(location + identity, t));
            }
        };
    }

    @Override
    public void addModule(String identity,
                          BuildExecutorModule module,
                          Function<String, Optional<String>> resolver,
                          SequencedMap<String, String> dependencies) {
        add(identity, bindModule(module, resolver), dependencies);
    }

    @Override
    public void replaceModule(String identity,
                              BuildExecutorModule module,
                              Function<String, Optional<String>> resolver) {
        replace(identity, bindModule(module, resolver));
    }

    @Override
    public void prependModule(String identity,
                              String prepended,
                              BuildExecutorModule module,
                              Function<String, Optional<String>> resolver) {
        prepend(identity, prepended, bindModule(module, resolver));
    }

    @Override
    public void appendModule(String identity,
                             String appended,
                             BuildExecutorModule module,
                             Function<String, Optional<String>> resolver) {
        append(identity, appended, bindModule(module, resolver));
    }

    private Bound bindModule(BuildExecutorModule module, Function<String, Optional<String>> resolver) {
        return new Bound() {
            @Override
            public boolean module() {
                return true;
            }

            @Override
            public CompletionStage<Map<String, Map<String, StepSummary>>> apply(String prefix,
                                                                                Executor executor,
                                                                                Map<String, StepSummary> summaries,
                                                                                Set<Selector> selectors) {
                Consumer<Throwable> resolution = callback.module(location + prefix);
                try {
                    SequencedMap<String, Path> folders = new LinkedHashMap<>();
                    SequencedMap<String, StepSummary> inherited = new LinkedHashMap<>();
                    for (Map.Entry<String, StepSummary> entry : summaries.entrySet()) {
                        String identity = BuildExecutorModule.PREVIOUS + entry.getKey();
                        folders.put(identity, entry.getValue().folder());
                        inherited.put(identity, entry.getValue());
                    }
                    BuildExecutorDefault buildExecutor = new BuildExecutorDefault(target.resolve(prefix),
                            timeout,
                            hash,
                            stepHash,
                            callback,
                            cache,
                            aggregate,
                            permits,
                            run,
                            location + prefix + "/",
                            inherited);
                    module.accept(buildExecutor, folders);
                    resolution.accept(null);
                    return buildExecutor.doExecute(executor, selectors).thenComposeAsync(results -> {
                        try {
                            Map<String, StepSummary> prefixed = new LinkedHashMap<>();
                            results.forEach((identity, values) -> {
                                String resolved = module.resolve(identity).flatMap(resolver).orElse(null);
                                if (resolved != null && prefixed.putIfAbsent(
                                        resolved.isEmpty() ? prefix : prefix + "/" + validated(resolved, VALIDATE_RESOLVED),
                                        values) != null) {
                                    throw new IllegalArgumentException("Duplicate resolution " + resolved);
                                }
                            });
                            return CompletableFuture.completedStage(Map.of(prefix, prefixed));
                        } catch (Throwable t) {
                            return CompletableFuture.failedStage(new BuildExecutorException(location + prefix, t));
                        }
                    }, executor);
                } catch (Throwable t) {
                    resolution.accept(t);
                    return CompletableFuture.failedStage(new BuildExecutorException(location + prefix, t));
                }
            }
        };
    }

    private void add(String identity, Bound bound, Map<String, String> dependencies) {
        SequencedSet<String> preliminaries = new LinkedHashSet<>();
        Set<String> synonyms = new HashSet<>();
        dependencies.forEach((dependency, synonym) -> {
            if (!synonyms.add(synonym)) {
                throw new IllegalArgumentException("Duplicated synonym: " + synonym);
            }
            int index, limit = dependency.length();
            while ((index = dependency.lastIndexOf('/', limit - 1)) != -1) {
                if (dependencies.containsKey(dependency.substring(0, index))) {
                    throw new IllegalArgumentException("Redundant root dependency: " + dependency.substring(0, index));
                }
                limit = index;
            }
            if (dependency.startsWith(BuildExecutorModule.PREVIOUS)) {
                if (!inherited.containsKey(dependency)) {
                    throw new IllegalArgumentException("Did not inherit: " + dependency);
                }
            } else if (registrations.containsKey(dependency.substring(0, limit))) {
                preliminaries.add(dependency.substring(0, limit));
            } else {
                throw new IllegalArgumentException("Did not find dependency: " + dependency);
            }
        });
        if (registrations.putIfAbsent(
                validated(identity, VALIDATE_ORIGINAL),
                new Registration(bound, preliminaries, dependencies)) != null) {
            throw new IllegalArgumentException("Step already registered: " + identity);
        }
    }

    private void replace(String identity, Bound bound) {
        Registration registration = registrations.get(identity);
        if (registration == null) {
            throw new IllegalArgumentException("Unknown step: " + identity);
        }
        registrations.replace(identity, new Registration(bound,
                registration.preliminaries(),
                registration.dependencies()));
    }

    private void prepend(String identity, String prepended, Bound bound) {
        Registration registration = registrations.get(identity);
        if (registration == null) {
            throw new IllegalArgumentException("Unknown step: " + identity);
        }
        if (registrations.putIfAbsent(validated(prepended, VALIDATE_ORIGINAL), new Registration(bound,
                registration.preliminaries(),
                registration.dependencies())) != null) {
            throw new IllegalArgumentException("Step already registered: " + prepended);
        }
        registrations.replace(identity, new Registration(registration.bound(), new LinkedHashSet<>(Set.of(prepended)), Map.of(prepended, prepended)));
    }

    private void append(String identity, String appended, Bound bound) {
        Registration registration = registrations.get(identity);
        if (registration == null) {
            throw new IllegalArgumentException("Unknown step: " + identity);
        }
        if (registrations.putIfAbsent(validated(appended, VALIDATE_ORIGINAL), registration) != null) {
            throw new IllegalArgumentException("Step already registered: " + appended);
        }
        registrations.replace(identity, new Registration(bound, new LinkedHashSet<>(Set.of(appended)), Map.of(appended, appended)));
    }

    @Override
    public CompletionStage<SequencedMap<String, Path>> execute(Executor executor, String... selectors) {
        Set<Selector> initial = Arrays.stream(selectors)
                .map(s -> new Selector(s, false))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Path canonical = target.toAbsolutePath().normalize();
        FileChannel lock = lock(canonical);
        BiConsumer<Boolean, Throwable> completion;
        try {
            callback.run(run);
            completion = callback.step(null, registrations.sequencedKeySet());
        } catch (RuntimeException | Error e) {
            release(canonical, lock);
            throw e;
        }
        try {
            return doExecute(executor, initial).thenApplyAsync(summaries -> {
                SequencedMap<String, Path> translated = new LinkedHashMap<>();
                for (Map.Entry<String, StepSummary> entry : summaries.entrySet()) {
                    translated.put(entry.getKey(), entry.getValue().folder());
                }
                return translated;
            }, executor).whenComplete((_, throwable) -> {
                try {
                    completion.accept(null, throwable);
                } finally {
                    release(canonical, lock);
                }
            });
        } catch (RuntimeException | Error e) {
            try {
                completion.accept(null, e);
            } finally {
                release(canonical, lock);
            }
            throw e;
        }
    }

    private static FileChannel lock(Path canonical) {
        FileChannel channel;
        try {
            channel = FileChannel.open(canonical.resolve(LOCK_MARKER),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot open the build lock in " + canonical, e);
        }
        if (LOCKS.putIfAbsent(canonical, channel) != null) {
            close(channel);
            return null;
        }
        FileLock lock;
        try {
            lock = channel.tryLock();
        } catch (IOException e) {
            LOCKS.remove(canonical);
            close(channel);
            throw new UncheckedIOException("Cannot lock the build in " + canonical, e);
        }
        if (lock == null) {
            LOCKS.remove(canonical);
            close(channel);
            throw new IllegalStateException("Another build process is already building " + canonical
                    + ": concurrent builds over one target folder interfere with each other's staged steps");
        }
        return channel;
    }

    private static void release(Path canonical, FileChannel channel) {
        if (channel != null) {
            LOCKS.remove(canonical);
            close(channel);
        }
    }

    private static void close(FileChannel channel) {
        try {
            channel.close();
        } catch (IOException _) {
        }
    }

    private CompletionStage<Map<String, StepSummary>> doExecute(Executor executor, Set<Selector> selectors) {
        SequencedSet<String> scheduled = new LinkedHashSet<>();
        Set<String> pinned = new HashSet<>(), direct = new HashSet<>();
        Map<String, Set<Selector>> forwarded = new LinkedHashMap<>();
        if (selectors.isEmpty()) {
            scheduled.addAll(registrations.keySet());
        } else {
            Queue<Selector> queue = new ArrayDeque<>(selectors);
            while (!queue.isEmpty()) {
                Selector selector = queue.poll(), tail = selector.tail();
                String first = selector.first();
                if (first.equals(":") || first.equals("::")) {
                    scheduled.addAll(registrations.keySet());
                    if (tail == null) {
                        direct.addAll(registrations.keySet());
                        pinned.addAll(registrations.keySet());
                    } else {
                        boolean anyDepth = first.equals("::");
                        if (anyDepth) {
                            queue.add(tail.asLenient());
                        }
                        Selector descend = (anyDepth ? selector : tail).asLenient();
                        registrations.keySet().forEach(identity ->
                                forwarded.computeIfAbsent(identity, _ -> new LinkedHashSet<>())
                                        .add(descend));
                    }
                } else if (!registrations.containsKey(first)) {
                    if (!selector.lenient()) {
                        throw new IllegalArgumentException("Unknown selector: " + selector.path()
                                + " - " + (registrations.isEmpty()
                                ? "nothing is registered here"
                                : "expected one of " + registrations.sequencedKeySet()));
                    }
                } else {
                    scheduled.add(first);
                    pinned.add(first);
                    if (tail == null) {
                        direct.add(first);
                    } else {
                        forwarded.computeIfAbsent(first, _ -> new LinkedHashSet<>()).add(tail);
                    }
                }
            }
            ArrayDeque<String> prelimQueue = new ArrayDeque<>(pinned);
            for (String identity : scheduled) {
                if (registrations.get(identity).bound().module() && pinned.add(identity)) {
                    prelimQueue.add(identity);
                }
            }
            while (!prelimQueue.isEmpty()) {
                for (String preliminary : registrations.get(prelimQueue.poll()).preliminaries()) {
                    scheduled.add(preliminary);
                    direct.add(preliminary);
                    if (pinned.add(preliminary)) {
                        prelimQueue.add(preliminary);
                    }
                }
            }
            for (String identity : direct) {
                forwarded.remove(identity);
            }
        }
        CompletionStage<Map<String, Map<String, StepSummary>>> initial = CompletableFuture.completedStage(Map.of());
        SequencedMap<String, Registration> pending = new LinkedHashMap<>();
        for (Map.Entry<String, Registration> entry : registrations.entrySet()) {
            if (scheduled.contains(entry.getKey())) {
                pending.put(entry.getKey(), entry.getValue());
            }
        }
        SequencedMap<String, CompletionStage<Map<String, Map<String, StepSummary>>>> dispatched = new LinkedHashMap<>();
        while (!pending.isEmpty()) {
            Iterator<Map.Entry<String, Registration>> it = pending.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, Registration> entry = it.next();
                if (dispatched.keySet().containsAll(entry.getValue().preliminaries())) {
                    CompletionStage<Map<String, Map<String, StepSummary>>> completionStage = initial;
                    for (String dependency : entry.getValue().preliminaries()) {
                        completionStage = completionStage.thenCombineAsync(
                                dispatched.get(dependency),
                                (left, right) -> {
                                    SequencedMap<String, Map<String, StepSummary>> merged = new LinkedHashMap<>(left);
                                    merged.putAll(right);
                                    return merged;
                                },
                                executor);
                    }
                    dispatched.put(entry.getKey(), completionStage.thenComposeAsync(summaries -> {
                        try {
                            SequencedMap<String, StepSummary> propagated = new LinkedHashMap<>();
                            entry.getValue().dependencies().forEach((dependency, synonym) -> {
                                if (dependency.startsWith(BuildExecutorModule.PREVIOUS)) {
                                    propagated.put(synonym, inherited.get(dependency));
                                } else {
                                    int index = dependency.indexOf('/');
                                    if (index != -1) {
                                        StepSummary summary = summaries.getOrDefault(
                                                dependency.substring(0, index),
                                                Map.of()).get(dependency);
                                        if (summary == null) {
                                            throw new IllegalArgumentException("Did not find dependency: " + dependency);
                                        }
                                        propagated.put(synonym, summary);
                                    } else {
                                        summaries.getOrDefault(dependency, Map.of()).forEach((key, value) -> propagated.put(
                                                synonym + key.substring(dependency.length()),
                                                value));
                                    }
                                }
                            });
                            return entry.getValue().bound().apply(
                                    entry.getKey(),
                                    executor,
                                    propagated,
                                    forwarded.getOrDefault(entry.getKey(), Set.of()));
                        } catch (Throwable t) {
                            return CompletableFuture.failedStage(new BuildExecutorException(
                                    location + entry.getKey(),
                                    t));
                        }
                    }, executor));
                    it.remove();
                }
            }
        }
        if (!aggregate) {
            CompletionStage<Map<String, StepSummary>> result = CompletableFuture.completedStage(Map.of());
            for (String identity : scheduled) {
                result = result.thenCombineAsync(dispatched.get(identity), (left, right) -> {
                    SequencedMap<String, StepSummary> merged = new LinkedHashMap<>(left);
                    right.values().forEach(merged::putAll);
                    return merged;
                }, executor);
            }
            return result;
        }
        List<CompletableFuture<Map<String, Map<String, StepSummary>>>> futures = new ArrayList<>();
        for (String identity : scheduled) {
            futures.add(dispatched.get(identity).toCompletableFuture());
        }
        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).handleAsync((_, _) -> {
            Map<String, StepSummary> merged = new LinkedHashMap<>();
            CompletionException aggregate = null;
            Set<Throwable> reported = Collections.newSetFromMap(new IdentityHashMap<>());
            for (CompletableFuture<Map<String, Map<String, StepSummary>>> future : futures) {
                try {
                    future.join().values().forEach(merged::putAll);
                } catch (CompletionException e) {
                    if (!reported.add(e.getCause() == null ? e : e.getCause())) {
                        continue;
                    }
                    if (aggregate == null) {
                        aggregate = e;
                    } else {
                        aggregate.addSuppressed(e);
                    }
                }
            }
            if (aggregate != null) {
                throw aggregate;
            }
            return merged;
        }, executor);
    }

    private static String validated(String identity, Pattern pattern) {
        if (pattern.matcher(identity).matches()) {
            return identity;
        }
        throw new IllegalArgumentException(identity + " does not match pattern: " + pattern.pattern());
    }

    private interface Bound {

        CompletionStage<Map<String, Map<String, StepSummary>>> apply(String identity,
                                                                     Executor executor,
                                                                     Map<String, StepSummary> summaries,
                                                                     Set<Selector> selectors)
                throws IOException;

        default boolean module() {
            return false;
        }

        default Bound summaries(HashFunction hash, Set<Path> paths) {
            Bound delegate = this;
            return new Bound() {
                @Override
                public CompletionStage<Map<String, Map<String, StepSummary>>> apply(String identity,
                                                                                    Executor executor,
                                                                                    Map<String, StepSummary> summaries,
                                                                                    Set<Selector> selectors)
                        throws IOException {
                    SequencedMap<String, StepSummary> extended = new LinkedHashMap<>(summaries);
                    for (Path path : paths) {
                        String name = ":" + BuildExecutorModule.encode(Objects.toString(path.getFileName(), "")),
                                key = name;
                        for (int index = 1; extended.containsKey(key); index++) {
                            key = name + "-" + index;
                        }
                        extended.put(key, new StepSummary(path, HashFunction.read(path, hash, executor)));
                    }
                    return delegate.apply(identity, executor, extended, selectors);
                }

                @Override
                public boolean module() {
                    return delegate.module();
                }
            };
        }
    }

    private record Selector(String path, boolean lenient) {

        String first() {
            int slash = path.indexOf('/');
            return slash == -1 ? path : path.substring(0, slash);
        }

        Selector tail() {
            int slash = path.indexOf('/');
            return slash == -1 ? null : new Selector(path.substring(slash + 1), lenient);
        }

        Selector asLenient() {
            return lenient ? this : new Selector(path, true);
        }
    }

    private record Registration(Bound bound, SequencedSet<String> preliminaries, Map<String, String> dependencies) {
    }

    private record StepSummary(Path folder, Map<Path, byte[]> checksums) {
    }

    private record StepFolder(Path path, Path checksum, Path output, Path persistent, Path step, Path checksums) {

        private static final String ARGUMENT = "argument.", PROPERTIES = ".properties", ARGUMENTS = "arguments.properties";

        private static StepFolder of(Path path) {
            Path checksum = path.resolve("checksum");
            return new StepFolder(path,
                    checksum,
                    path.resolve("output"),
                    path.resolve(PERSISTENT),
                    checksum.resolve("step" + PROPERTIES),
                    checksum.resolve("output" + PROPERTIES));
        }

        SequencedMap<String, Path> arguments() throws IOException {
            SequencedMap<String, Path> arguments = new LinkedHashMap<>();
            Path index = checksum.resolve(ARGUMENTS);
            if (Files.isRegularFile(index)) {
                SequencedProperties.ofFiles(index).forEachProperty((key, file) -> arguments.put(key, checksum.resolve(file)));
            }
            return arguments;
        }

        void record(Map<String, StepSummary> summaries) throws IOException {
            SequencedProperties index = new SequencedProperties();
            for (Map.Entry<String, StepSummary> entry : new TreeMap<>(summaries).entrySet()) {
                String file = ARGUMENT + index.size() + PROPERTIES;
                HashFunction.write(checksum.resolve(file), entry.getValue().checksums());
                index.setProperty(entry.getKey(), file);
            }
            index.store(checksum.resolve(ARGUMENTS));
        }

        void forget(SequencedMap<String, Path> recorded, Set<String> keys) throws IOException {
            SequencedProperties index = new SequencedProperties();
            for (Map.Entry<String, Path> entry : recorded.entrySet()) {
                if (keys.contains(entry.getKey())) {
                    Files.deleteIfExists(entry.getValue());
                } else {
                    index.setProperty(entry.getKey(), checksum.relativize(entry.getValue()).toString());
                }
            }
            index.store(checksum.resolve(ARGUMENTS));
        }

        SequencedMap<String, Set<Path>> vanished(SequencedMap<String, Path> recorded, Set<String> declared)
                throws IOException {
            SequencedMap<String, Set<Path>> vanished = new TreeMap<>();
            for (Map.Entry<String, Path> entry : recorded.entrySet()) {
                if (!declared.contains(entry.getKey()) && Files.isRegularFile(entry.getValue())) {
                    vanished.put(entry.getKey(), HashFunction.read(entry.getValue()).keySet());
                }
            }
            return vanished;
        }
    }

    private record StepRecord(Map<Path, byte[]> checksums, boolean consistent) {

        private static final StepRecord INCOMPLETE = new StepRecord(Map.of(), false);
    }

    private StepRecord completed(StepFolder folder, byte[] step, Executor executor) {
        if (!Files.isRegularFile(folder.step())) {
            return StepRecord.INCOMPLETE;
        }
        try {
            String serialization = SequencedProperties.ofFiles(folder.step()).getProperty("serialization");
            if (serialization == null || !Arrays.equals(step, HexFormat.of().parseHex(serialization))) {
                return StepRecord.INCOMPLETE;
            }
            Map<Path, byte[]> checksums = HashFunction.read(folder.checksums());
            return new StepRecord(checksums, HashFunction.areConsistent(folder.output(), checksums, hash, executor));
        } catch (IOException | IllegalArgumentException _) {
            return StepRecord.INCOMPLETE;
        }
    }

    private static class RecursiveFolderDeletion extends SimpleFileVisitor<Path> {

        private final Path root;

        private RecursiveFolderDeletion(Path root) {
            this.root = root;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            Files.delete(file);
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
            if (!dir.equals(root)) {
                Files.delete(dir);
            }
            return FileVisitResult.CONTINUE;
        }
    }

    static final class Permits {

        private final Deque<CompletableFuture<Void>> waiting = new ArrayDeque<>();
        private int available;

        Permits(int available) {
            this.available = available;
        }

        CompletionStage<Void> acquire() {
            synchronized (this) {
                if (available > 0) {
                    available--;
                    return CompletableFuture.completedStage(null);
                }
                CompletableFuture<Void> future = new CompletableFuture<>();
                waiting.add(future);
                return future;
            }
        }

        void release() {
            CompletableFuture<Void> next;
            synchronized (this) {
                next = waiting.poll();
                if (next == null) {
                    available++;
                }
            }
            if (next != null) {
                next.complete(null);
            }
        }
    }
}
