package build.jenesis.project;

import module java.base;
import build.jenesis.BuildExecutor;
import build.jenesis.BuildExecutorModule;
import build.jenesis.BuildStep;
import build.jenesis.Environment;
import build.jenesis.Repository;
import build.jenesis.Resolver;
import build.jenesis.SequencedProperties;

public class InferredSourceCodeQualityModule implements BuildExecutorModule {

    public static final String CHECKSTYLE = "checkstyle",
            PMD = "pmd",
            DETEKT = "detekt",
            KTLINT = "ktlint",
            SCALASTYLE = "scalastyle",
            SCALAFMT = "scalafmt",
            CODENARC = "codenarc";
    private static final Pattern CONFIG_LOC = Pattern.compile("\\$\\{config_loc}/([^\"'<>${}\\s]+)");

    private final SequencedSet<Path> configuration;
    private final ExternalModule checkstyleModule;
    private final boolean checkstyle;
    private final ExternalModule pmdModule;
    private final boolean pmd;
    private final ExternalModule detektModule;
    private final boolean detekt;
    private final ExternalModule ktlintModule;
    private final boolean ktlint;
    private final ExternalModule scalastyleModule;
    private final boolean scalastyle;
    private final ExternalModule scalafmtModule;
    private final boolean scalafmt;
    private final ExternalModule codenarcModule;
    private final boolean codenarc;
    private final SequencedMap<String, BuildExecutorModule> custom;

    public InferredSourceCodeQualityModule(SequencedSet<Path> configuration,
                                           Map<String, Repository> repositories,
                                           Map<String, Resolver> resolvers) {
        this(configuration,
                ExternalModule.official(Environment.NONE, "checkstyle", null, repositories, resolvers),
                true,
                ExternalModule.official(Environment.NONE, "pmd", null, repositories, resolvers),
                true,
                ExternalModule.official(Environment.NONE, "detekt", null, repositories, resolvers),
                true,
                ExternalModule.official(Environment.NONE, "ktlint", "check", repositories, resolvers),
                true,
                ExternalModule.official(Environment.NONE, "scalastyle", null, repositories, resolvers),
                true,
                ExternalModule.official(Environment.NONE, "scalafmt", "check", repositories, resolvers),
                true,
                ExternalModule.official(Environment.NONE, "codenarc", null, repositories, resolvers),
                true,
                Collections.emptyNavigableMap());
    }

    public static InferredSourceCodeQualityModule ofEnvironment(Environment environment,
                                                                SequencedSet<Path> configuration,
                                                                Map<String, Repository> repositories,
                                                                Map<String, Resolver> resolvers) {
        return new InferredSourceCodeQualityModule(configuration,
                ExternalModule.official(environment, "checkstyle", null, repositories, resolvers),
                environment.flag("check.checkstyle", true),
                ExternalModule.official(environment, "pmd", null, repositories, resolvers),
                environment.flag("check.pmd", true),
                ExternalModule.official(environment, "detekt", null, repositories, resolvers),
                environment.flag("check.detekt", true),
                ExternalModule.official(environment, "ktlint", "check", repositories, resolvers),
                environment.flag("check.ktlint", true),
                ExternalModule.official(environment, "scalastyle", null, repositories, resolvers),
                environment.flag("check.scalastyle", true),
                ExternalModule.official(environment, "scalafmt", "check", repositories, resolvers),
                environment.flag("check.scalafmt", true),
                ExternalModule.official(environment, "codenarc", null, repositories, resolvers),
                environment.flag("check.codenarc", true),
                Collections.emptyNavigableMap());
    }

    private InferredSourceCodeQualityModule(SequencedSet<Path> configuration,
                                            ExternalModule checkstyleModule,
                                            boolean checkstyle,
                                            ExternalModule pmdModule,
                                            boolean pmd,
                                            ExternalModule detektModule,
                                            boolean detekt,
                                            ExternalModule ktlintModule,
                                            boolean ktlint,
                                            ExternalModule scalastyleModule,
                                            boolean scalastyle,
                                            ExternalModule scalafmtModule,
                                            boolean scalafmt,
                                            ExternalModule codenarcModule,
                                            boolean codenarc,
                                            SequencedMap<String, BuildExecutorModule> custom) {
        this.configuration = configuration;
        this.checkstyleModule = checkstyleModule;
        this.checkstyle = checkstyle;
        this.pmdModule = pmdModule;
        this.pmd = pmd;
        this.detektModule = detektModule;
        this.detekt = detekt;
        this.ktlintModule = ktlintModule;
        this.ktlint = ktlint;
        this.scalastyleModule = scalastyleModule;
        this.scalastyle = scalastyle;
        this.scalafmtModule = scalafmtModule;
        this.scalafmt = scalafmt;
        this.codenarcModule = codenarcModule;
        this.codenarc = codenarc;
        this.custom = custom;
    }

    public InferredSourceCodeQualityModule checkstyle(boolean checkstyle) {
        return new InferredSourceCodeQualityModule(configuration,
                checkstyleModule,
                checkstyle,
                pmdModule,
                pmd,
                detektModule,
                detekt,
                ktlintModule,
                ktlint,
                scalastyleModule,
                scalastyle,
                scalafmtModule,
                scalafmt,
                codenarcModule,
                codenarc,
                custom);
    }

    public InferredSourceCodeQualityModule pmd(boolean pmd) {
        return new InferredSourceCodeQualityModule(configuration,
                checkstyleModule,
                checkstyle,
                pmdModule,
                pmd,
                detektModule,
                detekt,
                ktlintModule,
                ktlint,
                scalastyleModule,
                scalastyle,
                scalafmtModule,
                scalafmt,
                codenarcModule,
                codenarc,
                custom);
    }

    public InferredSourceCodeQualityModule detekt(boolean detekt) {
        return new InferredSourceCodeQualityModule(configuration,
                checkstyleModule,
                checkstyle,
                pmdModule,
                pmd,
                detektModule,
                detekt,
                ktlintModule,
                ktlint,
                scalastyleModule,
                scalastyle,
                scalafmtModule,
                scalafmt,
                codenarcModule,
                codenarc,
                custom);
    }

    public InferredSourceCodeQualityModule ktlint(boolean ktlint) {
        return new InferredSourceCodeQualityModule(configuration,
                checkstyleModule,
                checkstyle,
                pmdModule,
                pmd,
                detektModule,
                detekt,
                ktlintModule,
                ktlint,
                scalastyleModule,
                scalastyle,
                scalafmtModule,
                scalafmt,
                codenarcModule,
                codenarc,
                custom);
    }

    public InferredSourceCodeQualityModule scalastyle(boolean scalastyle) {
        return new InferredSourceCodeQualityModule(configuration,
                checkstyleModule,
                checkstyle,
                pmdModule,
                pmd,
                detektModule,
                detekt,
                ktlintModule,
                ktlint,
                scalastyleModule,
                scalastyle,
                scalafmtModule,
                scalafmt,
                codenarcModule,
                codenarc,
                custom);
    }

    public InferredSourceCodeQualityModule scalafmt(boolean scalafmt) {
        return new InferredSourceCodeQualityModule(configuration,
                checkstyleModule,
                checkstyle,
                pmdModule,
                pmd,
                detektModule,
                detekt,
                ktlintModule,
                ktlint,
                scalastyleModule,
                scalastyle,
                scalafmtModule,
                scalafmt,
                codenarcModule,
                codenarc,
                custom);
    }

    public InferredSourceCodeQualityModule codenarc(boolean codenarc) {
        return new InferredSourceCodeQualityModule(configuration,
                checkstyleModule,
                checkstyle,
                pmdModule,
                pmd,
                detektModule,
                detekt,
                ktlintModule,
                ktlint,
                scalastyleModule,
                scalastyle,
                scalafmtModule,
                scalafmt,
                codenarcModule,
                codenarc,
                custom);
    }

    public InferredSourceCodeQualityModule custom(SequencedMap<String, BuildExecutorModule> custom) {
        return new InferredSourceCodeQualityModule(configuration,
                checkstyleModule,
                checkstyle,
                pmdModule,
                pmd,
                detektModule,
                detekt,
                ktlintModule,
                ktlint,
                scalastyleModule,
                scalastyle,
                scalafmtModule,
                scalafmt,
                codenarcModule,
                codenarc,
                custom);
    }

    public InferredSourceCodeQualityModule custom(String name, BuildExecutorModule module) {
        if (custom.containsKey(name)) {
            throw new IllegalArgumentException("A custom module named " + name + " is added already - give this one"
                    + " another name");
        }
        SequencedMap<String, BuildExecutorModule> added = new LinkedHashMap<>(custom);
        added.put(name, module);
        return custom(added);
    }

    public InferredSourceCodeQualityModule custom(String name, BuildStep step) {
        return custom(name, step.asModule(name));
    }

    @Override
    public void accept(BuildExecutor buildExecutor, SequencedMap<String, Path> inherited) throws IOException {
        ExternalModule checkstyleDiscovered = checkstyle
                ? checkstyleModule.discovered(configuration, Path.of("checkstyle.xml"))
                : null;
        if (checkstyleDiscovered != null) {
            ExternalModule module = checkstyleDiscovered;
            Path checkstyleFile = BuildStep.locate(configuration, "checkstyle.properties");
            Path rules = checkstyleFile == null
                    ? BuildStep.locate(configuration, "checkstyle.xml")
                    : ExternalModule.named(checkstyleFile,
                            ExternalModule.CONFIGURATION,
                            SequencedProperties.ofFiles(checkstyleFile).value(ExternalModule.CONFIGURATION));
            Matcher matcher = CONFIG_LOC.matcher(Files.readString(rules));
            while (matcher.find()) {
                Path sibling = Path.of(matcher.group(1)).normalize();
                if (sibling.isAbsolute() || sibling.startsWith("..")) {
                    throw new IllegalArgumentException(rules + " references ${config_loc}/" + matcher.group(1)
                            + ", which is not beside or below it - place the file in the folder of "
                            + rules.getFileName());
                }
                if (!sibling.equals(rules.getFileName())
                        && !sibling.equals(Path.of("checkstyle.xml"))
                        && Files.isRegularFile(rules.resolveSibling(sibling))) {
                    module = module.bound(ExternalModule.RULES, sibling, rules.resolveSibling(sibling));
                }
            }
            buildExecutor.addModule(CHECKSTYLE, module, inherited.sequencedKeySet());
        }
        ExternalModule pmdDiscovered = pmd ? pmdModule.discovered(configuration, Path.of("pmd.xml")) : null;
        if (pmdDiscovered != null) {
            buildExecutor.addModule(PMD, pmdDiscovered, inherited.sequencedKeySet());
        }
        ExternalModule detektDiscovered = detekt ? detektModule.discovered(configuration, Path.of("detekt.yml")) : null;
        if (detektDiscovered != null) {
            buildExecutor.addModule(DETEKT, detektDiscovered, inherited.sequencedKeySet());
        }
        ExternalModule ktlintDiscovered = ktlint ? ktlintModule.discovered(configuration, Path.of(".editorconfig")) : null;
        if (ktlintDiscovered != null) {
            buildExecutor.addModule(KTLINT, ktlintDiscovered, inherited.sequencedKeySet());
        }
        ExternalModule scalastyleDiscovered = scalastyle
                ? scalastyleModule.discovered(configuration, Path.of("scalastyle-config.xml"))
                : null;
        if (scalastyleDiscovered != null) {
            buildExecutor.addModule(SCALASTYLE, scalastyleDiscovered, inherited.sequencedKeySet());
        }
        ExternalModule scalafmtDiscovered = scalafmt ? scalafmtModule.discovered(configuration, Path.of(".scalafmt.conf")) : null;
        if (scalafmtDiscovered != null) {
            buildExecutor.addModule(SCALAFMT, scalafmtDiscovered, inherited.sequencedKeySet());
        }
        ExternalModule codenarcDiscovered = codenarc ? codenarcModule.discovered(configuration, Path.of("codenarc.xml")) : null;
        if (codenarcDiscovered != null) {
            buildExecutor.addModule(CODENARC, codenarcDiscovered, inherited.sequencedKeySet());
        }
        if (!custom.isEmpty()) {
            buildExecutor.addModule("custom", (nested, nestedInherited) -> custom.forEach((name, module) ->
                    nested.addModule(name, module, nestedInherited.sequencedKeySet())), inherited.sequencedKeySet());
        }
    }
}
