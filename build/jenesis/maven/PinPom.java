package build.jenesis.maven;

import module java.base;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.BuildStepResult;
import build.jenesis.Environment;
import build.jenesis.HashDigestFunction;
import build.jenesis.Permits;
import build.jenesis.Pinning;
import build.jenesis.Platform;
import build.jenesis.SequencedProperties;
import build.jenesis.step.Inventory;

public class PinPom implements BuildStep {

    private static final Pattern DEPENDENCY_MANAGEMENT = Pattern.compile(
            "(?s)([ \\t]*)<dependencyManagement>.*?</dependencyManagement>\\s*\\n");
    private static final Pattern DEPENDENCIES_OPEN = Pattern.compile("([ \\t]*)<dependencies>");
    private static final Pattern PROJECT_CLOSE = Pattern.compile("\\n([ \\t]*)</project>");
    private static final Pattern CHECKSUM_COMMENT = Pattern.compile("[ \\t]*<!--\\s*Checksum/[^>]*-->\\s*\\n");
    private static final Pattern INDENT = Pattern.compile("\\n([ \\t]+)<");
    private static final Pattern PIN_COMMENT = Pattern.compile("(?s)([ \\t]*)<!--\\s*jenesis\\.pin\\b(.*?)-->\\s*\\n");
    private static final Pattern MANAGED_DEPENDENCY = Pattern.compile("(?s)[ \\t]*<dependency>.*?</dependency>[ \\t]*\\n");
    private static final Pattern IMPORT_SCOPE = Pattern.compile("<scope>\\s*import\\s*</scope>");
    private static final Pattern EXCLUSIONS = Pattern.compile("(?s)<exclusions>.*?</exclusions>");
    private static final Pattern COORDINATE_ELEMENT = Pattern.compile(
            "[ \\t]*<(groupId|artifactId|version|type|classifier)>\\s*([^<]*?)\\s*</\\1>[ \\t]*\\n?");

    private final transient Permits permits;

    private final String prefix;
    private final String path;
    private final List<Path> pomFiles;
    private final transient HashDigestFunction hashFunction;
    private final Platform platform;

    public PinPom(String prefix, String path, List<Path> pomFiles, HashDigestFunction hashFunction) {
        this(prefix, path, pomFiles, hashFunction, new Platform(), Pinning.permits());
    }

    public static PinPom ofEnvironment(Environment environment,
                                       String prefix,
                                       String path,
                                       List<Path> pomFiles,
                                       HashDigestFunction hashFunction) {
        return new PinPom(prefix, path, pomFiles, hashFunction)
                .permits(Pinning.permits(environment))
                .platform(Platform.ofEnvironment(environment));
    }

    private PinPom(String prefix,
                   String path,
                   List<Path> pomFiles,
                   HashDigestFunction hashFunction,
                   Platform platform,
                   Permits permits) {
        this.permits = permits;
        this.prefix = prefix;
        this.path = path;
        this.pomFiles = List.copyOf(pomFiles);
        this.hashFunction = hashFunction;
        this.platform = platform;
    }

    public PinPom permits(Permits permits) {
        return new PinPom(prefix, path, pomFiles, hashFunction, platform, permits);
    }

    public PinPom platform(Platform platform) {
        return new PinPom(prefix, path, pomFiles, hashFunction, platform, permits);
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
        SequencedMap<String, List<String>> exclusions = new LinkedHashMap<>();
        SequencedMap<String, String> scopes = new LinkedHashMap<>(), expressions = new LinkedHashMap<>();
        Set<String> declared = new HashSet<>();
        for (BuildStepArgument argument : arguments.values()) {
            if (argument.removed()) {
                continue;
            }
            Path module = argument.folder().resolve(MODULE),
                    managed = argument.folder().resolve(MANAGED),
                    scoped = argument.folder().resolve(MavenProject.SCOPES),
                    expressed = argument.folder().resolve(MavenProject.EXPRESSIONS),
                    requires = argument.folder().resolve(REQUIRES);
            if (!Files.isRegularFile(module)
                    || !path.equals(SequencedProperties.ofFiles(module).getProperty("path"))) {
                continue;
            }
            if (Files.isRegularFile(requires)) {
                for (String key : SequencedProperties.ofFiles(requires).stringPropertyNames()) {
                    int first = key.indexOf('/'), second = key.indexOf('/', first + 1);
                    if (first > 0 && second > first) {
                        declared.add(key.substring(0, first) + key.substring(second));
                    }
                }
            }
            if (Files.isRegularFile(managed)) {
                SequencedProperties properties = SequencedProperties.ofFiles(managed);
                for (String key : properties.stringPropertyNames()) {
                    String coordinate = key.substring(key.indexOf('/') + 1);
                    if (coordinate.startsWith(prefix + "/")) {
                        exclusions.putIfAbsent(coordinate.substring(prefix.length() + 1), properties.entries(key));
                    }
                }
            }
            if (Files.isRegularFile(scoped)) {
                SequencedProperties.ofFiles(scoped).forEachProperty((key, scope) -> {
                    String coordinate = key.substring(key.indexOf('/') + 1);
                    if (coordinate.startsWith(prefix + "/")) {
                        scopes.putIfAbsent(coordinate.substring(prefix.length() + 1), scope);
                    }
                });
            }
            if (Files.isRegularFile(expressed)) {
                SequencedProperties.ofFiles(expressed).forEachProperty(expressions::putIfAbsent);
            }
        }
        SequencedMap<String, String> entries = collectEntries(closure, internal, declared, hashFunction);
        for (Path pomFile : pomFiles) {
            updatePom(pomFile, entries, exclusions, scopes, expressions);
        }
        return CompletableFuture.completedStage(new BuildStepResult(true));
    }

    private void updatePom(Path pomFile,
                           SequencedMap<String, String> entries,
                           SequencedMap<String, List<String>> exclusions,
                           SequencedMap<String, String> scopes,
                           SequencedMap<String, String> expressions) throws IOException {
        Map<String, String> expressed = new HashMap<>();
        expressions.forEach((coordinate, expression) -> {
            String[] raw = expression.split(" ")[0].split("/", -1);
            expressed.put(new MavenDependencyKey(raw[0], raw[1], raw[2], raw[3]).coordinate(null, null), coordinate);
        });
        String existing = Files.readString(pomFile);
        SequencedMap<String, String> managed = new TreeMap<>();
        SequencedMap<String, String> qualified = new TreeMap<>();
        for (Map.Entry<String, String> entry : entries.entrySet()) {
            String key = entry.getKey();
            int first = key.indexOf('/');
            int second = key.indexOf('/', first + 1);
            String group = key.substring(0, first);
            String repository = second < 0 ? "" : key.substring(first + 1, second);
            if (repository.equals("maven") && group.equals("main")) {
                managed.putIfAbsent(key.substring(second + 1), entry.getValue());
            } else {
                qualified.put(key, entry.getValue());
            }
        }
        Matcher pinMatcher = PIN_COMMENT.matcher(existing);
        StringBuilder pinned = new StringBuilder();
        while (pinMatcher.find()) {
            pinned.append(pinMatcher.group(2)).append('\n');
        }
        List<String> preserved = pinned.isEmpty()
                ? List.of()
                : preserveGuarded(pinned.toString(), qualified, managed);
        Matcher dependencyManagementMatcher = DEPENDENCY_MANAGEMENT.matcher(existing);
        String indent, updated;
        if (dependencyManagementMatcher.find()) {
            indent = dependencyManagementMatcher.group(1);
            SequencedMap<String, String> pending = new TreeMap<>(managed);
            StringBuilder rewritten = new StringBuilder();
            Matcher dependencyMatcher = MANAGED_DEPENDENCY.matcher(dependencyManagementMatcher.group());
            while (dependencyMatcher.find()) {
                String dependency = dependencyMatcher.group();
                if (IMPORT_SCOPE.matcher(dependency).find()) {
                    dependencyMatcher.appendReplacement(rewritten, Matcher.quoteReplacement(dependency));
                    continue;
                }
                String inner = dependency.substring(dependency.indexOf("<dependency>") + "<dependency>".length(),
                        dependency.lastIndexOf("</dependency>"));
                Map<String, String> coordinate = new HashMap<>();
                StringBuilder retaining = new StringBuilder();
                Matcher exclusionsMatcher = EXCLUSIONS.matcher(inner);
                int from = 0;
                boolean nested;
                do {
                    nested = exclusionsMatcher.find();
                    Matcher elementMatcher = COORDINATE_ELEMENT.matcher(
                            inner.substring(from, nested ? exclusionsMatcher.start() : inner.length()));
                    while (elementMatcher.find()) {
                        coordinate.putIfAbsent(elementMatcher.group(1), elementMatcher.group(2));
                    }
                    retaining.append(CHECKSUM_COMMENT.matcher(elementMatcher.replaceAll("")).replaceAll(""));
                    if (nested) {
                        retaining.append(exclusionsMatcher.group());
                        from = exclusionsMatcher.end();
                    }
                } while (nested);
                String children = retaining.substring(retaining.indexOf("\n") + 1);
                children = children.substring(0, children.lastIndexOf('\n') + 1);
                String key = null;
                if (coordinate.containsKey("groupId") && coordinate.containsKey("artifactId")) {
                    String written = new MavenDependencyKey(coordinate.get("groupId"),
                            coordinate.get("artifactId"),
                            coordinate.get("type"),
                            coordinate.get("classifier")).coordinate(null, null);
                    key = expressed.getOrDefault(written, written);
                }
                String value = key == null ? null : pending.remove(key);
                dependencyMatcher.appendReplacement(rewritten, Matcher.quoteReplacement(value != null
                        ? renderEntry(key, value, children.isBlank() ? null : children, exclusions, scopes, expressions, indent)
                        : CHECKSUM_COMMENT.matcher(dependency).find() ? "" : dependency));
            }
            dependencyMatcher.appendTail(rewritten);
            StringBuilder added = new StringBuilder();
            for (Map.Entry<String, String> entry : pending.entrySet()) {
                added.append(renderEntry(entry.getKey(), entry.getValue(), null, exclusions, scopes, expressions, indent));
            }
            int close = rewritten.lastIndexOf("</dependencies>");
            if (close < 0) {
                throw new IllegalStateException("The <dependencyManagement> of " + pomFile + " holds no <dependencies>");
            }
            rewritten.insert(rewritten.lastIndexOf("\n", close) + 1, added);
            updated = existing.substring(0, dependencyManagementMatcher.start())
                    + rewritten
                    + existing.substring(dependencyManagementMatcher.end());
        } else {
            Matcher indentMatcher = INDENT.matcher(existing);
            indent = indentMatcher.find() ? indentMatcher.group(1) : "    ";
            if (managed.isEmpty()) {
                updated = existing;
            } else {
                StringBuilder block = new StringBuilder();
                block.append(indent).append("<dependencyManagement>\n");
                block.append(indent).append(indent).append("<dependencies>\n");
                for (Map.Entry<String, String> entry : managed.entrySet()) {
                    block.append(renderEntry(entry.getKey(), entry.getValue(), null, exclusions, scopes, expressions, indent));
                }
                block.append(indent).append(indent).append("</dependencies>\n");
                block.append(indent).append("</dependencyManagement>\n");
                Matcher dependenciesMatcher = DEPENDENCIES_OPEN.matcher(existing);
                if (dependenciesMatcher.find()) {
                    updated = existing.substring(0, dependenciesMatcher.start()) + block + existing.substring(dependenciesMatcher.start());
                } else {
                    Matcher projectCloseMatcher = PROJECT_CLOSE.matcher(existing);
                    if (!projectCloseMatcher.find()) {
                        throw new IllegalStateException("No </project> tag in " + pomFile);
                    }
                    updated = existing.substring(0, projectCloseMatcher.start() + 1) + block + existing.substring(projectCloseMatcher.start() + 1);
                }
            }
        }
        Matcher requiresMatcher = PIN_COMMENT.matcher(updated);
        String requires = qualified.isEmpty() && preserved.isEmpty() ? "" : renderRequires(qualified, preserved, indent);
        if (requiresMatcher.find()) {
            StringBuilder rewritten = new StringBuilder();
            requiresMatcher.appendReplacement(rewritten, Matcher.quoteReplacement(requires));
            while (requiresMatcher.find()) {
                requiresMatcher.appendReplacement(rewritten, "");
            }
            updated = requiresMatcher.appendTail(rewritten).toString();
        } else if (!requires.isEmpty()) {
            Matcher projectCloseMatcher = PROJECT_CLOSE.matcher(updated);
            if (!projectCloseMatcher.find()) {
                throw new IllegalStateException("No </project> tag in " + pomFile);
            }
            updated = updated.substring(0, projectCloseMatcher.start() + 1) + requires + updated.substring(projectCloseMatcher.start() + 1);
        }
        updated = stripDirectDependencyChecksums(updated);
        if (!updated.equals(existing)) {
            Files.writeString(pomFile, updated);
        }
    }

    private static String expand(String token) {
        int first = token.indexOf('/');
        if (first < 0) {
            return "main/module/" + token;
        }
        return token.indexOf('/', first + 1) < 0 ? "main/maven/" + token : token;
    }

    static SequencedMap<String, String> collectEntries(SequencedMap<String, Inventory.Dependency> closure,
                                                       Set<String> internal,
                                                       Set<String> declared,
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
            String checksum = version.endsWith("-SNAPSHOT") ? null : dependency.getValue().checksum(hashFunction);
            String value = checksum == null ? version : version + " " + checksum;
            if (declared.contains(dependency.getKey())) {
                entries.put(group + "/" + coordinate, value);
            } else {
                entries.putIfAbsent(group + "/" + coordinate, value);
            }
        }
        return entries;
    }

    private List<String> preserveGuarded(String block,
                                         SequencedMap<String, String> qualified,
                                         SequencedMap<String, String> managed) {
        record Pin(String token, String value, String guard) {
        }
        List<Pin> pins = new ArrayList<>();
        Set<String> guarded = new LinkedHashSet<>();
        for (String line : block.replace("&#45;", "-").split("\n")) {
            String trimmed = line.trim().replaceAll("\\s+", " ");
            if (trimmed.isEmpty()) {
                continue;
            }
            int space = trimmed.indexOf(' ');
            if (space < 1) {
                continue;
            }
            String token = trimmed.substring(0, space);
            String value = trimmed.substring(space + 1).trim();
            String guard = null;
            if (value.endsWith(")")) {
                int bracket = value.lastIndexOf('(');
                if (bracket > 0 && !value.substring(0, bracket).trim().isEmpty()) {
                    guard = value.substring(bracket + 1, value.length() - 1);
                    value = value.substring(0, bracket).trim();
                }
            }
            pins.add(new Pin(token, value, guard));
            if (guard != null) {
                guarded.add(expand(token));
            }
        }
        if (guarded.isEmpty()) {
            return List.of();
        }
        for (String key : guarded) {
            String resolved = key.startsWith("main/maven/")
                    ? managed.remove(key.substring("main/maven/".length()))
                    : qualified.remove(key);
            Integer fallback = null, matched = null;
            int specificity = 0;
            boolean ambiguous = false;
            for (int index = 0; index < pins.size(); index++) {
                Pin pin = pins.get(index);
                if (!expand(pin.token()).equals(key)) {
                    continue;
                }
                if (pin.guard() == null) {
                    fallback = index;
                    continue;
                }
                Platform guard = Platform.of(pin.guard());
                if (!platform.matches(guard)) {
                    continue;
                }
                if (guard.tokens().size() > specificity) {
                    matched = index;
                    specificity = guard.tokens().size();
                    ambiguous = false;
                } else if (guard.tokens().size() == specificity) {
                    ambiguous = true;
                }
            }
            Integer winner = matched != null ? matched : fallback;
            if (resolved != null && winner != null && !ambiguous) {
                Pin pin = pins.get(winner);
                pins.set(winner, new Pin(pin.token(), resolved, pin.guard()));
            }
        }
        List<String> preserved = new ArrayList<>();
        for (Pin pin : pins) {
            if (guarded.contains(expand(pin.token()))) {
                preserved.add(pin.token() + " " + pin.value() + (pin.guard() == null ? "" : " (" + pin.guard() + ")"));
            }
        }
        return preserved;
    }

    private static String renderRequires(SequencedMap<String, String> qualified, List<String> preserved, String indent) {
        StringBuilder sb = new StringBuilder();
        sb.append(indent).append("<!--jenesis.pin\n");
        for (String line : preserved) {
            sb.append(indent).append(line.replace("--", "&#45;&#45;")).append("\n");
        }
        for (Map.Entry<String, String> entry : qualified.entrySet()) {
            sb.append(indent).append((entry.getKey() + " " + entry.getValue()).replace("--", "&#45;&#45;")).append("\n");
        }
        sb.append(indent).append("-->\n");
        return sb.toString();
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

    private static String stripDirectDependencyChecksums(String content) {
        Matcher dependencyManagementMatcher = DEPENDENCY_MANAGEMENT.matcher(content);
        int dependencyManagementStart = -1, dependencyManagementEnd = -1;
        if (dependencyManagementMatcher.find()) {
            dependencyManagementStart = dependencyManagementMatcher.start();
            dependencyManagementEnd = dependencyManagementMatcher.end();
        }
        Matcher checksumMatcher = CHECKSUM_COMMENT.matcher(content);
        StringBuilder result = new StringBuilder();
        int previous = 0;
        while (checksumMatcher.find()) {
            if (checksumMatcher.start() >= dependencyManagementStart && checksumMatcher.end() <= dependencyManagementEnd) {
                continue;
            }
            result.append(content, previous, checksumMatcher.start());
            previous = checksumMatcher.end();
        }
        result.append(content, previous, content.length());
        return result.toString();
    }

    private static String renderEntry(String key,
                                      String value,
                                      String children,
                                      SequencedMap<String, List<String>> exclusions,
                                      SequencedMap<String, String> scopes,
                                      SequencedMap<String, String> expressions,
                                      String indent) {
        StringBuilder sb = new StringBuilder();
        String[] elements = key.split("/");
        String groupId, artifactId, type, classifier;
        switch (elements.length) {
            case 2 -> { groupId = elements[0]; artifactId = elements[1]; type = null; classifier = null; }
            case 3 -> { groupId = elements[0]; artifactId = elements[1]; type = elements[2]; classifier = null; }
            case 4 -> { groupId = elements[0]; artifactId = elements[1]; type = elements[2]; classifier = elements[3]; }
            default -> throw new IllegalArgumentException("Insufficient Maven coordinate: " + key);
        }
        int space = value.indexOf(' ');
        String version = space < 0 ? value : value.substring(0, space);
        String checksum = space < 0 ? null : value.substring(space + 1).trim();
        String pinned = version, expression = expressions.get(key);
        if (expression != null) {
            String[] words = expression.split(" ");
            String[] raw = words[0].split("/", -1);
            groupId = raw[0];
            artifactId = raw[1];
            type = raw[2].isEmpty() ? type : raw[2];
            classifier = raw[3].isEmpty() ? classifier : raw[3];
            if (words.length == 3 && words[2].equals(version)) {
                version = words[1];
            }
        }
        String prefix = indent + indent + indent;
        sb.append(prefix).append("<dependency>\n");
        sb.append(prefix).append(indent).append("<groupId>").append(groupId).append("</groupId>\n");
        sb.append(prefix).append(indent).append("<artifactId>").append(artifactId).append("</artifactId>\n");
        sb.append(prefix).append(indent).append("<version>").append(version).append("</version>\n");
        if (type != null && !"jar".equals(type)) {
            sb.append(prefix).append(indent).append("<type>").append(type).append("</type>\n");
        }
        if (classifier != null) {
            sb.append(prefix).append(indent).append("<classifier>").append(classifier).append("</classifier>\n");
        }
        List<String> excluded = exclusions.get(key);
        String scope = scopes.get(key);
        if (children == null && scope != null) {
            sb.append(prefix).append(indent).append("<scope>").append(scope).append("</scope>\n");
        }
        if (children != null) {
            sb.append(children);
        } else if (excluded != null && !excluded.isEmpty()) {
            sb.append(prefix).append(indent).append("<exclusions>\n");
            for (String exclusion : excluded) {
                int slash = exclusion.indexOf('/');
                sb.append(prefix).append(indent).append(indent).append("<exclusion>\n");
                sb.append(prefix).append(indent).append(indent).append(indent)
                        .append("<groupId>").append(exclusion, 0, slash).append("</groupId>\n");
                sb.append(prefix).append(indent).append(indent).append(indent)
                        .append("<artifactId>").append(exclusion.substring(slash + 1)).append("</artifactId>\n");
                sb.append(prefix).append(indent).append(indent).append("</exclusion>\n");
            }
            sb.append(prefix).append(indent).append("</exclusions>\n");
        }
        if (checksum != null) {
            sb.append(prefix).append(indent).append("<!--Checksum/").append(checksum)
                    .append(version.equals(pinned) ? "" : " " + pinned)
                    .append("-->\n");
        }
        sb.append(prefix).append("</dependency>\n");
        return sb.toString();
    }
}
