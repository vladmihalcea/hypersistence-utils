package build.jenesis.step;

import module java.base;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.BuildStepResult;
import build.jenesis.Environment;
import build.jenesis.KeyExpiry;
import build.jenesis.Palette;
import build.jenesis.Repository;
import build.jenesis.RepositoryItem;
import build.jenesis.Resolver;
import build.jenesis.SequencedProperties;
import build.jenesis.Sigstore;
import build.jenesis.Verification;
import build.jenesis.maven.MavenDependencyKey;
import build.jenesis.maven.MavenRepository;

public class Signatures extends ProcessBuildStep {

    private static final String STATUS = "[GNUPG:] ";
    private static final List<String> VERIFY = List.of("--status-fd", "1");

    private final transient Map<String, Repository> repositories;
    private final Verification verification;
    private final KeyExpiry expiry;
    private final String command;
    private final String issuers;
    private final transient URI trustedRoot;
    private final transient Function<List<String>, ? extends ProcessHandler> supplied;
    private final transient Consumer<String> printing;
    private final transient Palette palette;

    public Signatures(Map<String, Repository> repositories) {
        this(repositories,
                Verification.NONE,
                KeyExpiry.SIGNING,
                "gpgv",
                "github.com=token.actions.githubusercontent.com",
                null,
                null,
                null,
                Palette.NONE);
    }

    public static Signatures ofEnvironment(Environment environment,
                                    Map<String, Repository> repositories) {
        Signatures signatures = new Signatures(repositories)
                .verification(Verification.ofEnvironment(environment))
                .expiry(KeyExpiry.ofEnvironment(environment));
        String command = environment.getProperty("openpgp.command");
        if (command != null) {
            signatures = signatures.command(command);
        }
        String issuers = environment.getProperty("sigstore.issuers");
        if (issuers != null) {
            signatures = signatures.issuers(issuers);
        }
        String sigstore = environment.getProperty("sigstore.uri");
        if (sigstore != null) {
            signatures = signatures.trustedRoot(URI.create(sigstore));
        }
        Boolean print = environment.flagOrNull("print.signatures");
        return print == null || !print ? signatures : signatures.printing(environment.out(), Palette.ofEnvironment(environment));
    }

    private Signatures(Map<String, Repository> repositories,
                       Verification verification,
                       KeyExpiry expiry,
                       String command,
                       String issuers,
                       URI trustedRoot,
                       Function<List<String>, ? extends ProcessHandler> supplied,
                       Consumer<String> printing,
                       Palette palette) {
        super("gpgv", supplied == null ? ProcessHandler.OfProcess.ofCommand(command) : supplied);
        this.repositories = repositories;
        this.verification = verification;
        this.expiry = expiry;
        this.command = command;
        this.issuers = issuers;
        this.trustedRoot = trustedRoot;
        this.supplied = supplied;
        this.printing = printing;
        this.palette = palette;
    }

    public Signatures repositories(Map<String, Repository> repositories) {
        return new Signatures(repositories, verification, expiry, command, issuers, trustedRoot, supplied, printing, palette);
    }

    public Signatures verification(Verification verification) {
        return new Signatures(repositories, verification, expiry, command, issuers, trustedRoot, supplied, printing, palette);
    }

    public Signatures expiry(KeyExpiry expiry) {
        return new Signatures(repositories, verification, expiry, command, issuers, trustedRoot, supplied, printing, palette);
    }

    public Signatures command(String command) {
        return new Signatures(repositories, verification, expiry, command, issuers, trustedRoot, supplied, printing, palette);
    }

    public Signatures issuers(String issuers) {
        return new Signatures(repositories, verification, expiry, command, issuers, trustedRoot, supplied, printing, palette);
    }

    public Signatures trustedRoot(URI trustedRoot) {
        return new Signatures(repositories, verification, expiry, command, issuers, trustedRoot, supplied, printing, palette);
    }

    public Signatures factory(Function<List<String>, ? extends ProcessHandler> factory) {
        return new Signatures(repositories, verification, expiry, command, issuers, trustedRoot, factory, printing, palette);
    }

    public Signatures printing(Consumer<String> printing, Palette palette) {
        return new Signatures(repositories, verification, expiry, command, issuers, trustedRoot, supplied, printing, palette);
    }

    private void print(String marker, String colour, String coordinate, String detail) {
        if (printing == null) {
            return;
        }
        printing.accept("%s%-11s%s %s %s".formatted(
                colour,
                marker,
                palette.reset(),
                coordinate,
                detail));
    }

    @Override
    protected CompletionStage<List<String>> process(Executor executor,
                                                    BuildStepContext context,
                                                    SequencedMap<String, BuildStepArgument> arguments,
                                                    SequencedMap<String, SequencedMap<String, String>> properties) {
        return CompletableFuture.completedStage(null);
    }

    @Override
    public CompletionStage<BuildStepResult> apply(Executor executor,
                                                  BuildStepContext context,
                                                  SequencedMap<String, BuildStepArgument> arguments)
            throws IOException {
        if (verification == Verification.NONE) {
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }
        SequencedMap<String, SequencedSet<String>> declared = new TreeMap<>();
        SequencedMap<String, String> resolved = new LinkedHashMap<>();
        Set<String> internal = new HashSet<>();
        for (BuildStepArgument argument : arguments.values()) {
            if (argument.removed()) {
                continue;
            }
            Path declarations = argument.folder().resolve(SIGNATURES);
            if (Files.isRegularFile(declarations)) {
                SequencedProperties properties = SequencedProperties.ofFiles(declarations);
                for (String fingerprint : properties.stringPropertyNames()) {
                    declared.computeIfAbsent(fingerprint, _ -> new TreeSet<>())
                            .addAll(List.of(properties.getProperty(fingerprint).split(" ")));
                }
            }
            Path produced = argument.folder().resolve(Dependencies.INTERNAL);
            if (Files.isRegularFile(produced)) {
                internal.addAll(SequencedProperties.ofFiles(produced).stringPropertyNames());
            }
            Path index = argument.folder().resolve(DEPENDENCIES);
            if (Files.isRegularFile(index)) {
                SequencedProperties properties = SequencedProperties.ofFiles(index);
                for (String key : properties.stringPropertyNames()) {
                    String value = properties.getProperty(key);
                    int space = value.indexOf(' ');
                    Path jar = argument.folder().resolve(space < 0 ? value : value.substring(0, space)).normalize();
                    if (Files.isRegularFile(jar)) {
                        resolved.putIfAbsent(key, jar.toString());
                    }
                }
            }
        }
        Sigstore sigstore = new Sigstore().trustedRoot(trustedRoot);
        List<String> prefix = new ArrayList<>(prepended(properties(arguments)));
        prefix.addAll(VERIFY);
        prefix.add("--keyring");
        prefix.add(keyring(executor, declared.keySet(), context).toAbsolutePath().toString());
        SequencedMap<String, SequencedMap<String, String>> candidates = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : resolved.entrySet()) {
            String key = entry.getKey();
            int first = key.indexOf('/'), second = key.indexOf('/', first + 1);
            if (second < 0) {
                continue;
            }
            String rest = key.substring(second + 1);
            if (internal.contains(rest)) {
                continue;
            }
            int last = rest.lastIndexOf('/');
            if (last <= 0 || last == rest.indexOf('/')) {
                continue;
            }
            candidates.computeIfAbsent(entry.getValue(), _ -> new LinkedHashMap<>())
                    .putIfAbsent(key.substring(0, first) + "/" + rest.substring(0, last),
                            rest.substring(last + 1));
        }
        List<String> violations = new ArrayList<>();
        for (Map.Entry<String, SequencedMap<String, String>> candidate : candidates.entrySet()) {
            SequencedSet<String> accepted = new LinkedHashSet<>();
            String token = candidate.getValue().firstEntry().getKey();
            String version = candidate.getValue().firstEntry().getValue();
            for (Map.Entry<String, String> spelling : candidate.getValue().entrySet()) {
                SequencedSet<String> covering = new LinkedHashSet<>();
                for (Map.Entry<String, SequencedSet<String>> declaration : declared.entrySet()) {
                    if (declaration.getValue().stream().anyMatch(value -> value.equals(spelling.getKey())
                            || value.endsWith("/*")
                            && spelling.getKey().startsWith(value.substring(0, value.length() - 1)))) {
                        covering.add(declaration.getKey());
                    }
                }
                if (!covering.isEmpty() && accepted.isEmpty()) {
                    token = spelling.getKey();
                    version = spelling.getValue();
                }
                accepted.addAll(covering);
            }
            String key = null;
            for (Map.Entry<String, String> resolvedEntry : resolved.entrySet()) {
                if (resolvedEntry.getValue().equals(candidate.getKey())) {
                    key = resolvedEntry.getKey();
                    break;
                }
            }
            int first = key.indexOf('/'), second = key.indexOf('/', first + 1);
            String rest = token.substring(token.indexOf('/') + 1) + "/" + version;
            String coordinate = rest.substring(0, rest.lastIndexOf('/'));
            if (accepted.isEmpty()) {
                if (printing != null) {
                    print("[UNDECLARED]",
                            palette.warning(),
                            token + " " + version,
                            "no @jenesis.signature line covers it");
                }
                if (verification == Verification.STRICT) {
                    violations.add(token + " " + version + ": no @jenesis.signature line declares a key for it");
                }
                continue;
            }
            if (accepted.stream().anyMatch("unsigned/ignored"::equalsIgnoreCase)) {
                if (printing != null) {
                    print("[UNVERIFIED]",
                            palette.warning(),
                            token + " " + version,
                            "unsigned/ignored accepts it signed or not");
                }
                continue;
            }
            int repositorySlash = coordinate.indexOf('/');
            Repository repository = repositorySlash < 1
                    ? null
                    : repositories.get(Resolver.base(coordinate.substring(0, repositorySlash)));
            if (repository == null) {
                violations.add(token + " " + version + ": no repository resolves it, so its signature cannot be read");
                continue;
            }
            String relative = coordinate.substring(repositorySlash + 1) + "/" + version;
            SequencedSet<String> bundled = new LinkedHashSet<>(accepted);
            bundled.removeIf(declaration -> !bundled(declaration));
            SequencedSet<String> keys = new LinkedHashSet<>(accepted);
            keys.removeIf(declaration -> unsigned(declaration) || bundled(declaration));
            boolean attested = false;
            if (!bundled.isEmpty()) {
                Path bundle = materialise(executor, context, repository, relative, "sigstore.json");
                if (bundle != null) {
                    Sigstore.Attestation attestation;
                    try {
                        attestation = sigstore.verify(Path.of(candidate.getKey()), bundle);
                    } catch (GeneralSecurityException | RuntimeException e) {
                        violations.add(token + " " + version + ": " + reason(e));
                        continue;
                    }
                    String matched = null, misissued = null;
                    for (String declaration : bundled) {
                        if (!names(declaration, attestation.identity())) {
                            continue;
                        }
                        if (issuer(host(declaration)).equals(attestation.issuer())) {
                            matched = declaration;
                            break;
                        }
                        misissued = declaration;
                    }
                    if (matched == null) {
                        violations.add(token + " " + version + ": " + (misissued == null
                                ? "signed by " + attestation.identity()
                                        + " through " + attestation.issuer()
                                        + ", but only " + String.join(", ", bundled)
                                        + (bundled.size() == 1 ? " is" : " are") + " declared for it; add "
                                        + suggested(attestation)
                                        + " to a @jenesis.signature line to accept this signer"
                                : "signed by " + attestation.identity() + ", which " + misissued
                                        + " covers, but issued by " + attestation.issuer()
                                        + " rather than " + issuer(host(misissued))
                                        + "; name what issues " + host(misissued) + " identities with"
                                        + " -Djenesis.sigstore.issuers=" + host(misissued)
                                        + "=" + bare(attestation.issuer())));
                        continue;
                    }
                    if (printing != null) {
                        print("[VERIFIED]",
                                palette.status(),
                                token + " " + version,
                                matched + " as " + attestation.identity() + ", recorded " + attestation.recorded());
                    }
                    String violation = attests(executor,
                            context,
                            repository,
                            sigstore,
                            descriptor(repository, relative, version),
                            attestation);
                    if (violation != null) {
                        violations.add(token + " " + version + ": " + violation);
                        continue;
                    }
                    attested = true;
                }
            }
            boolean identity = !bundled.isEmpty() && keys.isEmpty();
            Path signature = identity ? null : materialise(executor, context, repository, relative, "asc");
            if (signature == null) {
                if (attested) {
                    continue;
                }
                SequencedSet<String> acknowledged = new LinkedHashSet<>(accepted);
                acknowledged.removeIf(declaration -> !unsigned(declaration));
                if (printing != null) {
                    print(acknowledged.isEmpty() ? "[UNSIGNED]" : "[UNVERIFIED]",
                            palette.warning(),
                            token + " " + version,
                            acknowledged.isEmpty()
                                    ? (identity
                                            ? "an identity is declared for it but no Sigstore bundle is published"
                                            : "a key is declared for it but no signature is published")
                                    : "no signature is published, which "
                                            + String.join(", ", acknowledged) + " accepts");
                }
                if (acknowledged.isEmpty()) {
                    violations.add(token + " " + version
                            + (identity
                                    ? ": an identity is declared for it but no Sigstore bundle is published"
                                    : ": a key is declared for it but no signature is published")
                            + "; if that is reviewed"
                            + " and accepted, declare it as unsigned/missing, which fails again once a"
                            + " signature appears, or unsigned/ignored, which never looks");
                }
                continue;
            }
            Status status = verify(executor, context, prefix, Path.of(candidate.getKey()), signature);
            if (status.failure() != null) {
                violations.add(token + " " + version + ": " + status.failure()
                        + (status.missing() == null ? "" : missing(accepted)));
                continue;
            }
            if (status.fingerprint() == null) {
                violations.add(token + " " + version + ": gpg reported no validated signature");
                continue;
            }
            String fingerprint = "OpenPGP/" + status.fingerprint().toUpperCase(Locale.ROOT);
            if (printing != null && accepted.stream().anyMatch(fingerprint::equalsIgnoreCase)) {
                print(status.expired() < 0 ? "[VERIFIED]" : "[EXPIRED]",
                        status.expired() < 0 ? palette.status() : palette.warning(),
                        token + " " + version,
                        fingerprint + status.dates());
            }
            if (accepted.stream().noneMatch(fingerprint::equalsIgnoreCase)) {
                violations.add(token + " " + version + ": signed by " + fingerprint
                        + " but only " + String.join(", ", accepted)
                        + (accepted.size() == 1 ? " is" : " are") + " declared for it; "
                        + (accepted.stream().anyMatch("unsigned/missing"::equalsIgnoreCase)
                                ? "a signature has appeared where unsigned/missing said there was none,"
                                        + " so replace that line with " + fingerprint
                                : "add " + fingerprint
                                        + " to a @jenesis.signature line to accept a key rotation"));
                continue;
            }
            String descriptor = descriptor(repository, relative, version);
            if (descriptor == null) {
                continue;
            }
            Path pomSignature = materialise(executor, context, repository, descriptor, "asc");
            Path pom = pomSignature == null ? null : materialise(executor, context, repository, descriptor, null);
            if (pomSignature == null) {
                if (verification == Verification.STRICT) {
                    violations.add(token + " " + version
                            + ": its artifact is signed but its POM publishes no signature");
                }
                continue;
            }
            if (pom == null) {
                violations.add(token + " " + version
                        + ": a signature is published for its POM but the POM itself did not resolve");
                continue;
            }
            Status pomStatus = verify(executor, context, prefix, pom, pomSignature);
            String signer = pomStatus.fingerprint() == null
                    ? null
                    : "OpenPGP/" + pomStatus.fingerprint().toUpperCase(Locale.ROOT);
            if (pomStatus.failure() != null) {
                violations.add(token + " " + version + ": for its POM, " + pomStatus.failure()
                        + (pomStatus.missing() == null
                                ? "; a repository that re-serialises POMs invalidates them, so resolve from one"
                                        + " that serves the published bytes"
                                : missing(accepted)));
            } else if (signer == null) {
                violations.add(token + " " + version + ": gpg reported no validated signature for its POM");
            } else if (!signer.equalsIgnoreCase(fingerprint)) {
                violations.add(token + " " + version + ": its POM is signed by " + signer
                        + " but its artifact by " + fingerprint);
            }
        }
        if (!violations.isEmpty()) {
            throw new IllegalStateException("Unverified dependency signatures:\n  "
                    + String.join("\n  ", violations));
        }
        return CompletableFuture.completedStage(new BuildStepResult(true));
    }

    private Path materialise(Executor executor,
                             BuildStepContext context,
                             Repository repository,
                             String coordinate,
                             String extension) throws IOException {
        RepositoryItem item = repository.fetch(executor, coordinate, extension).orElse(null);
        if (item == null) {
            return null;
        }
        Path file = item.file().orElse(null);
        if (file != null) {
            return file;
        }
        Path target = Files.createTempFile(context.supplement(), "fetched", extension == null ? ".tmp" : "." + extension);
        try (InputStream inputStream = item.toInputStream()) {
            Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return target;
    }

    private Status verify(Executor executor, BuildStepContext context, List<String> prefix, Path file, Path signature)
            throws IOException {
        Path output = Files.createTempFile(context.supplement(), "status", ".txt");
        Path error = Files.createTempFile(context.supplement(), "error", ".txt");
        List<String> commands = new ArrayList<>(prefix);
        commands.add(signature.toAbsolutePath().toString());
        commands.add(file.toAbsolutePath().toString());
        ProcessHandler handler;
        try {
            handler = factory.apply(commands);
        } catch (IllegalStateException e) {
            throw new IllegalStateException("Could not locate '" + command
                    + "' to verify dependency signatures: install GnuPG, or name another command with"
                    + " -Djenesis.openpgp.command", e);
        }
        int exitCode;
        try {
            exitCode = execute(handler, output, error, tee(executor, handler));
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            throw new InterruptedIOException("Interrupted while verifying " + file);
        }
        String fingerprint = null, failure = null, missing = null;
        long signed = -1, expired = -1;
        boolean keyExpired = false;
        for (String line : Files.readAllLines(output, StandardCharsets.ISO_8859_1)) {
            if (!line.startsWith(STATUS)) {
                continue;
            }
            String[] tokens = line.substring(STATUS.length()).trim().split(" ");
            switch (tokens[0]) {
                case "VALIDSIG" -> {
                    if (tokens.length > 10) {
                        fingerprint = tokens[10];
                    } else if (tokens.length > 1) {
                        fingerprint = tokens[1];
                    }
                    if (tokens.length > 3) {
                        signed = seconds(tokens[3]);
                    }
                }
                case "KEYEXPIRED" -> {
                    if (tokens.length > 1) {
                        expired = Math.max(expired, seconds(tokens[1]));
                    }
                }
                case "BADSIG" -> failure = "the signature does not match the file";
                case "EXPKEYSIG" -> keyExpired = true;
                case "REVKEYSIG" -> failure = "the signing key was revoked";
                case "NO_PUBKEY" -> missing = tokens.length > 1 ? tokens[1] : "";
                case "ERRSIG" -> {
                    if (failure == null) {
                        failure = "the signature could not be checked";
                    }
                }
                default -> {
                }
            }
        }
        if (keyExpired && failure == null) {
            failure = expired(signed, expired);
        }
        if (missing != null) {
            failure = "the public key " + missing + " is not available";
        }
        if (exitCode != 0 && fingerprint == null && failure == null) {
            throw new IllegalStateException("Unexpected exit code " + exitCode + " and no signature verdict from "
                    + command
                    + " for " + file
                    + "\nTo reproduce, execute:\n "
                    + shell(handler.commands())
                    + tail("Error", error));
        }
        return new Status(fingerprint, failure, signed, keyExpired ? expired : -1, missing);
    }

    private Path keyring(Executor executor, Set<String> declared, BuildStepContext context) throws IOException {
        Path file = context.supplement().resolve("keyring.gpg");
        try (OutputStream out = Files.newOutputStream(file)) {
            for (String declaration : declared) {
                if (unsigned(declaration) || bundled(declaration)) {
                    continue;
                }
                int slash = declaration.indexOf('/');
                String algorithm = declaration.substring(0, slash);
                Repository keys = repositories.get(algorithm);
                if (keys == null) {
                    throw new IllegalStateException("No repository is registered as '"
                            + algorithm
                            + "' to resolve the key that "
                            + declaration
                            + " declares; register one under that name, or declare the coordinate"
                            + " unsigned/missing or unsigned/ignored");
                }
                String fingerprint = declaration.substring(slash + 1).toUpperCase(Locale.ROOT);
                byte[] key;
                try (InputStream stream = keys.fetch(executor, fingerprint)
                        .orElseThrow(() -> new IOException("No key server holds it"))
                        .toInputStream()) {
                    key = stream.readAllBytes();
                } catch (IOException e) {
                    if (printing != null) {
                        print("[UNFETCHED]",
                                palette.warning(),
                                declaration,
                                e.getMessage() == null ? e.toString() : e.getMessage());
                    }
                    continue;
                }
                String text = new String(key, StandardCharsets.US_ASCII);
                out.write(text.regionMatches(0, "-----BEGIN PGP", 0, 14) ? dearmoured(text) : key);
            }
        }
        return file;
    }

    private static byte[] dearmoured(String armoured) {
        int begin = armoured.indexOf("-----BEGIN PGP PUBLIC KEY BLOCK-----");
        int end = armoured.indexOf("-----END PGP PUBLIC KEY BLOCK-----");
        if (begin < 0 || end < begin) {
            throw new IllegalArgumentException("No OpenPGP public key block in the response");
        }
        StringBuilder base64 = new StringBuilder();
        boolean body = false;
        for (String line : armoured.substring(begin, end).lines().skip(1).toList()) {
            String trimmed = line.trim();
            if (!body) {
                if (trimmed.isEmpty()) {
                    body = true;
                } else if (trimmed.indexOf(':') < 0) {
                    body = true;
                    base64.append(trimmed);
                }
                continue;
            }
            if (!trimmed.isEmpty() && !trimmed.startsWith("=")) {
                base64.append(trimmed);
            }
        }
        return Base64.getMimeDecoder().decode(base64.toString());
    }

    private String attests(Executor executor,
                           BuildStepContext context,
                           Repository repository,
                           Sigstore sigstore,
                           String descriptor,
                           Sigstore.Attestation attestation) throws IOException {
        if (descriptor == null) {
            return null;
        }
        Path bundle = materialise(executor, context, repository, descriptor, "sigstore.json");
        if (bundle == null) {
            return verification == Verification.STRICT
                    ? "its artifact carries a Sigstore bundle but its POM publishes none"
                    : null;
        }
        Path pom = materialise(executor, context, repository, descriptor, null);
        if (pom == null) {
            return "a Sigstore bundle is published for its POM but the POM itself did not resolve";
        }
        Sigstore.Attestation signer;
        try {
            signer = sigstore.verify(pom, bundle);
        } catch (GeneralSecurityException | RuntimeException e) {
            return "for its POM, " + reason(e);
        }
        return signer.identity().equals(attestation.identity())
                ? null
                : "its POM is signed by " + signer.identity() + " but its artifact by " + attestation.identity();
    }

    private static boolean names(String declaration, String identity) {
        String prefix = "https://" + declaration.substring(declaration.indexOf('/') + 1);
        return identity.equals(prefix) || identity.startsWith(prefix + "/") || identity.startsWith(prefix + "@");
    }

    private static String bare(String issuer) {
        int scheme = issuer.indexOf("://");
        return scheme < 0 ? issuer : issuer.substring(scheme + 3);
    }

    private static String host(String declaration) {
        String value = declaration.substring(declaration.indexOf('/') + 1);
        int slash = value.indexOf('/');
        if (slash < 1) {
            throw new IllegalArgumentException(declaration + " names no path below an identity host, expected"
                    + " a form such as Sigstore/github.com/<owner>/<repository>");
        }
        return value.substring(0, slash);
    }

    private String issuer(String host) {
        for (String entry : issuers.split(",")) {
            int equals = entry.indexOf('=');
            if (equals > 0 && entry.substring(0, equals).trim().equals(host)) {
                String issuer = entry.substring(equals + 1).trim();
                if (issuer.contains("://")) {
                    throw new IllegalArgumentException("An issuer of jenesis.sigstore.issuers is named as it is"
                            + " in an identity, without a scheme: " + host + "="
                            + issuer.substring(issuer.indexOf("://") + 3));
                }
                return "https://" + issuer;
            }
        }
        return "https://" + host;
    }

    private static String suggested(Sigstore.Attestation attestation) {
        String path = attestation.identity().startsWith("https://")
                ? attestation.identity().substring(8)
                : attestation.identity();
        int end = -1;
        for (int index = 0; index < 3; index++) {
            end = path.indexOf('/', end + 1);
            if (end < 0) {
                return "Sigstore/" + path;
            }
        }
        return "Sigstore/" + path.substring(0, end);
    }

    private static String reason(Throwable throwable) {
        return throwable.getMessage() == null ? throwable.toString() : throwable.getMessage();
    }

    private static String descriptor(Repository repository, String relative, String version) {
        if (!(repository instanceof MavenRepository)) {
            return null;
        }
        try {
            MavenDependencyKey.Versioned parsed = MavenDependencyKey.parse(relative);
            return new MavenDependencyKey(parsed.key().groupId(), parsed.key().artifactId(), "pom", null)
                    .coordinate(null, version);
        } catch (IllegalArgumentException _) {
            return null;
        }
    }

    private static boolean bundled(String declaration) {
        return declaration.startsWith("Sigstore/");
    }

    private static boolean unsigned(String declaration) {
        return declaration.equalsIgnoreCase("unsigned/missing")
                || declaration.equalsIgnoreCase("unsigned/ignored");
    }

    private static String missing(SequencedSet<String> accepted) {
        return ", and is declared as "
                + String.join(", ", accepted)
                + "; obtain that key, verify the fingerprint against the project's published keys, and import it";
    }

    private String expired(long signed, long expired) {
        return switch (expiry) {
            case IGNORED -> null;
            case CURRENT -> "the signing key has expired; -Djenesis.openpgp.expiry=signing accepts a"
                    + " signature that the key made before it expired";
            case SIGNING -> {
                if (signed < 0 || expired < 0) {
                    yield "the signing key has expired and " + command
                            + " did not report when, so it cannot be told whether the signature predates it;"
                            + " -Djenesis.openpgp.expiry=ignored accepts it regardless";
                }
                yield signed < expired ? null : "the signature was made at "
                        + Instant.ofEpochSecond(signed)
                        + ", after the signing key expired at "
                        + Instant.ofEpochSecond(expired)
                        + "; -Djenesis.openpgp.expiry=ignored accepts it regardless";
            }
        };
    }

    private static long seconds(String token) {
        try {
            return Long.parseLong(token);
        } catch (NumberFormatException _) {
            return -1;
        }
    }

    private record Status(String fingerprint, String failure, long signed, long expired, String missing) {

        private String dates() {
            StringBuilder dates = new StringBuilder();
            if (signed >= 0) {
                dates.append(" signed ").append(date(signed));
            }
            if (expired >= 0) {
                dates.append(dates.isEmpty() ? " " : ", ").append("key expired ").append(date(expired));
            }
            return dates.toString();
        }

        private static LocalDate date(long seconds) {
            return Instant.ofEpochSecond(seconds).atZone(ZoneOffset.UTC).toLocalDate();
        }
    }
}
