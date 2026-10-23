package build.jenesis.project;

import module java.base;
import build.jenesis.BuildExecutor;
import build.jenesis.BuildExecutorModule;
import build.jenesis.BuildStep;
import build.jenesis.Environment;
import build.jenesis.PathPlacement;
import build.jenesis.Pinning;
import build.jenesis.Repository;
import build.jenesis.Resolver;
import build.jenesis.SequencedProperties;
import build.jenesis.step.Abi;
import build.jenesis.step.ClassPathCompatibility;
import build.jenesis.step.Jar;
import build.jenesis.step.JarSigner;
import build.jenesis.step.ProcessHandler;

public class InferredJavaToolchainModule implements BuildExecutorModule {

    public static final String MAVEN_PLUGIN = "mavenplugin";

    private final SequencedSet<Path> configuration;
    private final Pinning pinning;
    private final PathPlacement pathPlacement;
    private final InferredSourceGenerationModule generatorModule;
    private final InferredCompilerChainModule compilerModule;
    private final InferredByteCodeQualityModule validatorModule;
    private final JarSigner signerStep;
    private final ClassPathCompatibility classpathStep;
    private final Abi abiStep;
    private final Function<InferredSourceGenerationModule, BuildExecutorModule> generator;
    private final Function<InferredCompilerChainModule, BuildExecutorModule> compiler;
    private final Function<InferredByteCodeQualityModule, BuildExecutorModule> validator;
    private final BuildExecutorModule transformer;
    private final BuildExecutorModule archiver;
    private final Function<JarSigner, BuildStep> signer;
    private final Function<ClassPathCompatibility, BuildStep> classpath;
    private final Function<Abi, BuildStep> abi;
    private final SequencedMap<String, BuildExecutorModule> custom;
    private final Path location;
    private final Path root;
    private final ExternalModule mavenPluginModule;
    private final boolean mavenPlugin;

    public InferredJavaToolchainModule(SequencedSet<Path> configuration,
                                       Path location,
                                       Path root,
                                       Map<String, Repository> repositories,
                                       Map<String, Resolver> resolvers) {
        this(configuration,
                null,
                PathPlacement.INFERRED,
                new InferredSourceGenerationModule(configuration, location, root, repositories, resolvers),
                new InferredCompilerChainModule(repositories, resolvers),
                new InferredByteCodeQualityModule(configuration, repositories, resolvers),
                new JarSigner(),
                new ClassPathCompatibility(),
                new Abi(Abi.Mode.MODULAR),
                value -> value,
                value -> value,
                value -> value,
                null,
                new Jar(ProcessHandler.Factory.of(), Jar.Sort.CLASSES).asModule("jar"),
                step -> step.configured() ? step : null,
                value -> value,
                null,
                Collections.emptyNavigableMap(),
                location,
                root,
                ExternalModule.official(Environment.NONE, MAVEN_PLUGIN, null, repositories, resolvers),
                true);
    }

    public static InferredJavaToolchainModule ofEnvironment(Environment environment,
                                                            SequencedSet<Path> configuration,
                                                            Path location,
                                                            Path root,
                                                            Map<String, Repository> repositories,
                                                            Map<String, Resolver> resolvers) {
        String mode = environment.value("abi.mode");
        InferredJavaToolchainModule module = new InferredJavaToolchainModule(configuration,
                null,
                PathPlacement.INFERRED,
                InferredSourceGenerationModule.ofEnvironment(environment, configuration, location, root, repositories, resolvers),
                InferredCompilerChainModule.ofEnvironment(environment, repositories, resolvers),
                InferredByteCodeQualityModule.ofEnvironment(environment, configuration, repositories, resolvers),
                JarSigner.ofEnvironment(environment),
                new ClassPathCompatibility(),
                new Abi(mode == null ? Abi.Mode.MODULAR : Abi.Mode.of(mode)),
                value -> value,
                value -> value,
                value -> value,
                null,
                Jar.ofEnvironment(environment, ProcessHandler.Factory.ofEnvironment(environment), Jar.Sort.CLASSES).asModule("jar"),
                step -> step.configured() ? step : null,
                value -> value,
                mode == null ? null : value -> value,
                Collections.emptyNavigableMap(),
                location,
                root,
                ExternalModule.official(environment, MAVEN_PLUGIN, null, repositories, resolvers),
                environment.flag("artifact." + MAVEN_PLUGIN, true));
        return environment.flag("generate.classpath", true) ? module : module.classpath(null);
    }

    private InferredJavaToolchainModule(SequencedSet<Path> configuration,
                                        Pinning pinning,
                                        PathPlacement pathPlacement,
                                        InferredSourceGenerationModule generatorModule,
                                        InferredCompilerChainModule compilerModule,
                                        InferredByteCodeQualityModule validatorModule,
                                        JarSigner signerStep,
                                        ClassPathCompatibility classpathStep,
                                        Abi abiStep,
                                        Function<InferredSourceGenerationModule, BuildExecutorModule> generator,
                                        Function<InferredCompilerChainModule, BuildExecutorModule> compiler,
                                        Function<InferredByteCodeQualityModule, BuildExecutorModule> validator,
                                        BuildExecutorModule transformer,
                                        BuildExecutorModule archiver,
                                        Function<JarSigner, BuildStep> signer,
                                        Function<ClassPathCompatibility, BuildStep> classpath,
                                        Function<Abi, BuildStep> abi,
                                        SequencedMap<String, BuildExecutorModule> custom,
                                        Path location,
                                        Path root,
                                        ExternalModule mavenPluginModule,
                                        boolean mavenPlugin) {
        this.configuration = configuration;
        this.pinning = pinning;
        this.pathPlacement = pathPlacement;
        this.generatorModule = generatorModule;
        this.compilerModule = compilerModule;
        this.validatorModule = validatorModule;
        this.signerStep = signerStep;
        this.classpathStep = classpathStep;
        this.abiStep = abiStep;
        this.generator = generator;
        this.compiler = compiler;
        this.validator = validator;
        this.transformer = transformer;
        this.archiver = archiver;
        this.signer = signer;
        this.classpath = classpath;
        this.abi = abi;
        this.custom = custom;
        this.location = location;
        this.root = root;
        this.mavenPluginModule = mavenPluginModule;
        this.mavenPlugin = mavenPlugin;
    }

    public InferredJavaToolchainModule pinning(Pinning pinning) {
        return new InferredJavaToolchainModule(configuration,
                pinning,
                pathPlacement,
                generatorModule,
                compilerModule,
                validatorModule,
                signerStep,
                classpathStep,
                abiStep,
                generator,
                compiler,
                validator,
                transformer,
                archiver,
                signer,
                classpath,
                abi,
                custom,
                location,
                root,
                mavenPluginModule,
                mavenPlugin);
    }

    public InferredJavaToolchainModule pathPlacement(PathPlacement pathPlacement) {
        return new InferredJavaToolchainModule(configuration,
                pinning,
                pathPlacement,
                generatorModule,
                compilerModule,
                validatorModule,
                signerStep,
                classpathStep,
                abiStep,
                generator,
                compiler,
                validator,
                transformer,
                archiver,
                signer,
                classpath,
                abi,
                custom,
                location,
                root,
                mavenPluginModule,
                mavenPlugin);
    }

    public InferredJavaToolchainModule generator(Function<InferredSourceGenerationModule, BuildExecutorModule> generator) {
        return new InferredJavaToolchainModule(configuration,
                pinning,
                pathPlacement,
                generatorModule,
                compilerModule,
                validatorModule,
                signerStep,
                classpathStep,
                abiStep,
                generator,
                compiler,
                validator,
                transformer,
                archiver,
                signer,
                classpath,
                abi,
                custom,
                location,
                root,
                mavenPluginModule,
                mavenPlugin);
    }

    public InferredJavaToolchainModule compiler(Function<InferredCompilerChainModule, BuildExecutorModule> compiler) {
        return new InferredJavaToolchainModule(configuration,
                pinning,
                pathPlacement,
                generatorModule,
                compilerModule,
                validatorModule,
                signerStep,
                classpathStep,
                abiStep,
                generator,
                compiler,
                validator,
                transformer,
                archiver,
                signer,
                classpath,
                abi,
                custom,
                location,
                root,
                mavenPluginModule,
                mavenPlugin);
    }

    public InferredJavaToolchainModule validator(Function<InferredByteCodeQualityModule, BuildExecutorModule> validator) {
        return new InferredJavaToolchainModule(configuration,
                pinning,
                pathPlacement,
                generatorModule,
                compilerModule,
                validatorModule,
                signerStep,
                classpathStep,
                abiStep,
                generator,
                compiler,
                validator,
                transformer,
                archiver,
                signer,
                classpath,
                abi,
                custom,
                location,
                root,
                mavenPluginModule,
                mavenPlugin);
    }

    public InferredJavaToolchainModule transformer(BuildExecutorModule transformer) {
        return new InferredJavaToolchainModule(configuration,
                pinning,
                pathPlacement,
                generatorModule,
                compilerModule,
                validatorModule,
                signerStep,
                classpathStep,
                abiStep,
                generator,
                compiler,
                validator,
                transformer,
                archiver,
                signer,
                classpath,
                abi,
                custom,
                location,
                root,
                mavenPluginModule,
                mavenPlugin);
    }

    public InferredJavaToolchainModule archiver(BuildExecutorModule archiver) {
        return new InferredJavaToolchainModule(configuration,
                pinning,
                pathPlacement,
                generatorModule,
                compilerModule,
                validatorModule,
                signerStep,
                classpathStep,
                abiStep,
                generator,
                compiler,
                validator,
                transformer,
                archiver,
                signer,
                classpath,
                abi,
                custom,
                location,
                root,
                mavenPluginModule,
                mavenPlugin);
    }

    public InferredJavaToolchainModule signer(Function<JarSigner, BuildStep> signer) {
        return new InferredJavaToolchainModule(configuration,
                pinning,
                pathPlacement,
                generatorModule,
                compilerModule,
                validatorModule,
                signerStep,
                classpathStep,
                abiStep,
                generator,
                compiler,
                validator,
                transformer,
                archiver,
                signer,
                classpath,
                abi,
                custom,
                location,
                root,
                mavenPluginModule,
                mavenPlugin);
    }

    public InferredJavaToolchainModule classpath(Function<ClassPathCompatibility, BuildStep> classpath) {
        return new InferredJavaToolchainModule(configuration,
                pinning,
                pathPlacement,
                generatorModule,
                compilerModule,
                validatorModule,
                signerStep,
                classpathStep,
                abiStep,
                generator,
                compiler,
                validator,
                transformer,
                archiver,
                signer,
                classpath,
                abi,
                custom,
                location,
                root,
                mavenPluginModule,
                mavenPlugin);
    }

    public InferredJavaToolchainModule abi(Function<Abi, BuildStep> abi) {
        return new InferredJavaToolchainModule(configuration,
                pinning,
                pathPlacement,
                generatorModule,
                compilerModule,
                validatorModule,
                signerStep,
                classpathStep,
                abiStep,
                generator,
                compiler,
                validator,
                transformer,
                archiver,
                signer,
                classpath,
                abi,
                custom,
                location,
                root,
                mavenPluginModule,
                mavenPlugin);
    }

    public InferredJavaToolchainModule generatorModule(InferredSourceGenerationModule generatorModule) {
        return new InferredJavaToolchainModule(configuration,
                pinning,
                pathPlacement,
                generatorModule,
                compilerModule,
                validatorModule,
                signerStep,
                classpathStep,
                abiStep,
                generator,
                compiler,
                validator,
                transformer,
                archiver,
                signer,
                classpath,
                abi,
                custom,
                location,
                root,
                mavenPluginModule,
                mavenPlugin);
    }

    public InferredJavaToolchainModule compilerModule(InferredCompilerChainModule compilerModule) {
        return new InferredJavaToolchainModule(configuration,
                pinning,
                pathPlacement,
                generatorModule,
                compilerModule,
                validatorModule,
                signerStep,
                classpathStep,
                abiStep,
                generator,
                compiler,
                validator,
                transformer,
                archiver,
                signer,
                classpath,
                abi,
                custom,
                location,
                root,
                mavenPluginModule,
                mavenPlugin);
    }

    public InferredJavaToolchainModule validatorModule(InferredByteCodeQualityModule validatorModule) {
        return new InferredJavaToolchainModule(configuration,
                pinning,
                pathPlacement,
                generatorModule,
                compilerModule,
                validatorModule,
                signerStep,
                classpathStep,
                abiStep,
                generator,
                compiler,
                validator,
                transformer,
                archiver,
                signer,
                classpath,
                abi,
                custom,
                location,
                root,
                mavenPluginModule,
                mavenPlugin);
    }

    public InferredJavaToolchainModule custom(SequencedMap<String, BuildExecutorModule> custom) {
        return new InferredJavaToolchainModule(configuration,
                pinning,
                pathPlacement,
                generatorModule,
                compilerModule,
                validatorModule,
                signerStep,
                classpathStep,
                abiStep,
                generator,
                compiler,
                validator,
                transformer,
                archiver,
                signer,
                classpath,
                abi,
                custom,
                location,
                root,
                mavenPluginModule,
                mavenPlugin);
    }

    public InferredJavaToolchainModule mavenPlugin(boolean mavenPlugin) {
        return new InferredJavaToolchainModule(configuration,
                pinning,
                pathPlacement,
                generatorModule,
                compilerModule,
                validatorModule,
                signerStep,
                classpathStep,
                abiStep,
                generator,
                compiler,
                validator,
                transformer,
                archiver,
                signer,
                classpath,
                abi,
                custom,
                location,
                root,
                mavenPluginModule,
                mavenPlugin);
    }

    public InferredJavaToolchainModule custom(String name, BuildExecutorModule module) {
        if (custom.containsKey(name)) {
            throw new IllegalArgumentException("A custom module named " + name + " is added already - give this one"
                    + " another name");
        }
        SequencedMap<String, BuildExecutorModule> added = new LinkedHashMap<>(custom);
        added.put(name, module);
        return custom(added);
    }

    public InferredJavaToolchainModule custom(String name, BuildStep step) {
        return custom(name, step.asModule(name));
    }

    @Override
    public void accept(BuildExecutor buildExecutor, SequencedMap<String, Path> inherited) {
        toolchain(signer == null ? null : signer.apply(signerStep)).accept(buildExecutor, inherited);
        if (!custom.isEmpty()) {
            buildExecutor.addModule("custom", (nested, nestedInherited) -> custom.forEach((name, module) ->
                    nested.addModule(name, module, nestedInherited.sequencedKeySet())), inherited.sequencedKeySet());
        }
    }

    @Override
    public Optional<String> resolve(String path) {
        return toolchain(null).resolve(path);
    }

    private JavaToolchainModule toolchain(BuildStep signing) {
        BuildExecutorModule compiled = compiler == null ? null : compiler.apply(compilerModule
                .pathPlacement(pathPlacement));
        if (compiled == null) {
            throw new IllegalStateException("A Java toolchain requires a compiler but none is configured");
        }
        BuildStep compatibility = classpath == null
                || BuildStep.locate(configuration, ClassPathCompatibility.CONFIGURATION) == null
                ? null
                : classpath.apply(classpathStep);
        BuildStep extraction = abi == null ? null : abi.apply(abiStep);
        Path described = mavenPlugin ? BuildStep.locate(configuration, MAVEN_PLUGIN + ".properties") : null;
        BuildExecutorModule transforming = transformer;
        if (described != null) {
            SequencedMap<String, String> values = new LinkedHashMap<>();
            try {
                SequencedProperties.ofFiles(described).forEachProperty(values::put);
            } catch (IOException e) {
                throw new UncheckedIOException("Cannot read " + described, e);
            }
            SequencedMap<String, BuildExecutorModule> transforms = new LinkedHashMap<>();
            if (transformer instanceof ClassTransformModule(SequencedMap<String, BuildExecutorModule> declared)) {
                transforms.putAll(declared);
            } else if (transformer != null) {
                throw new IllegalStateException(described + " has the Maven plugin descriptor written into the"
                        + " compiled classes, which a transformer of its own replaces - hand the transforms as a"
                        + " ClassTransformModule, or switch the descriptor off with -Djenesis.artifact."
                        + MAVEN_PLUGIN + "=false");
            }
            transforms.put(MAVEN_PLUGIN, mavenPluginModule.configured(location, root, values));
            transforming = new ClassTransformModule(transforms);
        }
        return new JavaToolchainModule(
                generator == null ? null : generator.apply(generatorModule),
                compiled,
                transforming,
                validator == null ? null : validator.apply(validatorModule),
                signing == null || archiver == null ? archiver : new SignedArchive(archiver, signing),
                compatibility == null ? null : compatibility.asModule("services"),
                extraction == null ? null : extraction.asModule("extract"));
    }

    private record SignedArchive(BuildExecutorModule archiver, BuildStep signer) implements BuildExecutorModule {

        private static final String ARCHIVE = "archive", SIGN = "sign";

        @Override
        public void accept(BuildExecutor buildExecutor, SequencedMap<String, Path> inherited) {
            buildExecutor.addModule(ARCHIVE, archiver, inherited.sequencedKeySet());
            buildExecutor.addStep(SIGN, signer, Stream.concat(
                    Stream.of(ARCHIVE),
                    inherited.sequencedKeySet().stream()));
        }

        @Override
        public Optional<String> resolve(String path) {
            return path.equals(SIGN) ? Optional.of("") : Optional.empty();
        }
    }
}
