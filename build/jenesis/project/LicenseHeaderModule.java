package build.jenesis.project;

import module java.base;
import build.jenesis.BuildExecutor;
import build.jenesis.BuildExecutorModule;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.BuildStepResult;
import build.jenesis.License;
import build.jenesis.SequencedProperties;
import build.jenesis.step.Dependencies;

public class LicenseHeaderModule implements BuildExecutorModule {

    public static final String FORMAT = "format", CONFIGURATION = "licenseheader.properties";
    private static final Set<String> EXTENSIONS = Set.of(".java", ".kt", ".scala", ".groovy");

    private final boolean verify;
    private final String text;

    public LicenseHeaderModule() {
        this(false, null);
    }

    private LicenseHeaderModule(boolean verify, String text) {
        this.verify = verify;
        this.text = text;
    }

    public static Path configurationFile(SequencedSet<Path> configuration) {
        return BuildStep.locate(configuration, CONFIGURATION);
    }

    public LicenseHeaderModule verify(boolean verify) {
        return new LicenseHeaderModule(verify, text);
    }

    public LicenseHeaderModule text(String text) {
        return new LicenseHeaderModule(verify, text);
    }

    @Override
    public void accept(BuildExecutor buildExecutor, SequencedMap<String, Path> inherited) {
        buildExecutor.addStep(FORMAT, new Header(verify, text), inherited.sequencedKeySet());
    }

    private record Header(boolean verify, String text) implements BuildStep {

        @Override
        public boolean shouldRun(SequencedMap<String, BuildStepArgument> arguments) {
            return !verify || arguments.values().stream().anyMatch(BuildStepArgument::hasChanged);
        }

        @Override
        public CompletionStage<BuildStepResult> apply(Executor executor,
                                                      BuildStepContext context,
                                                      SequencedMap<String, BuildStepArgument> arguments)
                throws IOException {
            List<Path> folders = arguments.values().stream()
                    .filter(argument -> !argument.removed())
                    .map(BuildStepArgument::folder)
                    .toList();
            SequencedMap<Path, Path> sources = new TreeMap<>();
            for (Path root : folders) {
                Path folder = root.resolve(BuildStep.SOURCES);
                if (Files.isDirectory(folder)) {
                    try (Stream<Path> files = Files.walk(folder, FileVisitOption.FOLLOW_LINKS)) {
                        files.filter(Files::isRegularFile)
                                .filter(file -> EXTENSIONS.stream().anyMatch(file.getFileName().toString()::endsWith))
                                .forEach(file -> sources.put(folder.relativize(file), file));
                    }
                }
            }
            String header;
            if (text == null) {
                SequencedProperties metadata = SequencedProperties.ofFolders(folders, BuildStep.METADATA);
                SequencedSet<String> declared = new TreeSet<>();
                for (String key : metadata.stringPropertyNames()) {
                    if (key.startsWith("license.") && key.lastIndexOf('.') > "license.".length()) {
                        declared.add(key.substring(0, key.lastIndexOf('.')));
                    }
                }
                if (declared.size() != 1) {
                    throw new IllegalStateException("A license header without a text names the one license the project"
                            + " declares, but it declares "
                            + (declared.isEmpty() ? "none" : declared.size() + " (" + String.join(", ", declared) + ")")
                            + " - declare one in the <licenses> of the pom.xml or as license.<id>.name in project.properties,"
                            + " or set text=<the header> in " + CONFIGURATION);
                }
                License license = new License(null,
                        null,
                        metadata.value(declared.getFirst() + ".name"),
                        metadata.value(declared.getFirst() + ".url")).identified(Dependencies.aliases(folders));
                if (license.id() == null) {
                    throw new IllegalStateException("The declared license " + license.label() + " has no SPDX identifier"
                            + " to name in a license header - map it with alias/<license name>=<SPDX id> in spdx.properties,"
                            + " or set text=<the header> in " + CONFIGURATION);
                }
                String copyright = metadata.value("copyright");
                header = (copyright == null ? "" : "// SPDX-FileCopyrightText: " + copyright + "\n")
                        + "// SPDX-License-Identifier: " + license.id();
            } else {
                header = text.replace("\r\n", "\n").stripTrailing().replaceFirst("^\n+", "");
            }
            if (!header.startsWith("/*") && !header.startsWith("//")) {
                header = header.lines()
                        .map(line -> (" * " + line).stripTrailing())
                        .collect(Collectors.joining("\n", "/*\n", "\n */"));
            }
            SequencedMap<Path, String> deviations = new LinkedHashMap<>();
            for (Map.Entry<Path, Path> source : sources.entrySet()) {
                String content = Files.readString(source.getValue());
                String text = content.replace("\r\n", "\n");
                int start = 0;
                while (start < text.length() && Character.isWhitespace(text.charAt(start))) {
                    start++;
                }
                int end = -1;
                if (text.startsWith("/*", start)) {
                    int close = text.indexOf("*/", start + 2);
                    end = close < 0 ? -1 : close + 2;
                } else if (text.startsWith("//", start)) {
                    int line = start;
                    while (text.startsWith("//", line)) {
                        int newline = text.indexOf('\n', line);
                        end = newline < 0 ? text.length() : newline;
                        line = end + 1;
                        while (line < text.length() && (text.charAt(line) == ' ' || text.charAt(line) == '\t')) {
                            line++;
                        }
                    }
                }
                String comment = end < 0 ? null : text.substring(start, end);
                if (header.equals(comment)) {
                    continue;
                }
                String lower = comment == null ? "" : comment.toLowerCase(Locale.ROOT);
                boolean replaced = lower.contains("copyright") || lower.contains("license") || lower.contains("licence");
                if (verify) {
                    deviations.put(source.getKey(), replaced ? "a different license header" : "no license header");
                    continue;
                }
                String formatted = replaced
                        ? text.substring(0, start) + header + text.substring(end)
                        : header + "\n\n" + text.substring(start);
                Files.writeString(source.getValue(), content.contains("\r\n")
                        ? formatted.replace("\n", "\r\n")
                        : formatted);
            }
            if (!deviations.isEmpty()) {
                throw new IllegalStateException("Not every source starts with the license header of " + CONFIGURATION
                        + " - run with -Djenesis.format.rewrite=true to write it:\n"
                        + deviations.entrySet().stream()
                                .map(entry -> "  " + entry.getKey() + ": " + entry.getValue())
                                .collect(Collectors.joining("\n")));
            }
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }
    }
}
