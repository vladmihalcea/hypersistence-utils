package build.jenesis.docker;

import module java.base;

public class DockerizedJava {

    public static final String IMPLICIT_DOCKERFILE_LINUX = "FROM debian:stable-slim\n",
            IMPLICIT_DOCKERFILE_WINDOWS = "FROM mcr.microsoft.com/windows/servercore:ltsc2025\n";
    public static final String JAVA_HOME_MOUNT_LINUX = "/opt/java-home",
            JAVA_HOME_MOUNT_WINDOWS = "C:\\opt\\java-home";

    private final String image;
    private final Path workingDirectory;
    private final Map<Path, String> mounts;
    private final Map<String, String> environment;
    private final Boolean windowsDaemon;
    private final boolean hardened;
    private final Path home;

    public DockerizedJava(Path workingDirectory) throws IOException, InterruptedException {
        boolean windows = isWindowsDaemon();
        String dockerfile = windows ? IMPLICIT_DOCKERFILE_WINDOWS : IMPLICIT_DOCKERFILE_LINUX;
        String image;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(dockerfile.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(12);
            for (int index = 0; index < 6; index++) {
                builder.append(String.format("%02x", digest[index]));
            }
            image = "jenesis-build:" + builder;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        Process inspect = new ProcessBuilder("docker", "image", "inspect", image)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
        if (inspect.waitFor() != 0) {
            Process build = new ProcessBuilder("docker", "build", "-q", "-t", image, "-")
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.INHERIT)
                    .start();
            try (OutputStream out = build.getOutputStream()) {
                out.write(dockerfile.getBytes(StandardCharsets.UTF_8));
            }
            int code = build.waitFor();
            if (code != 0) {
                throw new IOException("Failed to build implicit Docker image " + image + ": exit code " + code);
            }
        }
        this.workingDirectory = workingDirectory;
        this.image = image;
        this.windowsDaemon = windows;
        this.mounts = Map.of();
        this.environment = Map.of();
        this.hardened = true;
        this.home = javaHome();
    }

    public DockerizedJava(Path workingDirectory, String image) {
        this(workingDirectory, image, null, Map.of(), Map.of(), false, javaHome());
    }

    private DockerizedJava(Path workingDirectory,
                           String image,
                           Boolean windowsDaemon,
                           Map<Path, String> mounts,
                           Map<String, String> environment,
                           boolean hardened,
                           Path home) {
        this.image = image;
        this.workingDirectory = workingDirectory;
        this.windowsDaemon = windowsDaemon;
        this.mounts = mounts;
        this.environment = environment;
        this.hardened = hardened;
        this.home = home;
    }

    private static Path javaHome() {
        String home = System.getProperty("java.home");
        if (home == null) {
            home = System.getenv("JAVA_HOME");
        }
        return home == null ? null : Path.of(home);
    }

    public String image() {
        return image;
    }

    public boolean hardened() {
        return hardened;
    }

    public Path home() {
        return home;
    }

    public DockerizedJava home(Path home) {
        return new DockerizedJava(workingDirectory, image, windowsDaemon, mounts, environment, hardened, home);
    }

    public DockerizedJava harden(boolean hardened) {
        return new DockerizedJava(workingDirectory, image, windowsDaemon, mounts, environment, hardened, home);
    }

    public DockerizedJava mount(Path host, String container, boolean readOnly) {
        SequencedMap<Path, String> copy = new LinkedHashMap<>(mounts);
        copy.put(host.toAbsolutePath(), container + (readOnly ? ":ro" : ""));
        return new DockerizedJava(workingDirectory, image, windowsDaemon, copy, environment, hardened, home);
    }

    public DockerizedJava mounts(String specification, Path base, boolean readOnly) {
        DockerizedJava docker = this;
        for (Mount mount : Mount.parse(specification, base)) {
            docker = docker.mount(mount.host(), mount.container(), readOnly);
        }
        return docker;
    }

    public DockerizedJava envs(String specification) {
        DockerizedJava docker = this;
        for (Env entry : Env.parse(specification)) {
            String value = entry.value() == null ? System.getenv(entry.name()) : entry.value();
            if (value != null) {
                docker = docker.env(entry.name(), value);
            }
        }
        return docker;
    }

    public DockerizedJava env(String name, String value) {
        SequencedMap<String, String> copy = new LinkedHashMap<>(environment);
        copy.put(name, value);
        return new DockerizedJava(workingDirectory, image, windowsDaemon, mounts, copy, hardened, home);
    }

    public int execute(String main, Map<String, String> properties, String... args) throws IOException, InterruptedException {
        List<String> javaArgs = new ArrayList<>();
        for (Map.Entry<String, String> property : properties.entrySet()) {
            javaArgs.add("-D" + property.getKey() + "=" + property.getValue());
        }
        javaArgs.add(main);
        javaArgs.addAll(Arrays.asList(args));
        return execute(javaArgs);
    }

    public int execute(List<String> javaArgs) throws IOException, InterruptedException {
        return new ProcessBuilder(command(javaArgs)).inheritIO().start().waitFor();
    }

    public List<String> command(List<String> javaArgs) throws IOException, InterruptedException {
        if (home == null) {
            throw new IllegalStateException("Neither JAVA_HOME environment or java.home property set");
        }
        Path javaHome = home.toAbsolutePath();
        boolean windows = windowsDaemon != null ? windowsDaemon : isWindowsDaemon();
        String javaHomeMount = windows ? JAVA_HOME_MOUNT_WINDOWS : JAVA_HOME_MOUNT_LINUX;
        List<String> docker = new ArrayList<>();
        docker.add("docker");
        docker.add("run");
        docker.add("--rm");
        docker.add("-i");
        if (hardened) {
            docker.add("--cap-drop");
            docker.add("ALL");
            docker.add("--security-opt");
            docker.add("no-new-privileges");
        }
        if (!windows) {
            try {
                Object uid = Files.getAttribute(workingDirectory, "unix:uid");
                Object gid = Files.getAttribute(workingDirectory, "unix:gid");
                docker.add("--user");
                docker.add(uid + ":" + gid);
            } catch (UnsupportedOperationException | IllegalArgumentException _) {
            }
        }
        docker.add("-w");
        docker.add(workingDirectory.toString());
        docker.add("-v");
        docker.add(workingDirectory + ":" + workingDirectory);
        docker.add("-v");
        docker.add(javaHome + ":" + javaHomeMount + ":ro");
        for (Map.Entry<Path, String> mount : mounts.entrySet()) {
            docker.add("-v");
            docker.add(mount.getKey() + ":" + mount.getValue());
        }
        for (Map.Entry<String, String> variable : environment.entrySet()) {
            docker.add("-e");
            docker.add(variable.getKey() + "=" + variable.getValue());
        }
        docker.add(image);
        docker.add(javaHomeMount + (windows ? "\\bin\\java.exe" : "/bin/java"));
        docker.addAll(javaArgs);
        return docker;
    }

    private static boolean isWindowsDaemon() throws IOException, InterruptedException {
        Process process = new ProcessBuilder("docker", "version", "--format", "{{.Server.Os}}")
                .redirectErrorStream(true)
                .start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        if (process.waitFor() != 0) {
            throw new IOException("Failed to query Docker server OS: " + output);
        }
        return "windows".equals(output);
    }

    public record Mount(Path host, String container) {

        public static List<Mount> parse(String specification, Path base) {
            List<Mount> mounts = new ArrayList<>();
            if (specification == null) {
                return mounts;
            }
            for (String entry : specification.split(",")) {
                String spec = entry.strip();
                if (spec.isEmpty()) {
                    continue;
                }
                int start = spec.length() > 1 && spec.charAt(1) == ':' && Character.isLetter(spec.charAt(0)) ? 2 : 0;
                int separator = spec.indexOf(':', start);
                Path host = base.resolve(separator < 0 ? spec : spec.substring(0, separator)).normalize();
                mounts.add(new Mount(host, separator < 0 ? host.toString() : spec.substring(separator + 1)));
            }
            return mounts;
        }
    }

    public record Env(String name, String value) {

        public static List<Env> parse(String specification) {
            List<Env> envs = new ArrayList<>();
            if (specification == null) {
                return envs;
            }
            for (String entry : specification.split(",")) {
                String spec = entry.strip();
                if (spec.isEmpty()) {
                    continue;
                }
                int separator = spec.indexOf('=');
                envs.add(separator < 0
                        ? new Env(spec, null)
                        : new Env(spec.substring(0, separator), spec.substring(separator + 1)));
            }
            return envs;
        }
    }
}
