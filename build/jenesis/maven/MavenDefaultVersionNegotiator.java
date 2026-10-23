package build.jenesis.maven;

import module java.base;
import module java.xml;

import static build.jenesis.maven.MavenPomResolver.missing;
import static build.jenesis.maven.MavenPomResolver.toChildren;

public class MavenDefaultVersionNegotiator implements MavenVersionNegotiator {

    private final transient DocumentBuilderFactory documentBuilderFactory;
    private final transient Map<MavenDependencyName, Metadata> cache = new HashMap<>();

    private MavenDefaultVersionNegotiator(DocumentBuilderFactory documentBuilderFactory) {
        this.documentBuilderFactory = documentBuilderFactory;
    }

    static DocumentBuilderFactory toDocumentBuilderFactory() {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        try {
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        } catch (ParserConfigurationException e) {
            throw new IllegalStateException(e);
        }
        return factory;
    }

    @SuppressWarnings("unchecked")
    public static <S extends Supplier<MavenVersionNegotiator> & Serializable> S maven() {
        return (S) (Supplier<MavenVersionNegotiator> & Serializable) () -> new MavenDefaultVersionNegotiator(toDocumentBuilderFactory()) {
            @Override
            public String resolve(Executor executor,
                                  MavenRepository repository,
                                  String groupId,
                                  String artifactId,
                                  String type,
                                  String classifier,
                                  String current,
                                  SequencedSet<String> versions) throws IOException {
                List<List<Restriction>> hardRequirements = new ArrayList<>();
                for (String version : versions) {
                    if (isRange(version)) {
                        hardRequirements.add(parseRanges(version));
                    }
                }
                if (hardRequirements.isEmpty()) {
                    return current;
                }
                return toMetadata(executor, repository, groupId, artifactId).versions().stream()
                        .filter(candidate -> hardRequirements.stream().allMatch(restrictions ->
                                restrictions.stream().anyMatch(restriction -> restriction.contains(candidate))))
                        .max(MavenDefaultVersionNegotiator::compareVersions)
                        .orElseThrow(() -> new IllegalStateException(
                                "Could not resolve version conflict for " + groupId + ":" + artifactId
                                        + " among " + versions));
            }
        };
    }

    @SuppressWarnings("unchecked")
    public static <S extends Supplier<MavenVersionNegotiator> & Serializable> S latest() {
        return (S) (Supplier<MavenVersionNegotiator> & Serializable) () -> new MavenDefaultVersionNegotiator(toDocumentBuilderFactory()) {
            @Override
            public String resolve(Executor executor,
                                  MavenRepository repository,
                                  String groupId,
                                  String artifactId,
                                  String type,
                                  String classifier,
                                  String version) throws IOException {
                return toMetadata(executor, repository, groupId, artifactId).latest();
            }
        };
    }

    @SuppressWarnings("unchecked")
    public static <S extends Supplier<MavenVersionNegotiator> & Serializable> S release() {
        return (S) (Supplier<MavenVersionNegotiator> & Serializable) () -> new MavenDefaultVersionNegotiator(toDocumentBuilderFactory()) {
            @Override
            public String resolve(Executor executor,
                                  MavenRepository repository,
                                  String groupId,
                                  String artifactId,
                                  String type,
                                  String classifier,
                                  String version) throws IOException {
                return toMetadata(executor, repository, groupId, artifactId).release();
            }
        };
    }

    @SuppressWarnings("unchecked")
    public static <S extends Supplier<MavenVersionNegotiator> & Serializable> S stable() {
        return (S) (Supplier<MavenVersionNegotiator> & Serializable) () -> new MavenDefaultVersionNegotiator(toDocumentBuilderFactory()) {
            @Override
            public String resolve(Executor executor,
                                  MavenRepository repository,
                                  String groupId,
                                  String artifactId,
                                  String type,
                                  String classifier,
                                  String version) throws IOException {
                return toStable(toMetadata(executor, repository, groupId, artifactId), groupId, artifactId);
            }
        };
    }

    @SuppressWarnings("unchecked")
    public static <S extends Supplier<MavenVersionNegotiator> & Serializable> S closest() {
        return (S) (Supplier<MavenVersionNegotiator> & Serializable) () -> new MavenDefaultVersionNegotiator(toDocumentBuilderFactory());
    }

    @SuppressWarnings("unchecked")
    public static <S extends Supplier<MavenVersionNegotiator> & Serializable> S fail() {
        return (S) (Supplier<MavenVersionNegotiator> & Serializable) () -> new MavenDefaultVersionNegotiator(toDocumentBuilderFactory()) {
            @Override
            public String resolve(Executor executor,
                                  MavenRepository repository,
                                  String groupId,
                                  String artifactId,
                                  String type,
                                  String classifier,
                                  String current,
                                  SequencedSet<String> versions) {
                throw diverging(groupId, artifactId, type, classifier, versions);
            }
        };
    }

    @SuppressWarnings("unchecked")
    public static <S extends Supplier<MavenVersionNegotiator> & Serializable> S managed() {
        return (S) (Supplier<MavenVersionNegotiator> & Serializable) () -> new MavenDefaultVersionNegotiator(toDocumentBuilderFactory()) {
            @Override
            public String resolve(Executor executor,
                                  MavenRepository repository,
                                  String groupId,
                                  String artifactId,
                                  String type,
                                  String classifier,
                                  String current,
                                  SequencedSet<String> versions) {
                throw diverging(groupId, artifactId, type, classifier, versions);
            }

            @Override
            public void discovered(String groupId,
                                   String artifactId,
                                   String type,
                                   String classifier,
                                   String version,
                                   boolean managed) {
                if (!managed) {
                    throw new IllegalStateException("No managed version for "
                            + coordinate(groupId, artifactId, type, classifier)
                            + " which resolved to "
                            + version
                            + " as another dependency's POM declares it"
                            + " (add it to dependencyManagement, or run the pin selector)");
                }
            }
        };
    }

    private static IllegalStateException diverging(String groupId,
                                                   String artifactId,
                                                   String type,
                                                   String classifier,
                                                   SequencedSet<String> versions) {
        return new IllegalStateException("Diverging versions "
                + versions
                + " required for "
                + coordinate(groupId, artifactId, type, classifier)
                + " (add it to dependencyManagement, or run the pin selector)");
    }

    private static String coordinate(String groupId, String artifactId, String type, String classifier) {
        return groupId
                + ":" + artifactId
                + (type == null || type.equals("jar") ? "" : ":" + type)
                + (classifier == null ? "" : ":" + classifier);
    }

    @Override
    public String resolve(Executor executor,
                          MavenRepository repository,
                          String groupId,
                          String artifactId,
                          String type,
                          String classifier,
                          String version) throws IOException {
        return switch (version) {
            case "RELEASE" -> {
                Metadata metadata = toMetadata(executor, repository, groupId, artifactId);
                String release = metadata.release();
                yield isStable(release) ? release : metadata.versions().stream()
                        .filter(MavenDefaultVersionNegotiator::isStable)
                        .max(MavenDefaultVersionNegotiator::compareVersions)
                        .orElse(release);
            }
            case "LATEST" -> toMetadata(executor, repository, groupId, artifactId).latest();
            case "STABLE" -> toStable(toMetadata(executor, repository, groupId, artifactId), groupId, artifactId);
            case String range when isRange(range) -> {
                List<Restriction> restrictions = parseRanges(range);
                yield toMetadata(executor, repository, groupId, artifactId).versions().stream()
                        .filter(candidate -> restrictions.stream().anyMatch(r -> r.contains(candidate)))
                        .max(MavenDefaultVersionNegotiator::compareVersions)
                        .orElseThrow(() -> new IllegalStateException("Could not resolve version in range: " + version));
            }
            default -> version;
        };
    }

    static boolean isRange(String version) {
        return !version.isEmpty()
                && (version.charAt(0) == '[' || version.charAt(0) == '(')
                && (version.charAt(version.length() - 1) == ']' || version.charAt(version.length() - 1) == ')');
    }

    static List<Restriction> parseRanges(String input) {
        List<Restriction> result = new ArrayList<>();
        int index = 0;
        while (index < input.length()) {
            while (index < input.length() && (input.charAt(index) == ',' || Character.isWhitespace(input.charAt(index)))) {
                index++;
            }
            if (index >= input.length()) {
                break;
            }
            char open = input.charAt(index);
            if (open != '[' && open != '(') {
                throw new IllegalArgumentException("Invalid version range: " + input);
            }
            int close = index + 1;
            while (close < input.length() && input.charAt(close) != ']' && input.charAt(close) != ')') {
                close++;
            }
            if (close >= input.length()) {
                throw new IllegalArgumentException("Unclosed version range: " + input);
            }
            String value = input.substring(index + 1, close).trim();
            char closeChar = input.charAt(close);
            String lower, upper;
            int comma = value.indexOf(',');
            if (comma == -1) {
                lower = upper = value;
            } else {
                lower = value.substring(0, comma).trim();
                upper = value.substring(comma + 1).trim();
            }
            result.add(new Restriction(
                    lower.isEmpty() ? null : lower,
                    open == '[',
                    upper.isEmpty() ? null : upper,
                    closeChar == ']'));
            index = close + 1;
        }
        if (result.isEmpty()) {
            throw new IllegalArgumentException("Invalid version range: " + input);
        }
        return result;
    }

    record Restriction(String lower,
                       boolean lowerInclusive,
                       String upper,
                       boolean upperInclusive) {
        boolean contains(String version) {
            if (lower != null) {
                int c = compareVersions(lower, version);
                if (lowerInclusive ? c > 0 : c >= 0) {
                    return false;
                }
            }
            if (upper != null) {
                int c = compareVersions(version, upper);
                if (upperInclusive ? c > 0 : c >= 0) {
                    return false;
                }
            }
            return true;
        }
    }

    Metadata toMetadata(Executor executor,
                        MavenRepository repository,
                        String groupId,
                        String artifactId) throws IOException {
        Metadata metadata = cache.get(new MavenDependencyName(groupId, artifactId));
        if (metadata == null) {
            Document document;
            try (InputStream inputStream = repository.fetchMetadata(executor, groupId, artifactId, null)
                    .orElseThrow(() -> new IllegalStateException("No metadata for " + groupId + ":" + artifactId))
                    .toInputStream()) {
                document = documentBuilderFactory.newDocumentBuilder().parse(inputStream);
            } catch (SAXException | ParserConfigurationException e) {
                throw new IllegalStateException(e);
            }
            metadata = switch (document.getDocumentElement().getAttribute("modelVersion")) {
                case "", "1.1.0" -> {
                    Node versioning = toChildren(document.getDocumentElement())
                            .filter(node -> Objects.equals(node.getNodeName(), "versioning"))
                            .findFirst()
                            .orElseThrow(missing("versioning"));
                    yield new Metadata(
                            toChildren(versioning)
                                    .filter(node -> Objects.equals(node.getNodeName(), "latest"))
                                    .findFirst()
                                    .map(Node::getTextContent)
                                    .orElse(null),
                            toChildren(versioning)
                                    .filter(node -> Objects.equals(node.getNodeName(), "release"))
                                    .findFirst()
                                    .map(Node::getTextContent)
                                    .orElse(null),
                            toChildren(versioning)
                                    .filter(node -> Objects.equals(node.getNodeName(), "versions"))
                                    .findFirst()
                                    .stream()
                                    .flatMap(MavenPomResolver::toChildren)
                                    .filter(node -> Objects.equals(node.getNodeName(), "version"))
                                    .map(Node::getTextContent)
                                    .toList());
                }
                case null, default -> throw new IllegalStateException("Unknown model version: " +
                        document.getDocumentElement().getAttribute("modelVersion"));
            };
            cache.put(new MavenDependencyName(groupId, artifactId), metadata);
        }
        return metadata;
    }

    public static int compareVersions(String left, String right) {
        return compareItems(parseVersion(left), parseVersion(right));
    }

    public static boolean isStable(String version) {
        return isStable(new ListItem(parseVersion(version)));
    }

    private static boolean isStable(Item item) {
        return switch (item) {
            case IntegerItem _ -> true;
            case StringItem stringItem -> !PRERELEASE.contains(letters(stringItem.value()))
                    && comparableQualifier(stringItem.value()).compareTo(RELEASE_INDEX) >= 0;
            case ListItem listItem -> listItem.items().stream()
                    .allMatch(MavenDefaultVersionNegotiator::isStable);
        };
    }

    private static String letters(String value) {
        int end = 0;
        while (end < value.length() && Character.isLetter(value.charAt(end))) {
            end++;
        }
        return value.substring(0, end);
    }

    private static String toStable(Metadata metadata, String groupId, String artifactId) {
        return metadata.versions().stream()
                .filter(MavenDefaultVersionNegotiator::isStable)
                .max(MavenDefaultVersionNegotiator::compareVersions)
                .orElseThrow(() -> new IllegalStateException("No stable version of "
                        + groupId + ":" + artifactId
                        + " among " + metadata.versions()
                        + ": every published version carries a pre-release qualifier"));
    }

    private static final List<String> QUALIFIERS = List.of(
            "alpha", "beta", "milestone", "rc", "snapshot", "", "sp");
    private static final String RELEASE_INDEX = String.valueOf(QUALIFIERS.indexOf(""));
    private static final Map<String, String> ALIASES = Map.of(
            "ga", "", "final", "", "release", "", "cr", "rc");
    private static final Set<String> PRERELEASE = Set.of(
            "ea", "pre", "prerelease", "preview", "dev", "nightly", "canary", "next", "test", "adhoc");

    private static String comparableQualifier(String value) {
        int index = QUALIFIERS.indexOf(value);
        return index >= 0 ? Integer.toString(index) : QUALIFIERS.size() + "-" + value;
    }

    private sealed interface Item permits IntegerItem, StringItem, ListItem {
        boolean isNull();
    }

    private record IntegerItem(BigInteger value) implements Item {
        static final IntegerItem ZERO = new IntegerItem(BigInteger.ZERO);

        @Override
        public boolean isNull() {
            return value.signum() == 0;
        }
    }

    private record StringItem(String value) implements Item {
        static StringItem of(String raw, boolean followedByDigit) {
            String expanded = followedByDigit && raw.length() == 1
                    ? switch (raw.charAt(0)) {
                        case 'a' -> "alpha";
                        case 'b' -> "beta";
                        case 'm' -> "milestone";
                        default -> raw;
                    }
                    : raw;
            return new StringItem(ALIASES.getOrDefault(expanded, expanded));
        }

        @Override
        public boolean isNull() {
            return comparableQualifier(value).equals(RELEASE_INDEX);
        }
    }

    private record ListItem(List<Item> items) implements Item {
        @Override
        public boolean isNull() {
            return items.isEmpty();
        }
    }

    private static List<Item> parseVersion(String version) {
        String lowered = version.toLowerCase(Locale.ENGLISH);
        List<List<Item>> stack = new ArrayList<>();
        List<Item> root = new ArrayList<>();
        stack.add(root);
        List<Item> current = root;
        boolean isDigit = false;
        int start = 0;
        for (int index = 0; index < lowered.length(); index++) {
            char c = lowered.charAt(index);
            if (c == '.') {
                if (index == start) {
                    current.add(IntegerItem.ZERO);
                } else {
                    current.add(parseSegment(isDigit, lowered.substring(start, index), false));
                }
                start = index + 1;
            } else if (c == '-') {
                if (index == start) {
                    current.add(IntegerItem.ZERO);
                } else {
                    current.add(parseSegment(isDigit, lowered.substring(start, index), false));
                }
                start = index + 1;
                List<Item> nested = new ArrayList<>();
                current.add(new ListItem(nested));
                current = nested;
                stack.add(nested);
            } else if (Character.isDigit(c)) {
                if (!isDigit && index > start) {
                    current.add(StringItem.of(lowered.substring(start, index), true));
                    start = index;
                    List<Item> nested = new ArrayList<>();
                    current.add(new ListItem(nested));
                    current = nested;
                    stack.add(nested);
                }
                isDigit = true;
            } else {
                if (isDigit && index > start) {
                    current.add(parseSegment(true, lowered.substring(start, index), true));
                    start = index;
                    List<Item> nested = new ArrayList<>();
                    current.add(new ListItem(nested));
                    current = nested;
                    stack.add(nested);
                }
                isDigit = false;
            }
        }
        if (lowered.length() > start) {
            current.add(parseSegment(isDigit, lowered.substring(start), false));
        }
        for (int j = stack.size() - 1; j >= 0; j--) {
            List<Item> items = stack.get(j);
            for (int index = items.size() - 1; index >= 0; index--) {
                Item item = items.get(index);
                if (item.isNull()) {
                    items.remove(index);
                } else if (!(item instanceof ListItem)) {
                    break;
                }
            }
        }
        return root;
    }

    private static Item parseSegment(boolean isDigit, String text, boolean followedByDigit) {
        if (isDigit) {
            int firstNonZero = 0;
            while (firstNonZero < text.length() - 1 && text.charAt(firstNonZero) == '0') {
                firstNonZero++;
            }
            return new IntegerItem(new BigInteger(text.substring(firstNonZero)));
        }
        return StringItem.of(text, followedByDigit);
    }

    private static int compareItems(List<Item> left, List<Item> right) {
        int n = Math.max(left.size(), right.size());
        for (int index = 0; index < n; index++) {
            Item l = index < left.size() ? left.get(index) : null;
            Item r = index < right.size() ? right.get(index) : null;
            int result;
            if (l == null && r == null) {
                result = 0;
            } else if (l == null) {
                result = -compareToNull(r);
            } else if (r == null) {
                result = compareToNull(l);
            } else {
                result = compareItem(l, r);
            }
            if (result != 0) {
                return result;
            }
        }
        return 0;
    }

    private static int compareItem(Item left, Item right) {
        return switch (left) {
            case IntegerItem(BigInteger leftValue) -> switch (right) {
                case IntegerItem(BigInteger rightValue) -> leftValue.compareTo(rightValue);
                case StringItem _, ListItem _ -> 1;
            };
            case StringItem(String leftValue) -> switch (right) {
                case IntegerItem _ -> -1;
                case StringItem(String rightValue) -> comparableQualifier(leftValue).compareTo(comparableQualifier(rightValue));
                case ListItem _ -> -1;
            };
            case ListItem(List<Item> leftItems) -> switch (right) {
                case IntegerItem _ -> -1;
                case StringItem _ -> 1;
                case ListItem(List<Item> rightItems) -> compareItems(leftItems, rightItems);
            };
        };
    }

    private static int compareToNull(Item item) {
        return switch (item) {
            case IntegerItem integerItem -> integerItem.isNull() ? 0 : 1;
            case StringItem stringItem -> comparableQualifier(stringItem.value()).compareTo(RELEASE_INDEX);
            case ListItem listItem -> listItem.items().isEmpty() ? 0 : compareToNull(listItem.items().get(0));
        };
    }

    private record Metadata(String latest, String release, List<String> versions) {
        @Override
        public String latest() {
            if (latest == null) {
                throw new IllegalStateException("Property not defined: latest");
            }
            return latest;
        }

        @Override
        public String release() {
            if (release == null) {
                throw new IllegalStateException("Property not defined: release");
            }
            return release;
        }
    }
}
