package build.jenesis.step;

import module java.base;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.BuildStepResult;
import build.jenesis.Environment;
import build.jenesis.Json;
import build.jenesis.Repository;
import build.jenesis.SequencedProperties;
import build.jenesis.maven.MavenDependencyKey;

public class OsvDownload implements BuildStep {

    private final URI endpoint;
    private final transient boolean insecure, offline;

    public OsvDownload() {
        this(URI.create("https://api.osv.dev"), false, false);
    }

    public static OsvDownload ofEnvironment(Environment environment) {
        return new OsvDownload(URI.create("https://api.osv.dev"),
                environment.flag("repository.insecure"),
                environment.flag("repository.offline"));
    }

    private OsvDownload(URI endpoint, boolean insecure, boolean offline) {
        this.endpoint = endpoint;
        this.insecure = insecure;
        this.offline = offline;
    }

    public OsvDownload endpoint(URI endpoint) {
        return new OsvDownload(endpoint, insecure, offline);
    }

    @Override
    public CompletionStage<BuildStepResult> apply(Executor executor,
                                                  BuildStepContext context,
                                                  SequencedMap<String, BuildStepArgument> arguments)
            throws IOException {
        SequencedSet<String> coordinateSet = new TreeSet<>();
        for (BuildStepArgument argument : arguments.values()) {
            if (argument.removed()) {
                continue;
            }
            Path index = argument.folder().resolve(DEPENDENCIES);
            if (!Files.exists(index)) {
                continue;
            }
            SequencedProperties dependencies = SequencedProperties.ofFiles(index);
            for (String key : dependencies.stringPropertyNames()) {
                String coordinate = mavenCoordinate(key);
                if (coordinate != null) {
                    coordinateSet.add(coordinate);
                }
            }
        }
        List<String> coordinates = new ArrayList<>(coordinateSet);
        SequencedProperties feed = new SequencedProperties();
        if (!coordinates.isEmpty()) {
            if (offline) {
                throw new IllegalStateException("Cannot ask " + endpoint + " for the advisories of "
                        + coordinates.size() + " dependencies while offline"
                        + " (unset -Djenesis.repository.offline, or set -Djenesis.compliance.vulnerability=false)");
            }
            List<List<String>> identifiers = identifiers(request(endpoint.resolve("/v1/querybatch"), queryBatch(coordinates), insecure));
            SequencedSet<String> distinct = new LinkedHashSet<>();
            for (List<String> ids : identifiers) {
                distinct.addAll(ids);
            }
            SequencedMap<String, CompletableFuture<String>> pending = new LinkedHashMap<>();
            for (String id : distinct) {
                pending.put(id, CompletableFuture.supplyAsync(() -> {
                    try {
                        return severity(request(endpoint.resolve("/v1/vulns/" + id), null, insecure));
                    } catch (IOException e) {
                        throw new CompletionException(e);
                    }
                }, executor));
            }
            SequencedMap<String, String> severityById = new LinkedHashMap<>();
            try {
                for (Map.Entry<String, CompletableFuture<String>> entry : pending.entrySet()) {
                    severityById.put(entry.getKey(), entry.getValue().join());
                }
            } catch (CompletionException e) {
                if (e.getCause() instanceof IOException cause) {
                    throw cause;
                }
                throw e;
            }
            for (int index = 0; index < coordinates.size(); index++) {
                List<String> ids = index < identifiers.size() ? identifiers.get(index) : List.of();
                if (ids.isEmpty()) {
                    continue;
                }
                List<String> entries = new ArrayList<>();
                for (String id : ids) {
                    entries.add(id + ":" + severityById.getOrDefault(id, ""));
                }
                feed.setProperty(coordinates.get(index), String.join(",", entries));
            }
        }
        feed.store(context.next().resolve("advisories.properties"));
        return CompletableFuture.completedStage(new BuildStepResult(true));
    }

    public static List<List<String>> identifiers(String response) {
        List<List<String>> result = new ArrayList<>();
        Object results = parse(response) instanceof Map<?, ?> map ? map.get("results") : null;
        if (!(results instanceof List<?> list)) {
            return result;
        }
        for (Object element : list) {
            List<String> ids = new ArrayList<>();
            if (element instanceof Map<?, ?> entry && entry.get("vulns") instanceof List<?> vulns) {
                for (Object vuln : vulns) {
                    if (vuln instanceof Map<?, ?> object && object.get("id") instanceof String id) {
                        ids.add(id);
                    }
                }
            }
            result.add(ids);
        }
        return result;
    }

    public static String severity(String response) {
        if (!(parse(response) instanceof Map<?, ?> map)) {
            return "";
        }
        if (map.get("database_specific") instanceof Map<?, ?> specific
                && specific.get("severity") instanceof String word
                && !word(word).isEmpty()) {
            return word(word);
        }
        double highest = -1.0;
        if (map.get("severity") instanceof List<?> entries) {
            for (Object entry : entries) {
                if (entry instanceof Map<?, ?> object && object.get("score") instanceof String vector) {
                    highest = Math.max(highest, cvss(vector));
                }
            }
        }
        return highest < 0 ? "" : band(highest);
    }

    private static String word(String value) {
        return switch (value.toUpperCase(Locale.ROOT)) {
            case "LOW" -> "LOW";
            case "MODERATE", "MEDIUM" -> "MEDIUM";
            case "HIGH" -> "HIGH";
            case "CRITICAL" -> "CRITICAL";
            default -> "";
        };
    }

    private static String band(double score) {
        if (score >= 9.0) {
            return "CRITICAL";
        }
        if (score >= 7.0) {
            return "HIGH";
        }
        if (score >= 4.0) {
            return "MEDIUM";
        }
        return score >= 0.1 ? "LOW" : "";
    }

    private static double cvss(String vector) {
        String trimmed = vector.trim();
        if (trimmed.startsWith("CVSS:3.")) {
            return cvss3(trimmed);
        }
        return trimmed.contains("Au:") ? cvss2(trimmed) : -1.0;
    }

    private static double cvss3(String vector) {
        Map<String, String> metric = metrics(vector);
        boolean changed = "C".equals(metric.get("S"));
        double av = switch (metric.getOrDefault("AV", "")) {
            case "N" -> 0.85; case "A" -> 0.62; case "L" -> 0.55; case "P" -> 0.2; default -> -1;
        };
        double ac = switch (metric.getOrDefault("AC", "")) {
            case "L" -> 0.77; case "H" -> 0.44; default -> -1;
        };
        double pr = switch (metric.getOrDefault("PR", "")) {
            case "N" -> 0.85;
            case "L" -> changed ? 0.68 : 0.62;
            case "H" -> changed ? 0.5 : 0.27;
            default -> -1;
        };
        double ui = switch (metric.getOrDefault("UI", "")) {
            case "N" -> 0.85; case "R" -> 0.62; default -> -1;
        };
        double c = impact3(metric.getOrDefault("C", ""));
        double i = impact3(metric.getOrDefault("I", ""));
        double a = impact3(metric.getOrDefault("A", ""));
        if (av < 0 || ac < 0 || pr < 0 || ui < 0 || c < 0 || i < 0 || a < 0) {
            return -1.0;
        }
        double iss = 1 - (1 - c) * (1 - i) * (1 - a);
        double impact = changed
                ? 7.52 * (iss - 0.029) - 3.25 * Math.pow(iss - 0.02, 15)
                : 6.42 * iss;
        if (impact <= 0) {
            return 0.0;
        }
        double exploitability = 8.22 * av * ac * pr * ui;
        long scaled = Math.round(Math.min((changed ? 1.08 : 1.0) * (impact + exploitability), 10) * 100_000);
        return scaled % 10_000 == 0 ? scaled / 100_000.0 : (Math.floorDiv(scaled, 10_000) + 1) / 10.0;
    }

    private static double impact3(String value) {
        return switch (value) {
            case "H" -> 0.56; case "L" -> 0.22; case "N" -> 0.0; default -> -1;
        };
    }

    private static double cvss2(String vector) {
        Map<String, String> metric = metrics(vector);
        double av = switch (metric.getOrDefault("AV", "")) {
            case "L" -> 0.395; case "A" -> 0.646; case "N" -> 1.0; default -> -1;
        };
        double ac = switch (metric.getOrDefault("AC", "")) {
            case "H" -> 0.35; case "M" -> 0.61; case "L" -> 0.71; default -> -1;
        };
        double au = switch (metric.getOrDefault("Au", "")) {
            case "M" -> 0.45; case "S" -> 0.56; case "N" -> 0.704; default -> -1;
        };
        double c = impact2(metric.getOrDefault("C", ""));
        double i = impact2(metric.getOrDefault("I", ""));
        double a = impact2(metric.getOrDefault("A", ""));
        if (av < 0 || ac < 0 || au < 0 || c < 0 || i < 0 || a < 0) {
            return -1.0;
        }
        double impact = 10.41 * (1 - (1 - c) * (1 - i) * (1 - a));
        double exploitability = 20 * av * ac * au;
        double base = ((0.6 * impact) + (0.4 * exploitability) - 1.5) * (impact == 0 ? 0 : 1.176);
        return Math.round(base * 10.0) / 10.0;
    }

    private static double impact2(String value) {
        return switch (value) {
            case "N" -> 0.0; case "P" -> 0.275; case "C" -> 0.660; default -> -1;
        };
    }

    private static Map<String, String> metrics(String vector) {
        Map<String, String> metric = new HashMap<>();
        for (String part : vector.split("/")) {
            int colon = part.indexOf(':');
            if (colon > 0) {
                metric.put(part.substring(0, colon), part.substring(colon + 1));
            }
        }
        return metric;
    }

    private static Object parse(String response) {
        try {
            return Json.parse(response);
        } catch (RuntimeException _) {
            return null;
        }
    }

    private static String mavenCoordinate(String key) {
        int first = key.indexOf('/');
        int second = first < 0 ? -1 : key.indexOf('/', first + 1);
        int third = second < 0 ? -1 : key.indexOf('/', second + 1);
        if (third < 0 || !key.substring(second + 1, third).equals("maven")) {
            return null;
        }
        try {
            MavenDependencyKey.Versioned parsed = MavenDependencyKey.tryParse(key.substring(third + 1));
            if (parsed.version() == null) {
                return null;
            }
            return parsed.key().groupId() + "/" + parsed.key().artifactId() + "/" + parsed.version();
        } catch (RuntimeException _) {
            return null;
        }
    }

    public static String queryBatch(List<String> coordinates) {
        StringBuilder builder = new StringBuilder("{\"queries\":[");
        for (int index = 0; index < coordinates.size(); index++) {
            String[] parts = coordinates.get(index).split("/");
            if (index > 0) {
                builder.append(",");
            }
            builder.append("{\"package\":{\"ecosystem\":\"Maven\",\"name\":\"")
                    .append(Json.escaped(parts[0])).append(":").append(Json.escaped(parts[1]))
                    .append("\"},\"version\":\"").append(Json.escaped(parts[2])).append("\"}");
        }
        return builder.append("]}").toString();
    }

    private static String request(URI uri, String body, boolean insecure) throws IOException {
        requireSecure(uri, insecure);
        for (int attempt = 0; ; attempt++) {
            HttpURLConnection http = (HttpURLConnection) Repository.connect(uri, insecure);
            http.setRequestProperty("User-Agent", "Jenesis");
            http.setConnectTimeout(10_000);
            http.setReadTimeout(30_000);
            if (body != null) {
                http.setRequestMethod("POST");
                http.setRequestProperty("Content-Type", "application/json");
                http.setDoOutput(true);
                try (OutputStream out = http.getOutputStream()) {
                    out.write(body.getBytes(StandardCharsets.UTF_8));
                }
            }
            int status = http.getResponseCode();
            if (status == 200) {
                try (InputStream in = http.getInputStream()) {
                    return new String(in.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
            long delay = Repository.retryAfterMillis(http.getHeaderField("Retry-After"), 500L << Math.min(attempt, 10));
            InputStream error = http.getErrorStream();
            if (error != null) {
                try (error) {
                    error.readAllBytes();
                } catch (IOException _) {
                }
            }
            if ((status == 429 || status >= 500) && attempt < 3) {
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted while backing off from OSV " + uri, e);
                }
                continue;
            }
            throw new IOException("OSV request to " + uri + " failed with status " + status);
        }
    }

    private static void requireSecure(URI uri, boolean insecure) {
        String scheme = uri.getScheme();
        if (scheme != null && !scheme.equals("https") && !insecure) {
            throw new IllegalStateException("Refusing to query OSV over insecure scheme '"
                    + scheme
                    + "': "
                    + uri
                    + " (set -Djenesis.repository.insecure=true to allow plaintext endpoints)");
        }
    }

}
