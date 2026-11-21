package build.jenesis.module;

import module java.base;
import module jdk.compiler;
import build.jenesis.ModuleGraph;
import build.jenesis.Platform;
import com.sun.source.doctree.LiteralTree;
import javax.lang.model.SourceVersion;
import javax.tools.ToolProvider;

import static java.util.Objects.requireNonNull;

public class ModuleInfoParser {

    private static final Pattern COORDINATE = Pattern.compile("[A-Za-z0-9_.:+~@*/-]+");
    private static final Set<String> BLOCKS = Set.of("p", "div", "pre", "ul", "ol", "dl", "table", "blockquote",
            "h1", "h2", "h3", "h4", "h5", "h6", "hr");

    private final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    private final String group;

    public ModuleInfoParser() {
        this("main");
    }

    public ModuleInfoParser(String group) {
        this.group = group;
    }

    public ModuleInfo identify(Path moduleInfo) throws IOException {
        JavacTask javac = (JavacTask) compiler.getTask(new PrintWriter(Writer.nullWriter()),
                compiler.getStandardFileManager(null, null, null),
                null,
                null,
                null,
                List.of(new SimpleJavaFileObject(moduleInfo.toUri(), JavaFileObject.Kind.SOURCE) {
                    @Override
                    public CharSequence getCharContent(boolean ignoreEncodingErrors) throws IOException {
                        return Files.readString(moduleInfo);
                    }
                }));
        DocTrees docTrees = DocTrees.instance(javac);
        for (CompilationUnitTree unit : javac.parse()) {
            ModuleTree module = requireNonNull(unit.getModule());
            SequencedSet<String> dependencies = new LinkedHashSet<>();
            SequencedSet<String> runtimeDependencies = new LinkedHashSet<>();
            for (DirectiveTree directive : module.getDirectives()) {
                if (directive instanceof RequiresTree requires) {
                    String name = requires.getModuleName().toString();
                    if (!ModuleGraph.isSystemModule(name)) {
                        dependencies.add(name);
                        if (!requires.isStatic()) {
                            runtimeDependencies.add(name);
                        }
                    }
                }
            }
            SequencedMap<String, String> aliases = new LinkedHashMap<>();
            SequencedMap<String, SequencedSet<String>> excludes = new LinkedHashMap<>();
            SequencedMap<String, SequencedSet<String>> overrides = new LinkedHashMap<>();
            SequencedMap<String, String> versions = new LinkedHashMap<>();
            SequencedMap<String, SequencedMap<String, String>> variants = new LinkedHashMap<>();
            SequencedMap<String, String> boms = new LinkedHashMap<>();
            SequencedMap<String, String> signatures = new TreeMap<>();
            SequencedMap<String, SequencedMap<String, String>> bomVariants = new LinkedHashMap<>();
            SequencedMap<String, String> plugins = new LinkedHashMap<>();
            SequencedMap<String, String> layerApis = new LinkedHashMap<>();
            SequencedMap<String, SequencedSet<String>> layers = new LinkedHashMap<>();
            SequencedMap<String, String> attachments = new LinkedHashMap<>();
            SequencedSet<String> natives = new LinkedHashSet<>();
            String release = null;
            String name = null;
            String description = null;
            String testOf = null;
            boolean abstractTest = false;
            String main = null;
            DocCommentTree docComment = docTrees.getDocCommentTree(TreePath.getPath(unit, module));
            if (docComment != null) {
                String summary = text(docComment.getFirstSentence()).lines()
                        .map(String::strip)
                        .filter(line -> !line.isEmpty())
                        .collect(Collectors.joining(" "));
                if (!summary.isEmpty()) {
                    name = summary.endsWith(".")
                            ? summary.substring(0, summary.length() - 1)
                            : summary;
                }
                List<String> paragraphs = new ArrayList<>();
                StringBuilder paragraph = new StringBuilder();
                for (String line : text(docComment.getFullBody()).lines().toList()) {
                    if (!line.isBlank()) {
                        paragraph.append(paragraph.isEmpty() ? "" : " ").append(line.strip());
                    } else if (!paragraph.isEmpty()) {
                        paragraphs.add(paragraph.toString());
                        paragraph.setLength(0);
                    }
                }
                if (!paragraph.isEmpty()) {
                    paragraphs.add(paragraph.toString());
                }
                if (paragraphs.size() > 1) {
                    description = paragraphs.get(1);
                }
                for (DocTree tag : docComment.getBlockTags()) {
                    if (tag instanceof UnknownBlockTagTree unknown) {
                        String content = unknown.getContent().stream()
                                .map(Object::toString)
                                .collect(Collectors.joining())
                                .replaceAll("\\s+", " ")
                                .trim();
                        switch (unknown.getTagName()) {
                            case "jenesis.pin" -> {
                                int split = content.indexOf(' ');
                                if (split < 1 || split == content.length() - 1) {
                                    continue;
                                }
                                String token = content.substring(0, split);
                                String version = content.substring(split + 1).trim();
                                String guard = null;
                                if (version.endsWith(")")) {
                                    int bracket = version.lastIndexOf('(');
                                    if (bracket < 0) {
                                        throw new IllegalArgumentException("Malformed @jenesis.pin guard '"
                                                + version
                                                + "': expected <value> (<token>,<token>...)");
                                    }
                                    String guarded = version.substring(0, bracket).trim();
                                    if (!guarded.isEmpty()) {
                                        guard = Platform.of(
                                                version.substring(bracket + 1, version.length() - 1)).canonical();
                                        version = guarded;
                                    }
                                }
                                if (token.isEmpty() || version.isEmpty()
                                        || ModuleGraph.isSystemModule(token)) {
                                    continue;
                                }
                                String[] words = version.split(" ");
                                if (words.length > 2 || (words.length == 2 && words[1].indexOf('/') < 1)) {
                                    throw new IllegalArgumentException("Malformed @jenesis.pin declaration '"
                                            + token + " " + version
                                            + "': expected <token> <version> [<algorithm>/<hash>] [(<platform>)]."
                                            + " A tag owns every line below it until the next tag, so prose written"
                                            + " under a pin becomes part of that pin; move it above the tag block");
                                }
                                String key = expand("jenesis.pin", token);
                                if (guard == null) {
                                    versions.put(key, version);
                                } else {
                                    variants.computeIfAbsent(key, _ -> new LinkedHashMap<>()).put(guard, version);
                                }
                            }
                            case "jenesis.bom" -> {
                                String guard = null;
                                if (content.endsWith(")")) {
                                    int bracket = content.lastIndexOf('(');
                                    if (bracket < 0) {
                                        throw new IllegalArgumentException("Malformed @jenesis.bom guard '"
                                                + content
                                                + "': expected <value> (<token>,<token>...)");
                                    }
                                    String guarded = content.substring(0, bracket).trim();
                                    if (!guarded.isEmpty()) {
                                        guard = Platform.of(content.substring(bracket + 1, content.length() - 1)).canonical();
                                        content = guarded;
                                    }
                                }
                                if (content.isEmpty()) {
                                    continue;
                                }
                                String[] words = content.split(" ");
                                String token = words[0], key, value;
                                String last = token.substring(token.lastIndexOf('/') + 1);
                                if (last.startsWith("pin-") && last.endsWith(".properties")) {
                                    if (words.length > 1) {
                                        throw new IllegalArgumentException("Malformed @jenesis.bom declaration '"
                                                + content
                                                + "': a local BOM takes no version or checksum");
                                    }
                                    int first = token.indexOf('/');
                                    String qualifier = first < 0 ? group : token.substring(0, first);
                                    if (qualifier.isEmpty() || first != token.lastIndexOf('/')) {
                                        throw new IllegalArgumentException("Malformed @jenesis.bom token '"
                                                + token
                                                + "': expected [<group>/]pin-<name>.properties");
                                    }
                                    key = qualifier + "/" + last;
                                    value = "";
                                } else {
                                    key = expand("jenesis.bom", token);
                                    int first = key.indexOf('/');
                                    int second = key.indexOf('/', first + 1);
                                    if (words.length > 3) {
                                        throw new IllegalArgumentException("Malformed @jenesis.bom declaration '"
                                                + content
                                                + "': expected <token> [<version> [<algorithm>/<hash>]]");
                                    }
                                    String version = words.length > 1 ? words[1] : "";
                                    if (version.startsWith(":")) {
                                        throw new IllegalArgumentException("Malformed @jenesis.bom version '"
                                                + version
                                                + "': a BOM cannot carry a classifier");
                                    }
                                    String checksum = words.length > 2 ? words[2] : "";
                                    if (!checksum.isEmpty() && !key.substring(first + 1, second).equals("module")) {
                                        throw new IllegalArgumentException("Malformed @jenesis.bom declaration '"
                                                + content
                                                + "': a Maven BOM cannot carry a checksum");
                                    }
                                    if (!checksum.isEmpty() && checksum.indexOf('/') < 1) {
                                        throw new IllegalArgumentException("Malformed @jenesis.bom checksum '"
                                                + checksum
                                                + "': expected <algorithm>/<hash>");
                                    }
                                    value = checksum.isEmpty() ? version : version + " " + checksum;
                                }
                                if (guard == null) {
                                    boms.put(key, value);
                                } else {
                                    bomVariants.computeIfAbsent(key, _ -> new LinkedHashMap<>()).put(guard, value);
                                }
                            }
                            case "jenesis.plugin" -> {
                                int space = content.indexOf(' ');
                                String group, token;
                                if (space > 0 && content.substring(0, space).indexOf('/') < 0) {
                                    group = content.substring(0, space).trim();
                                    token = content.substring(space + 1).trim();
                                } else {
                                    group = "plugin";
                                    token = content;
                                }
                                if (token.isEmpty()) {
                                    continue;
                                }
                                if (token.indexOf(' ') >= 0) {
                                    throw new IllegalArgumentException("Malformed @jenesis.plugin declaration '"
                                            + content
                                            + "': expected [<group>] <module> or [<group>] maven/<groupId>/<artifactId>,"
                                            + " with a version written into the coordinate, as"
                                            + " maven/<groupId>/<artifactId>/<version>, or left to pin");
                                }
                                String previous = plugins.put(token.indexOf('/') < 0 ? "module/" + token : token, group);
                                if (previous != null && !previous.equals(group)) {
                                    throw new IllegalArgumentException("Malformed @jenesis.plugin declaration '"
                                            + content
                                            + "': " + token + " is named in the group " + previous + " already,"
                                            + " and a coordinate resolves in one group of a module only");
                                }
                            }
                            case "jenesis.layer" -> {
                                if (content.isEmpty()) {
                                    continue;
                                }
                                String[] words = content.split(" ");
                                if (words.length == 3 && words[1].equals("api")) {
                                    String previous = layerApis.putIfAbsent(words[0], words[2]);
                                    if (previous != null && !previous.equals(words[2])) {
                                        throw new IllegalArgumentException("Layer "
                                                + words[0]
                                                + " names two API modules, "
                                                + previous
                                                + " and "
                                                + words[2]
                                                + ": a layer shares one module with its host");
                                    }
                                    layers.computeIfAbsent(words[0], _ -> new LinkedHashSet<>());
                                } else if (words.length == 3 && words[1].equals("provider")) {
                                    layers.computeIfAbsent(words[0], _ -> new LinkedHashSet<>())
                                            .add(layered(words[1], words[2]));
                                } else if (words.length == 3 && words[1].equals("native")) {
                                    natives.add(expand("jenesis.native", module.getName().toString()));
                                    natives.add("layer:" + words[0] + "/" + layered(words[1], words[2]));
                                } else {
                                    throw new IllegalArgumentException("Malformed @jenesis.layer declaration '"
                                            + content
                                            + "': expected <layer> api <module>,"
                                            + " <layer> provider <token> or <layer> native <token>");
                                }
                            }
                            case "jenesis.alias" -> {
                                String[] words = content.split(" ");
                                if (words.length != 2) {
                                    throw new IllegalArgumentException("Malformed @jenesis.alias declaration '"
                                            + content
                                            + "': expected <module-name> <groupId>/<artifactId>[/<type>]");
                                }
                                String alias = words[0];
                                if (ModuleGraph.isSystemModule(alias)) {
                                    throw new IllegalArgumentException("Illegal @jenesis.alias name '"
                                            + alias
                                            + "': platform modules cannot be aliased");
                                }
                                if (alias.indexOf('/') >= 0) {
                                    throw new IllegalArgumentException("Illegal @jenesis.alias name '"
                                            + alias
                                            + "': expected a module name");
                                }
                                String[] segments = words[1].split("/", -1);
                                if (segments.length == 4 && Arrays.stream(segments).noneMatch(String::isEmpty)) {
                                    throw new IllegalArgumentException("Malformed @jenesis.alias target '"
                                            + words[1]
                                            + "': an alias names an artifact and no classifier, which a pin selects"
                                            + " - write @jenesis.alias " + alias + " " + segments[0] + "/" + segments[1]
                                            + (segments[2].equals("jar") ? "" : "/" + segments[2])
                                            + " and @jenesis.pin " + segments[0] + "/" + segments[1] + " :" + segments[3]
                                            + "[:<version>]");
                                } else if (segments.length < 2 || segments.length > 3
                                        || Arrays.stream(segments).anyMatch(String::isEmpty)) {
                                    throw new IllegalArgumentException("Malformed @jenesis.alias target '"
                                            + words[1]
                                            + "': expected <groupId>/<artifactId>[/<type>]");
                                }
                                String value = words[1];
                                String previous = aliases.putIfAbsent(alias, value);
                                if (previous != null && !previous.equals(value)) {
                                    throw new IllegalArgumentException("Duplicate @jenesis.alias for "
                                            + alias
                                            + ": "
                                            + previous
                                            + " and "
                                            + value);
                                }
                            }
                            case "jenesis.exclude" -> {
                                String[] words = content.split(" ");
                                if (words.length < 2) {
                                    throw new IllegalArgumentException("Malformed @jenesis.exclude declaration '"
                                            + content
                                            + "': expected <module-name> <groupId>/<artifactId>...");
                                }
                                String excluded = words[0];
                                if (ModuleGraph.isSystemModule(excluded)) {
                                    throw new IllegalArgumentException("Illegal @jenesis.exclude module '"
                                            + excluded
                                            + "': platform modules resolve no dependencies");
                                }
                                if (excluded.indexOf('/') >= 0) {
                                    throw new IllegalArgumentException("Illegal @jenesis.exclude module '"
                                            + excluded
                                            + "': expected a module name");
                                }
                                SequencedSet<String> targets = excludes.computeIfAbsent(
                                        excluded, _ -> new LinkedHashSet<>());
                                for (int index = 1; index < words.length; index++) {
                                    String[] segments = words[index].split("/", -1);
                                    if (segments.length != 2 || Arrays.stream(segments).anyMatch(String::isEmpty)) {
                                        throw new IllegalArgumentException("Malformed @jenesis.exclude target '"
                                                + words[index]
                                                + "': expected <groupId>/<artifactId>");
                                    }
                                    targets.add(words[index]);
                                }
                            }
                            case "jenesis.override" -> {
                                String[] words = content.split(" ");
                                if (words.length < 2) {
                                    throw new IllegalArgumentException("Malformed @jenesis.override declaration '"
                                            + content
                                            + "': expected <module-name> <module-name>...");
                                }
                                for (String word : words) {
                                    if (ModuleGraph.isSystemModule(word)) {
                                        throw new IllegalArgumentException("Illegal @jenesis.override module '"
                                                + word
                                                + "': platform modules cannot be overridden or carry an override");
                                    }
                                    if (word.indexOf('/') >= 0) {
                                        throw new IllegalArgumentException("Illegal @jenesis.override module '"
                                                + word
                                                + "': expected a module name");
                                    }
                                }
                                SequencedSet<String> carriers = overrides.computeIfAbsent(
                                        words[0], _ -> new LinkedHashSet<>());
                                for (int index = 1; index < words.length; index++) {
                                    if (words[index].equals(words[0])) {
                                        throw new IllegalArgumentException("Illegal @jenesis.override for "
                                                + words[0]
                                                + ": a module cannot carry itself");
                                    }
                                    carriers.add(words[index]);
                                }
                            }
                            case "jenesis.attach" -> {
                                if (content.isEmpty()) {
                                    continue;
                                }
                                int split = content.indexOf(' ');
                                String token = split < 0 ? content : content.substring(0, split);
                                String arguments = split < 0 ? "" : content.substring(split + 1).trim();
                                if (ModuleGraph.isSystemModule(token)) {
                                    throw new IllegalArgumentException("Illegal @jenesis.attach token '"
                                            + token
                                            + "': platform modules cannot be attached");
                                }
                                String key = expand("jenesis.attach", token);
                                String previous = attachments.putIfAbsent(key, arguments);
                                if (previous != null && !previous.equals(arguments)) {
                                    throw new IllegalArgumentException("Duplicate @jenesis.attach for "
                                            + key
                                            + ": '"
                                            + previous
                                            + "' and '"
                                            + arguments
                                            + "'");
                                }
                            }
                            case "jenesis.native" -> {
                                if (content.isEmpty()) {
                                    throw new IllegalArgumentException("@jenesis.native of "
                                            + module.getName()
                                            + " names no module: name each module granted native access,"
                                            + " this one included, as @jenesis.native "
                                            + module.getName());
                                }
                                for (String token : content.split(" ")) {
                                    if (ModuleGraph.isSystemModule(token)) {
                                        throw new IllegalArgumentException("Illegal @jenesis.native token '"
                                                + token
                                                + "': platform modules cannot be granted native access");
                                    }
                                    if (token.startsWith("layer:")) {
                                        throw new IllegalArgumentException("Illegal @jenesis.native token '"
                                                + token
                                                + "': grant a module in a layer with @jenesis.layer <name> native <module>");
                                    }
                                    natives.add(expand("jenesis.native", token));
                                }
                            }
                            case "jenesis.release" -> {
                                if (!content.isEmpty()) {
                                    release = content;
                                }
                            }
                            case "jenesis.test" -> {
                                if (testOf != null) {
                                    throw new IllegalArgumentException("A second @jenesis.test '"
                                            + content
                                            + "' after @jenesis.test '"
                                            + (abstractTest ? "abstract" : testOf)
                                            + "': a test module tests one module - declare one tag, and"
                                            + " require any further module the tests need");
                                }
                                if (content.equals("abstract")) {
                                    abstractTest = true;
                                    testOf = "";
                                } else if (content.isEmpty() || SourceVersion.isName(content)) {
                                    testOf = content;
                                } else {
                                    throw new IllegalArgumentException("Malformed @jenesis.test value '"
                                            + content
                                            + "': expected no value, a module name, or 'abstract'");
                                }
                            }
                            case "jenesis.signature" -> {
                                int split = content.indexOf(' ');
                                if (split < 0) {
                                    String last = content.substring(content.lastIndexOf('/') + 1);
                                    if (!last.startsWith("signature-") || !last.endsWith(".properties")) {
                                        throw new IllegalArgumentException("Malformed @jenesis.signature declaration '"
                                                + content
                                                + "': expected <algorithm>/<fingerprint> <token>... or"
                                                + " [<group>/]signature-<name>.properties; a list that had to be"
                                                + " downloaded would itself need verifying");
                                    }
                                    int first = content.indexOf('/');
                                    String qualifier = first < 0 ? group : content.substring(0, first);
                                    if (qualifier.isEmpty() || first != content.lastIndexOf('/')) {
                                        throw new IllegalArgumentException("Malformed @jenesis.signature token '"
                                                + content
                                                + "': expected [<group>/]signature-<name>.properties");
                                    }
                                    signatures.putIfAbsent(qualifier + "/" + last, "");
                                    continue;
                                }
                                if (split < 1) {
                                    throw new IllegalArgumentException("Malformed @jenesis.signature declaration '"
                                            + content
                                            + "': expected <algorithm>/<fingerprint> <token>...");
                                }
                                String fingerprint = content.substring(0, split);
                                int slash = fingerprint.indexOf('/');
                                boolean identity = fingerprint.startsWith("Sigstore/");
                                if (slash < 1 || slash == fingerprint.length() - 1
                                        || (identity
                                                ? fingerprint.indexOf('/', slash + 1) < 0
                                                : slash != fingerprint.lastIndexOf('/'))) {
                                    throw new IllegalArgumentException("Malformed @jenesis.signature"
                                            + " fingerprint '" + fingerprint
                                            + "': expected <algorithm>/<fingerprint>, Sigstore/<host>/<path> for an identity a certificate names, or unsigned/missing or unsigned/ignored for a coordinate that publishes no signature."
                                            + " A tag owns every line below it until the next tag, so prose"
                                            + " written under a signature is read as part of it; move it above"
                                            + " the tag block");
                                }
                                String existing = signatures.get(fingerprint);
                                SequencedSet<String> tokens = new TreeSet<>();
                                if (existing != null && !existing.isEmpty()) {
                                    tokens.addAll(List.of(existing.split(" ")));
                                }
                                for (String token : content.substring(split + 1).split(" ")) {
                                    if (!COORDINATE.matcher(token).matches()) {
                                        throw new IllegalArgumentException("Malformed @jenesis.signature token '"
                                                + token
                                                + "' for " + fingerprint
                                                + ": expected <group>/<repo>/<coordinate>, <groupId>/<artifactId>"
                                                + " or <module>, optionally ending in /*."
                                                + " A tag owns every line below it until the next tag, so prose"
                                                + " written under a signature becomes one of its tokens; move it"
                                                + " above the tag block");
                                    }
                                    if (!token.endsWith("/*")) {
                                        tokens.add(expand("jenesis.signature", token));
                                    } else {
                                        String base = token.substring(0, token.length() - 2);
                                        tokens.add(base.indexOf('/') < 0
                                                ? group + "/maven/" + base + "/*"
                                                : base + "/*");
                                    }
                                }
                                signatures.put(fingerprint, String.join(" ", tokens));
                            }
                            case "jenesis.main" -> {
                                if (!content.isEmpty()) {
                                    main = content;
                                }
                            }
                        }
                    }
                }
            }
            return new ModuleInfo(module.getName().toString(),
                    release,
                    name,
                    description,
                    testOf,
                    abstractTest,
                    main,
                    dependencies,
                    runtimeDependencies,
                    plugins,
                    layerApis,
                    layers,
                    attachments,
                    natives,
                    aliases,
                    excludes,
                    overrides,
                    versions,
                    variants,
                    boms,
                    signatures,
                    bomVariants);
        }
        throw new IllegalArgumentException("Expected module-info.java to contain module information");
    }

    private static String text(List<? extends DocTree> trees) {
        StringBuilder text = new StringBuilder();
        for (DocTree tree : trees) {
            text.append(switch (tree) {
                case TextTree plain -> plain.getBody();
                case RawTextTree markdown -> markdown.getContent();
                case LiteralTree literal -> literal.getBody().getBody();
                case LinkTree link -> link.getLabel().isEmpty() ? link.getReference().getSignature() : text(link.getLabel());
                case EntityTree entity -> switch (entity.getName().toString()) {
                    case "amp" -> "&";
                    case "lt" -> "<";
                    case "gt" -> ">";
                    case "quot" -> "\"";
                    case "apos" -> "'";
                    case "nbsp" -> " ";
                    case String numeric when numeric.startsWith("#x") || numeric.startsWith("#X") ->
                            Character.toString(Integer.parseInt(numeric.substring(2), 16));
                    case String numeric when numeric.startsWith("#") -> Character.toString(Integer.parseInt(numeric.substring(1)));
                    case String named -> "&" + named + ";";
                };
                case StartElementTree element -> BLOCKS.contains(element.getName().toString().toLowerCase(Locale.ROOT)) ? "\n\n" : "";
                default -> "";
            });
        }
        return text.toString();
    }

    private static String layered(String kind, String token) {
        int slash = token.indexOf('/');
        if (slash == 0 || token.endsWith("/") || token.contains("//")) {
            throw new IllegalArgumentException("Malformed @jenesis.layer " + kind + " '"
                    + token
                    + "': expected <module> or <repository>/<coordinate>");
        }
        return slash < 0 ? "module/" + token : token;
    }

    private String expand(String tag, String token) {
        return expand(group, tag, token);
    }

    static String expand(String group, String tag, String token) {
        int firstSlash = token.indexOf('/');
        int secondSlash = firstSlash < 0 ? -1 : token.indexOf('/', firstSlash + 1);
        if (firstSlash < 0) {
            return group + "/module/" + token;
        } else if (secondSlash < 0) {
            if (firstSlash < 1 || firstSlash == token.length() - 1) {
                throw new IllegalArgumentException("Malformed @" + tag + " token '"
                        + token
                        + "': expected <module>, <groupId>/<artifactId>,"
                        + " or <group>/<repository>/<coordinate>");
            }
            return group + "/maven/" + token;
        } else {
            if (firstSlash < 1 || secondSlash == firstSlash + 1 || secondSlash == token.length() - 1) {
                throw new IllegalArgumentException("Malformed @" + tag + " token '"
                        + token
                        + "': expected <module>, <groupId>/<artifactId>,"
                        + " or <group>/<repository>/<coordinate>");
            }
            String first = token.substring(0, firstSlash), second = token.substring(firstSlash + 1, secondSlash);
            if (first.equals("maven")) {
                return group + "/" + token;
            }
            if (first.indexOf('.') >= 0 && !second.equals("maven") && !second.equals("module")) {
                throw new IllegalArgumentException("Malformed @" + tag + " token '"
                        + token
                        + "': " + first + " reads as a group and " + second + " as a repository, which"
                        + " names nothing - a Maven coordinate with a type or a classifier is written maven/"
                        + token + ", or <group>/maven/" + token + " for a group other than " + group);
            }
            return token;
        }
    }
}
