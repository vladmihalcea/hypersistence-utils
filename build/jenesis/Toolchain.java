package build.jenesis;

import module java.base;

public final class Toolchain {

    private static final Pattern VERSION = Pattern.compile("[1-9][0-9]*(\\.(0|[1-9][0-9]*))*(-[A-Za-z]+)*");
    private static final Pattern WORD = Pattern.compile("[A-Za-z]+");
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9._+-]+");

    private final String version;
    private final String searchpath;
    private final String installer;
    private final List<Integer> numbers;
    private final SequencedSet<String> words;
    private final List<Entry> entries;
    private final List<String> invocation;

    public Toolchain() {
        this(null, "@", null);
    }

    public static Toolchain ofKeys(Map<String, String> keys) {
        String searchpath = keys.get("toolchain.searchpath"), installer = keys.get("toolchain.installer");
        return new Toolchain(keys.get("toolchain.version"),
                searchpath == null ? "@" : searchpath,
                installer == null ? System.getenv("JENESIS_TOOLCHAIN_INSTALLER") : installer);
    }

    private Toolchain(String version, String searchpath, String installer) {
        this.version = version == null || version.isBlank() ? null : version.trim();
        this.searchpath = searchpath;
        this.installer = installer == null || installer.isBlank() ? null : installer.trim();
        List<Integer> numbers = new ArrayList<>();
        SequencedSet<String> words = new LinkedHashSet<>();
        if (this.version != null) {
            if (this.version.length() > 64 || !VERSION.matcher(this.version).matches()) {
                throw new IllegalArgumentException("Malformed jenesis.toolchain.version: '" + printable(this.version)
                        + "' (expected <feature>[.<interim>[.<update>...]][-<word>...], as 25, 25.0.3,"
                        + " 25-temurin or 26-ea)");
            }
            String[] parts = this.version.split("-");
            try {
                for (String number : parts[0].split("\\.")) {
                    numbers.add(Integer.parseInt(number));
                }
            } catch (NumberFormatException _) {
                throw new IllegalArgumentException("Malformed jenesis.toolchain.version: '" + this.version
                        + "' names a number too large for any JDK");
            }
            for (int index = 1; index < parts.length; index++) {
                words.add(parts[index].toLowerCase(Locale.ROOT));
            }
        }
        this.numbers = List.copyOf(numbers);
        this.words = Collections.unmodifiableSequencedSet(words);
        List<Entry> entries = new ArrayList<>();
        for (String part : searchpath.split(",")) {
            String entry = part.trim();
            if (entry.equals("@")) {
                for (String fallback : defaults()) {
                    entries.add(entry(fallback));
                }
            } else if (!entry.isEmpty()) {
                entries.add(entry(entry));
            }
        }
        this.entries = List.copyOf(entries);
        if (this.installer == null) {
            invocation = null;
        } else {
            Path whole;
            try {
                whole = Path.of(this.installer);
            } catch (InvalidPathException _) {
                whole = null;
            }
            List<String> invocation = new ArrayList<>(whole != null && whole.isAbsolute() && Files.isRegularFile(whole)
                    ? List.of(this.installer)
                    : List.of(this.installer.split("\\s+")));
            String program = invocation.getFirst();
            if (program.equals("~")
                    || program.startsWith("~/")
                    || File.separatorChar == '\\' && program.startsWith("~\\")) {
                program = System.getProperty("user.home") + program.substring(1);
            }
            if (!NAME.matcher(program).matches()) {
                Path path;
                try {
                    path = Path.of(program);
                } catch (InvalidPathException _) {
                    path = null;
                }
                if (path == null || !path.isAbsolute()) {
                    throw new IllegalArgumentException("Malformed jenesis.toolchain.installer: '"
                            + printable(this.installer) + "' (its program is a name looked up on the PATH, of"
                            + " letters, digits, dots, dashes, underscores and pluses, or an absolute path or one"
                            + " starting with ~)");
                }
            }
            invocation.set(0, program);
            this.invocation = List.copyOf(invocation);
        }
    }

    public Toolchain version(String version) {
        return new Toolchain(version, searchpath, installer);
    }

    public Toolchain searchpath(String searchpath) {
        return new Toolchain(version, searchpath, installer);
    }

    public Toolchain installer(String installer) {
        return new Toolchain(version, searchpath, installer);
    }

    public Path home() throws IOException, InterruptedException {
        return select().home();
    }

    public List<String> command(Class<?> main, List<String> options, List<String> arguments)
            throws IOException, InterruptedException {
        requireRunnable();
        return command(select().home(), main, options, arguments);
    }

    public int launch(Class<?> main, List<String> options, List<String> arguments)
            throws IOException, InterruptedException {
        requireRunnable();
        Candidate candidate = select();
        List<String> command = command(candidate.home(), main, options, arguments);
        System.err.println("Running on " + candidate.version() + " (" + String.join(" ", candidate.words())
                + ") at " + printable(candidate.home()) + ", which jenesis.toolchain.version=" + version
                + " selects");
        Process process = new ProcessBuilder(command).inheritIO().start();
        Thread hook = new Thread(process::destroy);
        Runtime.getRuntime().addShutdownHook(hook);
        try {
            return process.waitFor();
        } finally {
            try {
                Runtime.getRuntime().removeShutdownHook(hook);
            } catch (IllegalStateException _) {
                process.destroy();
            }
        }
    }

    private void requireRunnable() {
        if (!numbers.isEmpty() && numbers.getFirst() < 25) {
            throw new IllegalArgumentException("jenesis.toolchain.version=" + version + " names a JDK"
                    + " Jenesis cannot run on, as it needs 25 or newer - to compile for an older Java, keep"
                    + " the toolchain on 25 or newer and declare the release, as @jenesis.release or"
                    + " maven.compiler.release");
        }
    }

    private List<String> command(Path home, Class<?> main, List<String> options, List<String> arguments) {
        List<String> command = new ArrayList<>();
        command.add(home.resolve("bin").resolve(File.separatorChar == '\\' ? "java.exe" : "java").toString());
        command.addAll(options);
        if (version != null) {
            command.add("-Djenesis.toolchain.version=" + version);
        }
        command.add("-Djenesis.toolchain.searchpath=");
        CodeSource source = main.getProtectionDomain().getCodeSource();
        Path location;
        try {
            location = source == null ? null : Path.of(source.getLocation().toURI());
        } catch (URISyntaxException | IllegalArgumentException | FileSystemNotFoundException _) {
            location = null;
        }
        if (location != null && location.getFileName() != null
                && location.getFileName().toString().endsWith(".java")) {
            command.add(location.toString());
        } else if (main.getModule().isNamed()) {
            String modules = System.getProperty("jdk.module.path");
            if (modules == null) {
                throw new IllegalStateException("Cannot relaunch " + main.getName() + " on another JDK: its module "
                        + main.getModule().getName() + " is not on the module path this JVM was started with");
            }
            command.addAll(List.of("-p", modules, "-m", main.getModule().getName() + "/" + main.getName()));
        } else {
            command.addAll(List.of("-cp", System.getProperty("java.class.path"), main.getName()));
        }
        command.addAll(arguments);
        return command;
    }

    private Candidate select() throws IOException, InterruptedException {
        Path running = Path.of(System.getProperty("java.home"));
        if (version == null) {
            return new Candidate(running, Runtime.version(), System.getProperty("java.vendor", ""), words(
                    System.getProperty("java.vendor", ""),
                    System.getProperty("java.vendor.version", ""),
                    Runtime.version().pre().orElse(""),
                    Runtime.version().optional().orElse("")));
        }
        SequencedMap<Path, String> skipped = new LinkedHashMap<>();
        Candidate current = describe(running, skipped);
        if (current == null) {
            throw new IllegalStateException("The running JVM at " + running + " has no release file that names its"
                    + " version (" + skipped.getOrDefault(running, "it has no release file") + "), so it cannot be"
                    + " checked against jenesis.toolchain.version=" + version + " - run the build on a JDK");
        }
        if (!current.version().toString().equals(Runtime.version().toString())
                || !current.implementor().equals(System.getProperty("java.vendor", ""))) {
            throw new IllegalStateException("The release file of the running JVM at " + running + " describes "
                    + current.version() + " (" + String.join(" ", current.words()) + "), but the JVM itself is "
                    + Runtime.version() + " (" + String.join(" ", words(System.getProperty("java.vendor", ""),
                    System.getProperty("java.vendor.version", ""))) + "), so jenesis.toolchain.version=" + version
                    + " cannot be checked against it - reinstall that JDK or run the build on another one");
        }
        if (matches(current)) {
            return current;
        }
        if (entries.isEmpty()) {
            throw new IllegalStateException("The build requires a JDK matching jenesis.toolchain.version=" + version
                    + " but runs on " + current.version() + " (" + String.join(" ", current.words()) + ") at "
                    + running + ", and jenesis.toolchain.searchpath is empty - run it on a matching JDK, or name"
                    + " the folders to search for one in jenesis.toolchain.searchpath");
        }
        List<Candidate> found = search(running, skipped);
        Candidate selected = best(found);
        if (selected == null && invocation != null) {
            install();
            skipped.clear();
            found = search(running, skipped);
            selected = best(found);
            if (selected == null) {
                throw new IllegalStateException(listing(new StringBuilder("The installer ")
                        .append(printable(installer)).append(" ran for jenesis.toolchain.version=").append(version)
                        .append(", and still no JDK matches it: jenesis.toolchain.searchpath=")
                        .append(printable(searchpath)), found, skipped)
                        .append("\nMake the installer put the JDK into one of the searched folders, or add the"
                                + " folder it installs into to jenesis.toolchain.searchpath").toString());
            }
        }
        if (selected == null) {
            throw new IllegalStateException(listing(new StringBuilder("No JDK matches jenesis.toolchain.version=")
                    .append(version).append(". The running JVM is ").append(current.version())
                    .append(" (").append(String.join(" ", current.words())).append(") at ").append(running)
                    .append(", and jenesis.toolchain.searchpath=").append(printable(searchpath)), found, skipped)
                    .append("\nInstall a matching JDK into one of the searched folders, or add the folder that"
                            + " holds one to jenesis.toolchain.searchpath, on the command line or in"
                            + " ~/.jenesis/jenesis.properties; jenesis.toolchain.installer names a program that"
                            + " installs one when none matches").toString());
        }
        String unsafe = unsafe(selected.home());
        if (unsafe != null) {
            throw new IllegalStateException("The JDK at " + printable(selected.home()) + " matches"
                    + " jenesis.toolchain.version=" + version + " but is not safe to run: " + unsafe
                    + " - make the JDK writable by its owner alone, as with chmod -R go-w "
                    + printable(selected.home()) + ", or remove it from jenesis.toolchain.searchpath");
        }
        return selected;
    }

    private List<Candidate> search(Path running, SequencedMap<Path, String> skipped) throws IOException {
        Set<Path> seen = new HashSet<>();
        seen.add(running.toRealPath());
        List<Candidate> found = new ArrayList<>();
        for (Path home : expand(skipped)) {
            Path real;
            try {
                real = home.toRealPath();
            } catch (IOException _) {
                continue;
            }
            if (seen.add(real)) {
                Candidate candidate = describe(home, skipped);
                if (candidate != null) {
                    found.add(candidate);
                }
            }
        }
        return found;
    }

    private Candidate best(List<Candidate> found) {
        Comparator<Candidate> order = Comparator.comparing(Candidate::version, Runtime.Version::compareToIgnoreOptional);
        return found.stream().filter(this::matches).sorted(order.reversed()).findFirst().orElse(null);
    }

    private static StringBuilder listing(StringBuilder message,
                                         List<Candidate> found,
                                         SequencedMap<Path, String> skipped) {
        message.append(" found");
        if (found.isEmpty() && skipped.isEmpty()) {
            return message.append(" no JDK.");
        }
        message.append(':');
        for (Candidate candidate : found) {
            message.append("\n  ").append(printable(candidate.home())).append("  ")
                    .append(candidate.version()).append(" (").append(String.join(" ", candidate.words()))
                    .append(')');
        }
        skipped.forEach((home, reason) -> message.append("\n  ").append(printable(home))
                .append("  skipped, as ").append(reason));
        return message;
    }

    private void install() throws IOException, InterruptedException {
        List<String> command = new ArrayList<>(invocation);
        command.set(0, program(command.getFirst()));
        command.add(version);
        System.err.println("Installing a JDK for jenesis.toolchain.version=" + version + " with "
                + printable(String.join(" ", command)));
        Process process = new ProcessBuilder(command)
                .directory(new File(System.getProperty("user.home")))
                .redirectInput(ProcessBuilder.Redirect.INHERIT)
                .redirectErrorStream(true)
                .start();
        try (InputStream in = process.getInputStream()) {
            in.transferTo(System.err);
        }
        int code;
        try {
            code = process.waitFor();
        } catch (InterruptedException e) {
            process.destroy();
            throw e;
        }
        if (code != 0) {
            throw new IllegalStateException("The installer " + printable(installer) + " failed with exit code "
                    + code + " for jenesis.toolchain.version=" + version + " - see its output above");
        }
    }

    private static String program(String program) {
        if (!NAME.matcher(program).matches()) {
            return program;
        }
        List<String> extensions = new ArrayList<>(List.of(""));
        if (File.separatorChar == '\\') {
            String pathext = System.getenv("PATHEXT");
            extensions.addAll(List.of((pathext == null ? ".COM;.EXE;.BAT;.CMD" : pathext).split(";")));
        }
        String path = System.getenv("PATH");
        for (String entry : path == null ? new String[0] : path.split(File.pathSeparator)) {
            Path folder;
            try {
                folder = Path.of(entry);
            } catch (InvalidPathException _) {
                continue;
            }
            if (!folder.isAbsolute()) {
                continue;
            }
            for (String extension : extensions) {
                Path candidate = folder.resolve(program + extension);
                if (Files.isRegularFile(candidate) && Files.isExecutable(candidate)) {
                    return candidate.toString();
                }
            }
        }
        throw new IllegalStateException("The installer " + printable(program) + " that jenesis.toolchain.installer"
                + " names is on no absolute folder of the PATH - install it, or name it by its absolute path");
    }

    private boolean matches(Candidate candidate) {
        List<Integer> actual = candidate.version().version();
        for (int index = 0; index < numbers.size(); index++) {
            int expected = numbers.get(index), value = index < actual.size() ? actual.get(index) : 0;
            if (expected != value) {
                return false;
            }
        }
        return candidate.words().containsAll(words)
                && words.containsAll(words(candidate.version().pre().orElse("")));
    }

    private static Candidate describe(Path home, SequencedMap<Path, String> skipped) {
        Path release = home.resolve("release");
        if (!Files.isRegularFile(release)) {
            return null;
        }
        Properties properties = new Properties();
        try {
            if (Files.size(release) > 64 * 1024) {
                skipped.put(home, "its release file is larger than 64 KiB");
                return null;
            }
            try (InputStream in = Files.newInputStream(release)) {
                properties.load(in);
            }
        } catch (IOException | IllegalArgumentException _) {
            skipped.put(home, "its release file cannot be read");
            return null;
        }
        String runtime = unquote(properties.getProperty("JAVA_RUNTIME_VERSION"));
        if (runtime.isEmpty()) {
            skipped.put(home, "its release file names no JAVA_RUNTIME_VERSION");
            return null;
        }
        Runtime.Version parsed;
        try {
            parsed = Runtime.Version.parse(runtime);
        } catch (IllegalArgumentException _) {
            skipped.put(home, "its release file names a malformed JAVA_RUNTIME_VERSION");
            return null;
        }
        if (!Files.isRegularFile(home.resolve("bin").resolve(File.separatorChar == '\\' ? "java.exe" : "java"))) {
            skipped.put(home, "it has no java executable in bin");
            return null;
        }
        String implementor = unquote(properties.getProperty("IMPLEMENTOR"));
        return new Candidate(home, parsed, implementor, words(implementor,
                unquote(properties.getProperty("IMPLEMENTOR_VERSION")),
                parsed.pre().orElse(""),
                parsed.optional().orElse("")));
    }

    private static String unquote(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.length() > 1 && trimmed.startsWith("\"") && trimmed.endsWith("\"")
                ? trimmed.substring(1, trimmed.length() - 1)
                : trimmed;
    }

    private static SequencedSet<String> words(String... texts) {
        SequencedSet<String> words = new LinkedHashSet<>();
        for (String text : texts) {
            Matcher matcher = WORD.matcher(text);
            while (matcher.find()) {
                words.add(matcher.group().toLowerCase(Locale.ROOT));
            }
        }
        return words;
    }

    private List<Path> expand(SequencedMap<Path, String> skipped) {
        List<Path> homes = new ArrayList<>();
        for (Entry entry : entries) {
            List<Path> current = List.of(entry.base());
            for (String segment : entry.segments()) {
                List<Path> next = new ArrayList<>();
                for (Path path : current) {
                    if (!segment.equals("*")) {
                        next.add(path.resolve(segment));
                    } else if (Files.isDirectory(path)) {
                        try (Stream<Path> children = Files.list(path)) {
                            children.filter(Files::isDirectory).sorted().forEach(next::add);
                        } catch (IOException _) {
                            skipped.put(path, "it cannot be listed");
                        }
                    }
                }
                current = next;
            }
            for (Path path : current) {
                if (Files.isDirectory(path)) {
                    homes.add(path);
                }
            }
        }
        return homes;
    }

    private static Entry entry(String text) {
        String home = System.getProperty("user.home");
        String expanded = text.equals("~") ? home
                : text.startsWith("~/") || File.separatorChar == '\\' && text.startsWith("~\\")
                ? home + text.substring(1)
                : text;
        List<String> segments = List.of(expanded.split(File.separatorChar == '\\' ? "[/\\\\]" : "/", -1));
        int wildcard = segments.indexOf("*");
        if (wildcard < 0) {
            wildcard = segments.size();
        }
        Path base;
        try {
            base = Path.of(String.join(File.separator, segments.subList(0, wildcard)));
        } catch (InvalidPathException _) {
            throw malformed(text);
        }
        if (!base.isAbsolute() || segments.stream().anyMatch(segment -> segment.contains("*")
                && !segment.equals("*"))) {
            throw malformed(text);
        }
        return new Entry(base, segments.subList(wildcard, segments.size()).stream()
                .filter(segment -> !segment.isEmpty())
                .toList());
    }

    private static IllegalArgumentException malformed(String text) {
        return new IllegalArgumentException("Malformed entry '" + printable(text) + "' in"
                + " jenesis.toolchain.searchpath: an entry is an absolute folder or starts with ~, and * stands"
                + " for a whole folder name only, as in /usr/lib/jvm/* or ~/.sdkman/candidates/java/*");
    }

    private static List<String> defaults() {
        String system = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (system.startsWith("windows")) {
            return List.of("C:\\Program Files\\Eclipse Adoptium\\*",
                    "C:\\Program Files\\Java\\*",
                    "C:\\Program Files\\Microsoft\\*",
                    "C:\\Program Files\\Zulu\\*",
                    "C:\\Program Files\\Amazon Corretto\\*",
                    "C:\\Program Files\\BellSoft\\*",
                    "~\\.jdks\\*",
                    "~\\scoop\\apps\\*\\current");
        } else if (system.startsWith("mac")) {
            return List.of("/Library/Java/JavaVirtualMachines/*/Contents/Home",
                    "~/Library/Java/JavaVirtualMachines/*/Contents/Home",
                    "/opt/homebrew/opt/*/libexec/openjdk.jdk/Contents/Home",
                    "~/.sdkman/candidates/java/*",
                    "~/.local/share/mise/installs/java/*");
        } else {
            return List.of("/usr/lib/jvm/*",
                    "~/.sdkman/candidates/java/*",
                    "~/.jdks/*",
                    "~/.local/share/mise/installs/java/*");
        }
    }

    private static String unsafe(Path home) throws IOException {
        if (!Files.getFileStore(home).supportsFileAttributeView(PosixFileAttributeView.class)) {
            return null;
        }
        UserPrincipalLookupService lookup = home.getFileSystem().getUserPrincipalLookupService();
        Set<UserPrincipal> owners = new HashSet<>();
        for (String name : List.of(System.getProperty("user.name"), "root")) {
            try {
                owners.add(lookup.lookupPrincipalByName(name));
            } catch (UserPrincipalNotFoundException _) {
            }
        }
        String[] problem = new String[1];
        Files.walkFileTree(home, EnumSet.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) throws IOException {
                return check(directory);
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                return check(file);
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exception) {
                return FileVisitResult.CONTINUE;
            }

            private FileVisitResult check(Path path) throws IOException {
                Path existing = path;
                Set<Path> followed = new HashSet<>();
                while (!Files.exists(existing)) {
                    existing = Files.isSymbolicLink(existing) && followed.add(existing)
                            ? existing.resolveSibling(Files.readSymbolicLink(existing))
                            : existing.getParent();
                }
                PosixFileAttributes attributes = Files.readAttributes(existing, PosixFileAttributes.class);
                if (!owners.contains(attributes.owner())) {
                    problem[0] = printable(existing) + " is owned by " + printable(attributes.owner().getName())
                            + ", neither the current user nor root";
                } else if (attributes.permissions().contains(PosixFilePermission.OTHERS_WRITE)) {
                    problem[0] = printable(existing) + " is writable by every user";
                } else if (attributes.permissions().contains(PosixFilePermission.GROUP_WRITE)
                        && !attributes.group().getName().equals(attributes.owner().getName())) {
                    problem[0] = printable(existing) + " is writable by the group "
                            + printable(attributes.group().getName());
                } else {
                    return FileVisitResult.CONTINUE;
                }
                return FileVisitResult.TERMINATE;
            }
        });
        return problem[0];
    }

    private static String printable(Object value) {
        return value.toString().codePoints()
                .map(code -> Character.isISOControl(code) ? '?' : code)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();
    }

    private record Candidate(Path home, Runtime.Version version, String implementor, SequencedSet<String> words) {
    }

    private record Entry(Path base, List<String> segments) {
    }
}
