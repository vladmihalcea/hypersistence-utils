package build.jenesis.module;

import module java.base;
import module jdk.compiler;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.BuildStepResult;
import build.jenesis.Environment;
import build.jenesis.HashDigestFunction;
import build.jenesis.Palette;
import build.jenesis.Permits;
import build.jenesis.Pinning;
import build.jenesis.Platform;
import build.jenesis.SequencedProperties;
import build.jenesis.step.Inventory;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;

public class PinModuleInfo implements BuildStep {

    private final transient Permits permits;
    private final String prefix;
    private final String path;
    private final List<Path> moduleInfoFiles;
    private final transient HashDigestFunction hashFunction;
    private final Platform platform;
    private final boolean checksum;
    private final boolean flatten;
    private final Retain retain;
    private final transient Consumer<String> printing;
    private final transient Palette palette;

    public PinModuleInfo(String prefix, String path, List<Path> moduleInfoFiles, HashDigestFunction hashFunction) {
        this(prefix,
                path,
                moduleInfoFiles,
                hashFunction,
                new Platform(),
                true,
                false,
                Retain.GROUPS,
                Pinning.permits(),
                null,
                Palette.NONE);
    }

    public static PinModuleInfo ofEnvironment(Environment environment,
                                              String prefix,
                                              String path,
                                              List<Path> moduleInfoFiles,
                                              HashDigestFunction hashFunction) {
        PinModuleInfo pin = new PinModuleInfo(prefix, path, moduleInfoFiles, hashFunction)
                .checksum(checksumFrom(environment))
                .flatten(flattenFrom(environment))
                .retain(retainFrom(environment))
                .permits(Pinning.permits(environment))
                .platform(Platform.ofEnvironment(environment));
        Boolean pins = environment.flagOrNull("print.pins");
        return pins == null || !pins ? pin : pin.printing(environment.out(), Palette.ofEnvironment(environment));
    }

    private PinModuleInfo(String prefix,
                          String path,
                          List<Path> moduleInfoFiles,
                          HashDigestFunction hashFunction,
                          Platform platform,
                          boolean checksum,
                          boolean flatten,
                          Retain retain,
                          Permits permits,
                          Consumer<String> printing,
                          Palette palette) {
        this.permits = permits;
        this.prefix = prefix;
        this.path = path;
        this.moduleInfoFiles = List.copyOf(moduleInfoFiles);
        this.hashFunction = hashFunction;
        this.platform = platform;
        this.checksum = checksum;
        this.flatten = flatten;
        this.retain = retain;
        this.printing = printing;
        this.palette = palette;
    }

    public PinModuleInfo permits(Permits permits) {
        return new PinModuleInfo(prefix, path, moduleInfoFiles, hashFunction, platform, checksum, flatten, retain, permits, printing, palette);
    }

    public PinModuleInfo platform(Platform platform) {
        return new PinModuleInfo(prefix, path, moduleInfoFiles, hashFunction, platform, checksum, flatten, retain, permits, printing, palette);
    }

    public PinModuleInfo checksum(boolean checksum) {
        return new PinModuleInfo(prefix, path, moduleInfoFiles, hashFunction, platform, checksum, flatten, retain, permits, printing, palette);
    }

    public PinModuleInfo flatten(boolean flatten) {
        return new PinModuleInfo(prefix, path, moduleInfoFiles, hashFunction, platform, checksum, flatten, retain, permits, printing, palette);
    }

    public PinModuleInfo retain(Retain retain) {
        return new PinModuleInfo(prefix, path, moduleInfoFiles, hashFunction, platform, checksum, flatten, retain, permits, printing, palette);
    }

    public PinModuleInfo printing(Consumer<String> printing, Palette palette) {
        return new PinModuleInfo(prefix, path, moduleInfoFiles, hashFunction, platform, checksum, flatten, retain, permits, printing, palette);
    }

    private static boolean checksumFrom(Environment environment) {
        String value = environment.getProperty("pin.checksum");
        if (value == null || value.equals("true")) {
            return true;
        }
        if (value.equals("false")) {
            return false;
        }
        throw new IllegalArgumentException("Unknown pin checksum mode: " + value + " (expected true or false)");
    }

    private static boolean flattenFrom(Environment environment) {
        String value = environment.getProperty("pin.bom");
        if (value == null || value.equals("keep")) {
            return false;
        }
        if (value.equals("flatten")) {
            return true;
        }
        throw new IllegalArgumentException("Unknown pin BOM mode: " + value + " (expected keep or flatten)");
    }

    private static Retain retainFrom(Environment environment) {
        String value = environment.value("pin.retain", "groups");
        return switch (value) {
            case "groups" -> Retain.GROUPS;
            case "all" -> Retain.ALL;
            case "none" -> Retain.NONE;
            default -> throw new IllegalArgumentException("Unknown pin retention: " + value + " (expected groups, all or none)");
        };
    }

    @Override
    public boolean shouldRun(SequencedMap<String, BuildStepArgument> arguments) {
        return true;
    }

    @Override
    public CompletionStage<BuildStepResult> apply(Executor executor,
                                                  BuildStepContext context,
                                                  SequencedMap<String, BuildStepArgument> arguments)
            throws IOException {
        Permits permits = this.permits;
        if (permits == null) {
            return pin(arguments);
        }
        try {
            permits.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting to rewrite the pins of " + path, e);
        }
        try {
            return pin(arguments);
        } finally {
            permits.release();
        }
    }

    private CompletionStage<BuildStepResult> pin(SequencedMap<String, BuildStepArgument> arguments)
            throws IOException {
        SequencedMap<String, Inventory.Dependency> closure = Inventory.closure(arguments.values(), path);
        Set<String> internal = collectInternal(Inventory.identities(arguments.values()));
        SequencedMap<String, String> entries = collectEntries(closure, internal, checksum ? hashFunction : null);
        Set<String> covered = new HashSet<>();
        SequencedMap<String, String> references = new LinkedHashMap<>();
        if (!flatten) {
            SequencedMap<String, String> managed = Inventory.bomEntries(arguments.values(), path);
            Iterator<Map.Entry<String, String>> it = entries.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, String> entry = it.next();
                String expanded = expand(entry.getKey());
                String supplied = managed.get(expanded);
                if (supplied != null && covers(supplied, entry.getValue())) {
                    covered.add(expanded);
                    it.remove();
                }
            }
            for (Map.Entry<String, Path> reference : Inventory.bomReferences(arguments.values(), path).entrySet()) {
                int lastSlash = reference.getKey().lastIndexOf('/');
                String version = reference.getKey().substring(lastSlash + 1);
                references.put(reference.getKey().substring(0, lastSlash), checksum
                        ? version + " " + hashFunction.encodedHash(reference.getValue())
                        : version);
            }
            for (Map.Entry<String, String> reference : Inventory.bomVersions(arguments.values(), path).entrySet()) {
                references.putIfAbsent(reference.getKey(), reference.getValue());
            }
        }
        Set<String> groups = new HashSet<>();
        for (Inventory.Dependency dependency : closure.values()) {
            groups.add(dependency.group());
        }
        Predicate<String> retained = switch (retain) {
            case GROUPS -> key -> !groups.contains(key.substring(0, key.indexOf('/')));
            case ALL -> _ -> true;
            case NONE -> _ -> false;
        };
        for (Path file : moduleInfoFiles) {
            SequencedSet<String> carried = new TreeSet<>();
            updateModuleInfo(file, entries, covered, internal, retained, references, flatten, platform, carried);
            if (printing != null && !carried.isEmpty()) {
                printing.accept("%s%-11s%s %s".formatted(
                        palette.warning(),
                        "[KEPT]",
                        palette.reset(),
                        file + ": kept, resolved by no closure: "
                                + String.join(", ", carried)));
            }
        }
        return CompletableFuture.completedStage(new BuildStepResult(true));
    }

    private static boolean covers(String supplied, String pin) {
        int suppliedSpace = supplied.indexOf(' ');
        String suppliedVersion = suppliedSpace < 0 ? supplied : supplied.substring(0, suppliedSpace);
        String suppliedChecksum = suppliedSpace < 0 ? null : supplied.substring(suppliedSpace + 1).trim();
        int pinSpace = pin.indexOf(' ');
        String pinVersion = pinSpace < 0 ? pin : pin.substring(0, pinSpace);
        String pinChecksum = pinSpace < 0 ? null : pin.substring(pinSpace + 1).trim();
        return suppliedVersion.equals(pinVersion) && (pinChecksum == null || pinChecksum.equals(suppliedChecksum));
    }

    private record Tag(String name, String token, String rest, int line) {
    }

    private record Comment(int start, int end, String prefix, List<Tag> tags) {
    }

    private record Located(int moduleStart, Comment comment) {
    }

    private static Located locate(Path file, String text) throws IOException {
        JavacTask javac = (JavacTask) ToolProvider.getSystemJavaCompiler().getTask(
                new PrintWriter(Writer.nullWriter()),
                ToolProvider.getSystemJavaCompiler().getStandardFileManager(null, null, null),
                null,
                null,
                null,
                List.of(new SimpleJavaFileObject(file.toUri(), JavaFileObject.Kind.SOURCE) {
                    @Override
                    public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                        return text;
                    }
                }));
        DocTrees docTrees = DocTrees.instance(javac);
        DocSourcePositions positions = docTrees.getSourcePositions();
        for (CompilationUnitTree unit : javac.parse()) {
            ModuleTree module = unit.getModule();
            if (module == null) {
                continue;
            }
            int moduleStart = (int) positions.getStartPosition(unit, module);
            moduleStart = text.lastIndexOf('\n', Math.max(moduleStart - 1, 0)) + 1;
            DocCommentTree doc = docTrees.getDocCommentTree(TreePath.getPath(unit, module));
            if (doc == null) {
                return new Located(moduleStart, null);
            }
            int contentStart = (int) positions.getStartPosition(unit, doc, doc);
            int contentEnd = (int) positions.getEndPosition(unit, doc, doc);
            if (contentStart < 0 || contentEnd < contentStart) {
                return new Located(moduleStart, null);
            }
            int start = text.lastIndexOf('\n', contentStart - 1) + 1;
            int end = text.indexOf('\n', Math.max(contentEnd - 1, start));
            if (end < 0) {
                end = text.length();
            }
            List<Tag> tags = new ArrayList<>();
            for (DocTree tag : doc.getBlockTags()) {
                if (!(tag instanceof UnknownBlockTagTree unknown)) {
                    continue;
                }
                int tagStart = (int) positions.getStartPosition(unit, doc, tag);
                if (tagStart < start || tagStart > end) {
                    continue;
                }
                int lineEnd = text.indexOf('\n', tagStart);
                if (lineEnd < 0 || lineEnd > end) {
                    lineEnd = end;
                }
                String content = text.substring(tagStart, lineEnd).trim();
                content = content.substring(Math.min(content.length(),
                        unknown.getTagName().length() + 1)).trim();
                int space = content.indexOf(' ');
                int line = (int) text.substring(start, tagStart).chars().filter(c -> c == '\n').count();
                tags.add(new Tag(unknown.getTagName(),
                        space < 0 ? content : content.substring(0, space),
                        space < 0 ? "" : content.substring(space + 1).trim(),
                        line));
            }
            return new Located(moduleStart, new Comment(start, end, text.substring(start, contentStart), tags));
        }
        return null;
    }

    private static void updateModuleInfo(Path file,
                                         SequencedMap<String, String> entries,
                                         Set<String> covered,
                                         Set<String> internal,
                                         Predicate<String> retained,
                                         SequencedMap<String, String> references,
                                         boolean flatten,
                                         Platform platform,
                                         SequencedSet<String> carried) throws IOException {
        String existing = Files.readString(file);
        Located located = locate(file, existing);
        if (located == null) {
            throw new IllegalStateException("No module declaration found in " + file);
        }
        Comment comment = located.comment();
        String updated;
        if (comment == null) {
            if (entries.isEmpty()) {
                return;
            }
            updated = existing.substring(0, located.moduleStart())
                    + renderJavadoc(entries)
                    + "\n"
                    + existing.substring(located.moduleStart());
        } else {
            updated = existing.substring(0, comment.start())
                    + rewriteJavadoc(existing.substring(comment.start(), comment.end()),
                            comment.prefix(),
                            comment.tags(),
                            entries,
                            covered,
                            internal,
                            retained,
                            references,
                            flatten,
                            platform,
                            carried)
                    + existing.substring(comment.end());
        }
        if (!updated.equals(existing)) {
            Files.writeString(file, updated);
        }
    }

    static SequencedMap<String, String> collectEntries(SequencedMap<String, Inventory.Dependency> closure,
                                                       Set<String> internal,
                                                       HashDigestFunction hashFunction) throws IOException {
        SequencedMap<String, String> entries = new TreeMap<>();
        for (Map.Entry<String, Inventory.Dependency> dependency : closure.entrySet()) {
            String group = dependency.getValue().group();
            String key = dependency.getKey().substring(group.length() + 1);
            if (internal.contains(key)) {
                continue;
            }
            int lastSlash = key.lastIndexOf('/');
            int firstSlash = key.indexOf('/');
            if (lastSlash <= 0 || lastSlash == firstSlash) {
                continue;
            }
            String coordinate = key.substring(0, lastSlash);
            String version = key.substring(lastSlash + 1);
            String mavenCoordinate = group.equals("main") && coordinate.startsWith("maven/")
                    ? coordinate.substring("maven/".length())
                    : null;
            boolean mavenShortcut = mavenCoordinate != null
                    && mavenCoordinate.indexOf('/') > 0
                    && mavenCoordinate.indexOf('/') == mavenCoordinate.lastIndexOf('/');
            String checksum = hashFunction == null
                    ? null
                    : dependency.getValue().checksum(hashFunction);
            String value = checksum == null ? version : version + " " + checksum;
            String entry;
            if (coordinate.startsWith("module/")) {
                String module = coordinate.substring("module/".length());
                int dash = module.indexOf('-');
                if (dash >= 0) {
                    value = ":" + module.substring(dash + 1) + ":" + value;
                    module = module.substring(0, dash);
                }
                entry = group.equals("main") ? module : group + "/module/" + module;
            } else {
                entry = mavenShortcut ? mavenCoordinate : group + "/" + coordinate;
            }
            entries.putIfAbsent(entry, value);
        }
        return entries;
    }

    static Set<String> collectInternal(Set<String> identities) {
        Set<String> internal = new LinkedHashSet<>();
        for (String coord : identities) {
            internal.add(coord);
            int firstSlash = coord.indexOf('/');
            int lastSlash = coord.lastIndexOf('/');
            if (firstSlash > 0 && lastSlash > firstSlash) {
                internal.add(coord.substring(0, lastSlash));
            }
        }
        return internal;
    }

    private record PinLine(int index, String token, String guard) {
    }

    private static String rewriteJavadoc(String javadoc,
                                         String prefix,
                                         List<Tag> located,
                                         SequencedMap<String, String> entries,
                                         Set<String> covered,
                                         Set<String> internal,
                                         Predicate<String> retained,
                                         SequencedMap<String, String> references,
                                         boolean flatten,
                                         Platform platform,
                                         SequencedSet<String> carried) {
        List<String> lines = new ArrayList<>(List.of(javadoc.split("\\n", -1)));
        int opening = prefix.indexOf("/**");
        String continuation = opening < 0 ? prefix : prefix.substring(0, opening) + " * ";
        IntFunction<String> leads = index -> index == 0 ? prefix : continuation;
        SequencedMap<Integer, Tag> pinTags = new LinkedHashMap<>(), bomTags = new LinkedHashMap<>();
        for (Tag tag : located) {
            if (tag.line() >= lines.size()) {
                continue;
            }
            switch (tag.name()) {
                case "jenesis.pin" -> pinTags.put(tag.line(), tag);
                case "jenesis.bom" -> bomTags.put(tag.line(), tag);
                default -> {
                }
            }
        }
        rewriteBoms(lines, leads, bomTags, references, flatten, platform);
        SequencedMap<String, List<PinLine>> guarded = new LinkedHashMap<>();
        for (Tag tag : pinTags.values()) {
            if (tag.rest().endsWith(")")) {
                guarded.computeIfAbsent(expand(tag.token()), _ -> new ArrayList<>());
            }
        }
        for (Map.Entry<Integer, Tag> entry : pinTags.entrySet()) {
            int lineIndex = entry.getKey();
            Tag tag = entry.getValue();
            String key = expand(tag.token());
            List<PinLine> pins = guarded.get(key);
            if (pins == null) {
                continue;
            }
            String rest = tag.rest();
            String guard = null;
            if (rest.endsWith(")")) {
                int bracket = rest.lastIndexOf('(');
                if (bracket > 0 && !rest.substring(0, bracket).trim().isEmpty()) {
                    guard = rest.substring(bracket + 1, rest.length() - 1);
                }
            }
            pins.add(new PinLine(lineIndex, tag.token(), guard));
        }
        SequencedMap<String, String> expanded = new LinkedHashMap<>();
        entries.forEach((key, value) -> expanded.put(expand(key), key + " " + value));
        for (Map.Entry<String, List<PinLine>> entry : guarded.entrySet()) {
            String resolved = expanded.get(entry.getKey());
            if (resolved == null) {
                continue;
            }
            PinLine fallback = null, matched = null;
            int specificity = 0;
            boolean ambiguous = false;
            for (PinLine pin : entry.getValue()) {
                if (pin.guard() == null) {
                    fallback = pin;
                    continue;
                }
                Platform guard = Platform.of(pin.guard());
                if (!platform.matches(guard)) {
                    continue;
                }
                if (guard.tokens().size() > specificity) {
                    matched = pin;
                    specificity = guard.tokens().size();
                    ambiguous = false;
                } else if (guard.tokens().size() == specificity) {
                    ambiguous = true;
                }
            }
            if (ambiguous) {
                continue;
            }
            PinLine winner = matched != null ? matched : fallback;
            if (winner != null) {
                lines.set(winner.index(), leads.apply(winner.index()) + "@jenesis.pin "
                        + resolved
                        + (winner.guard() == null ? "" : " (" + winner.guard() + ")"));
            }
        }
        Set<String> regenerated = new HashSet<>();
        for (String key : entries.keySet()) {
            regenerated.add(expand(key));
        }
        regenerated.addAll(covered);
        SequencedMap<String, String> merged = new TreeMap<>();
        int insertAt = -1;
        List<String> kept = new ArrayList<>();
        for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
            Tag tag = pinTags.get(lineIndex);
            if (tag != null && !guarded.containsKey(expand(tag.token()))) {
                if (lineIndex == 0 && opening >= 0) {
                    kept.add(prefix.stripTrailing());
                }
                if (insertAt < 0) {
                    insertAt = kept.size();
                }
                String coordinate = expand(tag.token());
                if (internal.contains(coordinate.substring(coordinate.indexOf('/') + 1))) {
                    merged.putIfAbsent(tag.token(), tag.rest());
                } else if (!regenerated.contains(coordinate) && retained.test(coordinate)) {
                    merged.putIfAbsent(tag.token(), tag.rest());
                    carried.add(tag.token() + " " + tag.rest());
                }
                continue;
            }
            if (lines.get(lineIndex) != null) {
                kept.add(lines.get(lineIndex));
            }
        }
        lines = kept;
        if (insertAt < 0) {
            insertAt = lines.size();
        }
        for (Map.Entry<String, String> entry : entries.entrySet()) {
            if (guarded.containsKey(expand(entry.getKey()))) {
                continue;
            }
            merged.put(entry.getKey(), entry.getValue());
        }
        List<String> tags = new ArrayList<>();
        for (Map.Entry<String, String> entry : merged.entrySet()) {
            tags.add(continuation + "@jenesis.pin " + entry.getKey() + " " + entry.getValue());
        }
        lines.addAll(insertAt, tags);
        return String.join("\n", lines);
    }

    private static void rewriteBoms(List<String> lines,
                                    IntFunction<String> leads,
                                    SequencedMap<Integer, Tag> bomTags,
                                    SequencedMap<String, String> references,
                                    boolean flatten,
                                    Platform platform) {
        if (flatten) {
            for (Map.Entry<Integer, Tag> entry : bomTags.entrySet()) {
                String rest = entry.getValue().rest();
                if (rest.endsWith(")")) {
                    int bracket = rest.lastIndexOf('(');
                    if (bracket > 0 && !rest.substring(0, bracket).trim().isEmpty()) {
                        throw new IllegalStateException("Cannot flatten platform-guarded BOM declaration: "
                                + lines.get(entry.getKey()).trim());
                    }
                }
                String lead = leads.apply(entry.getKey());
                lines.set(entry.getKey(), lead.contains("/**") ? lead.stripTrailing() : null);
            }
            return;
        }
        SequencedMap<String, List<PinLine>> declarations = new LinkedHashMap<>();
        for (Map.Entry<Integer, Tag> entry : bomTags.entrySet()) {
            int lineIndex = entry.getKey();
            String token = entry.getValue().token();
            String last = token.substring(token.lastIndexOf('/') + 1);
            if (last.startsWith("pin-") && last.endsWith(".properties")) {
                continue;
            }
            String rest = entry.getValue().rest();
            String guard = null;
            if (rest.endsWith(")")) {
                int bracket = rest.lastIndexOf('(');
                if (bracket > 0 && !rest.substring(0, bracket).trim().isEmpty()) {
                    guard = rest.substring(bracket + 1, rest.length() - 1);
                }
            }
            declarations.computeIfAbsent(expand(token), _ -> new ArrayList<>())
                    .add(new PinLine(lineIndex, token, guard));
        }
        for (Map.Entry<String, List<PinLine>> entry : declarations.entrySet()) {
            String resolved = references.get(entry.getKey());
            if (resolved == null) {
                continue;
            }
            PinLine fallback = null, matched = null;
            int specificity = 0;
            boolean ambiguous = false;
            for (PinLine declaration : entry.getValue()) {
                if (declaration.guard() == null) {
                    fallback = declaration;
                    continue;
                }
                Platform guard = Platform.of(declaration.guard());
                if (!platform.matches(guard)) {
                    continue;
                }
                if (guard.tokens().size() > specificity) {
                    matched = declaration;
                    specificity = guard.tokens().size();
                    ambiguous = false;
                } else if (guard.tokens().size() == specificity) {
                    ambiguous = true;
                }
            }
            if (ambiguous) {
                continue;
            }
            PinLine winner = matched != null ? matched : fallback;
            if (winner != null) {
                lines.set(winner.index(), leads.apply(winner.index()) + "@jenesis.bom "
                        + winner.token()
                        + " "
                        + resolved
                        + (winner.guard() == null ? "" : " [" + winner.guard() + "]"));
            }
        }
    }

    private static String expand(String token) {
        return ModuleInfoParser.expand("main", "jenesis.pin", token);
    }

    private static String renderJavadoc(SequencedMap<String, String> entries) {
        StringBuilder sb = new StringBuilder("/**\n");
        for (Map.Entry<String, String> entry : entries.entrySet()) {
            sb.append(" * @jenesis.pin ").append(entry.getKey()).append(" ").append(entry.getValue()).append("\n");
        }
        sb.append(" */");
        return sb.toString();
    }

    public enum Retain {
        GROUPS, ALL, NONE
    }
}
