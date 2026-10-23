package build.jenesis;

import module java.base;
import build.jenesis.maven.MavenDefaultVersionNegotiator;
import build.jenesis.maven.MavenMetadata;

public record DiscoveredLocation(String domain,
                                 URI source,
                                 String key,
                                 String target,
                                 String since,
                                 List<String> suffixes,
                                 String latest) {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([^{}]*)}");
    private static final Pattern VERSION = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._+-]{0,127}");
    private static final String CLASSIFIER = "-classifier", TYPE = "type";
    private static final String METADATA = "/maven-metadata.xml";
    private static final List<Map.Entry<String, String>> CHECKSUMS = List.of(Map.entry("sha512", "SHA-512"),
            Map.entry("sha256", "SHA-256"),
            Map.entry("sha1", "SHA-1"));

    public boolean admits(String version) {
        if (version == null) {
            return since == null && suffixes == null;
        }
        if (since != null && MavenDefaultVersionNegotiator.compareVersions(version, since) < 0) {
            return false;
        }
        if (suffixes == null) {
            return true;
        }
        int dash = version.indexOf('-');
        if (dash < 0) {
            return suffixes.contains("none");
        }
        String qualifier = version.substring(dash + 1).toLowerCase(Locale.ROOT);
        for (String suffix : suffixes) {
            if (qualifier.startsWith(suffix) && (qualifier.length() == suffix.length()
                    || !Character.isLetter(qualifier.charAt(suffix.length())))) {
                return true;
            }
        }
        return false;
    }

    public boolean coordinate() {
        return !target.contains("://");
    }

    public boolean template() {
        return PLACEHOLDER.matcher(target).find();
    }

    public String suffix(String namespace) {
        String[] labels = namespace.split("\\.");
        int below = domain.split("\\.").length;
        if (below > labels.length
                || !domain.equals(Discovery.domain(String.join(".", Arrays.copyOf(labels, below))))) {
            throw new IllegalArgumentException(namespace + " is not below the domain " + domain + " of " + source);
        }
        return below == labels.length
                ? ""
                : "-" + String.join("-", Arrays.copyOfRange(labels, below, labels.length));
    }

    public URI root(Repository.Connection connection) {
        if (template()) {
            throw new IllegalStateException(key + " in " + source + " names a template, not a root: " + target);
        }
        return secured(URI.create(target.endsWith("/") ? target : target + "/"), connection);
    }

    public Optional<URI> resolve(Map<String, String> values, Repository.Connection connection) {
        return expand(values).map(text -> secured(URI.create(text), connection));
    }

    public Optional<String> expand(Map<String, String> values) {
        Optional<String> expanded = fill(target, key, values);
        if (expanded.isPresent()
                && (!target.contains("{" + CLASSIFIER + "}") && values.get(CLASSIFIER) != null
                && !values.get(CLASSIFIER).isEmpty()
                || !target.contains("{" + TYPE + "}") && values.get(TYPE) != null && !values.get(TYPE).equals("jar"))) {
            return Optional.empty();
        }
        return expanded;
    }

    public boolean listsVersions() {
        return latest != null && latest.endsWith(METADATA);
    }

    public Optional<String> latest(Map<String, String> values, Repository.Connection connection) throws IOException {
        if (latest == null) {
            return Optional.empty();
        }
        URI uri = secured(URI.create(fill(latest, key + ".latest", values).orElseThrow()), connection);
        String version = listsVersions()
                ? metadata(values, connection).map(MavenMetadata::release).orElse(null)
                : redirected(uri, values, connection);
        if (version != null && !VERSION.matcher(version).matches()) {
            throw new IllegalArgumentException(key + ".latest in " + source + " points to " + uri
                    + ", which names '" + version + "' where it should name the newest version, such as 1.2.3");
        }
        return Optional.ofNullable(version);
    }

    public Optional<MavenMetadata> metadata(Map<String, String> values, Repository.Connection connection)
            throws IOException {
        if (!listsVersions()) {
            return Optional.empty();
        }
        byte[] bytes = read(secured(URI.create(fill(latest, key + ".latest", values).orElseThrow()), connection),
                connection);
        return bytes == null
                ? Optional.empty()
                : Optional.of(MavenMetadata.of(() -> new ByteArrayInputStream(bytes)).filter(this::admits));
    }

    public Optional<RepositoryItem> fetch(Map<String, String> values,
                                          boolean validate,
                                          Repository.Connection connection,
                                          Consumer<String> printing,
                                          Palette palette) throws IOException {
        URI location = resolve(values, connection).orElse(null);
        byte[] bytes = location == null ? null : read(location, connection);
        if (bytes == null) {
            return Optional.empty();
        }
        if (validate && values.get(TYPE) != null && target.contains("{" + TYPE + "}")) {
            for (Map.Entry<String, String> checksum : CHECKSUMS) {
                Map<String, String> sibling = new HashMap<>(values);
                sibling.put(TYPE, values.get(TYPE) + "." + checksum.getKey());
                byte[] published = read(resolve(sibling, connection).orElseThrow(), connection);
                if (published == null) {
                    continue;
                }
                String expected = new String(published, StandardCharsets.US_ASCII).strip().split("\\s+")[0];
                String actual;
                try {
                    actual = HexFormat.of().formatHex(MessageDigest.getInstance(checksum.getValue()).digest(bytes));
                } catch (NoSuchAlgorithmException e) {
                    throw new IllegalStateException("The JDK offers no " + checksum.getValue(), e);
                }
                if (!actual.equalsIgnoreCase(expected)) {
                    throw new IllegalStateException("Failed checksum validation for " + location
                            + " against its " + checksum.getValue() + " checksum, named by " + key + " in " + source);
                }
                break;
            }
        }
        if (printing != null) {
            printing.accept("%s%-11s%s %s".formatted(palette.info(), "[FETCHED]", palette.reset(), location));
        }
        return Optional.of(() -> new ByteArrayInputStream(bytes));
    }

    private String redirected(URI uri, Map<String, String> values, Repository.Connection connection)
            throws IOException {
        HttpURLConnection http = (HttpURLConnection) Repository.connect(uri, connection.insecure());
        http.setRequestMethod("HEAD");
        http.setInstanceFollowRedirects(false);
        http.setRequestProperty("User-Agent", "Jenesis");
        http.setConnectTimeout(connection.connectTimeout());
        http.setReadTimeout(connection.readTimeout());
        try {
            int status = http.getResponseCode();
            if (status == HttpURLConnection.HTTP_NOT_FOUND || status == HttpURLConnection.HTTP_GONE) {
                return null;
            }
            String announced = http.getHeaderField(key.startsWith("maven")
                    ? "Jenesis-MavenVersion"
                    : "Jenesis-ModuleVersion");
            String redirect = http.getHeaderField("Location");
            if (announced != null) {
                return announced.strip();
            }
            if (status / 100 != 3 || redirect == null) {
                throw new IllegalArgumentException(key + ".latest in " + source + " points to " + uri
                        + ", which answered " + status + " without redirecting to the newest version or naming it");
            }
            String destination = uri.resolve(redirect).toString();
            int marker = target.indexOf("{version}"), boundary = target.indexOf('/', marker);
            String shape = target.substring(0, boundary < 0 ? target.length() : boundary);
            StringBuilder pattern = new StringBuilder();
            Matcher matcher = PLACEHOLDER.matcher(shape);
            int last = 0;
            while (matcher.find()) {
                pattern.append(Pattern.quote(shape.substring(last, matcher.start())));
                String placeholder = matcher.group(1);
                if (placeholder.equals("version")) {
                    pattern.append(matcher.start() == marker ? "(?<version>[A-Za-z0-9._+-]+)" : "\\k<version>");
                } else if (values.get(placeholder) != null) {
                    pattern.append(Pattern.quote(values.get(placeholder)));
                } else if (placeholder.equals(CLASSIFIER) || placeholder.equals(TYPE)) {
                    pattern.append("[^/?#]*");
                } else {
                    throw new IllegalArgumentException(key + " in " + source + " uses the placeholder {"
                            + placeholder + "}, which a request without a version cannot fill");
                }
                last = matcher.end();
            }
            pattern.append(Pattern.quote(shape.substring(last))).append("(?:[/?#].*)?");
            Matcher matched = Pattern.compile(pattern.toString()).matcher(destination);
            if (!matched.matches()) {
                throw new IllegalArgumentException(key + ".latest in " + source + " points to " + uri
                        + ", which leads to " + destination + " where " + key + " expects " + shape);
            }
            return matched.group("version");
        } finally {
            http.disconnect();
        }
    }

    private Optional<String> fill(String template, String name, Map<String, String> values) {
        Matcher matcher = PLACEHOLDER.matcher(template);
        while (matcher.find()) {
            String placeholder = matcher.group(1);
            if (!values.containsKey(placeholder)) {
                throw new IllegalArgumentException(name + " in " + source + " uses the placeholder {"
                        + placeholder + "}, but " + name + " knows only "
                        + values.keySet().stream().sorted().map(key -> "{" + key + "}").toList());
            }
            if (values.get(placeholder) == null) {
                return Optional.empty();
            }
        }
        return Optional.of(matcher.reset().replaceAll(result -> Matcher.quoteReplacement(values.get(result.group(1)))));
    }

    private URI secured(URI uri, Repository.Connection connection) {
        if (!"https".equals(uri.getScheme()) && !("http".equals(uri.getScheme()) && connection.insecure())) {
            throw new IllegalStateException(key + " in " + source + " points to " + uri
                    + ", but a location a domain names is only read over https"
                    + " (set -Djenesis.repository.insecure=true to allow plaintext http)");
        }
        return uri;
    }

    private static byte[] read(URI uri, Repository.Connection connection) throws IOException {
        try (InputStream inputStream = Repository.open(connection, uri, null)) {
            return inputStream.readAllBytes();
        } catch (FileNotFoundException _) {
            return null;
        }
    }
}
