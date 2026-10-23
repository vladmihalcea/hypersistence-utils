package build.jenesis.project;

import module java.base;
import build.jenesis.BuildExecutor;
import build.jenesis.BuildExecutorModule;
import build.jenesis.BuildStep;
import build.jenesis.Environment;
import build.jenesis.Repository;
import build.jenesis.Resolver;
import build.jenesis.SequencedProperties;

public class InferredSourceGenerationModule implements BuildExecutorModule {

    public static final String XJC = "xjc",
            PROTOC = "protoc",
            AVRO = "avro",
            WSIMPORT = "wsimport",
            OPENAPI = "openapi",
            ANTLR = "antlr",
            KSP = "ksp";

    private final SequencedSet<Path> configuration;
    private final Path location;
    private final Path root;
    private final ExternalModule xjcModule;
    private final boolean xjc;
    private final ExternalModule protocModule;
    private final boolean protoc;
    private final ExternalModule avroModule;
    private final boolean avro;
    private final ExternalModule wsimportModule;
    private final boolean wsimport;
    private final ExternalModule openapiModule;
    private final boolean openapi;
    private final ExternalModule antlrModule;
    private final boolean antlr;
    private final ExternalModule kspModule;
    private final boolean ksp;
    private final SequencedMap<String, BuildExecutorModule> custom;

    public InferredSourceGenerationModule(SequencedSet<Path> configuration,
                                          Path location,
                                          Path root,
                                          Map<String, Repository> repositories,
                                          Map<String, Resolver> resolvers) {
        this(configuration,
                location,
                root,
                ExternalModule.official(Environment.NONE, "xjc", null, repositories, resolvers),
                true,
                ExternalModule.official(Environment.NONE, "protoc", null, repositories, resolvers),
                true,
                ExternalModule.official(Environment.NONE, "avro", null, repositories, resolvers),
                true,
                ExternalModule.official(Environment.NONE, "wsimport", null, repositories, resolvers),
                true,
                ExternalModule.official(Environment.NONE, "openapi", null, repositories, resolvers),
                true,
                ExternalModule.official(Environment.NONE, "antlr", null, repositories, resolvers),
                true,
                ExternalModule.official(Environment.NONE, "ksp", null, repositories, resolvers),
                true,
                Collections.emptyNavigableMap());
    }

    public static InferredSourceGenerationModule ofEnvironment(Environment environment,
                                                               SequencedSet<Path> configuration,
                                                               Path location,
                                                               Path root,
                                                               Map<String, Repository> repositories,
                                                               Map<String, Resolver> resolvers) {
        return new InferredSourceGenerationModule(configuration,
                location,
                root,
                ExternalModule.official(environment, "xjc", null, repositories, resolvers),
                environment.flag("generate.xjc", true),
                ExternalModule.official(environment, "protoc", null, repositories, resolvers),
                environment.flag("generate.protoc", true),
                ExternalModule.official(environment, "avro", null, repositories, resolvers),
                environment.flag("generate.avro", true),
                ExternalModule.official(environment, "wsimport", null, repositories, resolvers),
                environment.flag("generate.wsimport", true),
                ExternalModule.official(environment, "openapi", null, repositories, resolvers),
                environment.flag("generate.openapi", true),
                ExternalModule.official(environment, "antlr", null, repositories, resolvers),
                environment.flag("generate.antlr", true),
                ExternalModule.official(environment, "ksp", null, repositories, resolvers),
                environment.flag("generate.ksp", true),
                Collections.emptyNavigableMap());
    }

    private InferredSourceGenerationModule(SequencedSet<Path> configuration,
                                           Path location,
                                           Path root,
                                           ExternalModule xjcModule,
                                           boolean xjc,
                                           ExternalModule protocModule,
                                           boolean protoc,
                                           ExternalModule avroModule,
                                           boolean avro,
                                           ExternalModule wsimportModule,
                                           boolean wsimport,
                                           ExternalModule openapiModule,
                                           boolean openapi,
                                           ExternalModule antlrModule,
                                           boolean antlr,
                                           ExternalModule kspModule,
                                           boolean ksp,
                                           SequencedMap<String, BuildExecutorModule> custom) {
        this.configuration = configuration;
        this.location = location;
        this.root = root;
        this.xjcModule = xjcModule;
        this.xjc = xjc;
        this.protocModule = protocModule;
        this.protoc = protoc;
        this.avroModule = avroModule;
        this.avro = avro;
        this.wsimportModule = wsimportModule;
        this.wsimport = wsimport;
        this.openapiModule = openapiModule;
        this.openapi = openapi;
        this.antlrModule = antlrModule;
        this.antlr = antlr;
        this.kspModule = kspModule;
        this.ksp = ksp;
        this.custom = custom;
    }

    public InferredSourceGenerationModule xjc(boolean xjc) {
        return new InferredSourceGenerationModule(configuration,
                location,
                root,
                xjcModule,
                xjc,
                protocModule,
                protoc,
                avroModule,
                avro,
                wsimportModule,
                wsimport,
                openapiModule,
                openapi,
                antlrModule,
                antlr,
                kspModule,
                ksp,
                custom);
    }

    public InferredSourceGenerationModule protoc(boolean protoc) {
        return new InferredSourceGenerationModule(configuration,
                location,
                root,
                xjcModule,
                xjc,
                protocModule,
                protoc,
                avroModule,
                avro,
                wsimportModule,
                wsimport,
                openapiModule,
                openapi,
                antlrModule,
                antlr,
                kspModule,
                ksp,
                custom);
    }

    public InferredSourceGenerationModule avro(boolean avro) {
        return new InferredSourceGenerationModule(configuration,
                location,
                root,
                xjcModule,
                xjc,
                protocModule,
                protoc,
                avroModule,
                avro,
                wsimportModule,
                wsimport,
                openapiModule,
                openapi,
                antlrModule,
                antlr,
                kspModule,
                ksp,
                custom);
    }

    public InferredSourceGenerationModule wsimport(boolean wsimport) {
        return new InferredSourceGenerationModule(configuration,
                location,
                root,
                xjcModule,
                xjc,
                protocModule,
                protoc,
                avroModule,
                avro,
                wsimportModule,
                wsimport,
                openapiModule,
                openapi,
                antlrModule,
                antlr,
                kspModule,
                ksp,
                custom);
    }

    public InferredSourceGenerationModule openapi(boolean openapi) {
        return new InferredSourceGenerationModule(configuration,
                location,
                root,
                xjcModule,
                xjc,
                protocModule,
                protoc,
                avroModule,
                avro,
                wsimportModule,
                wsimport,
                openapiModule,
                openapi,
                antlrModule,
                antlr,
                kspModule,
                ksp,
                custom);
    }

    public InferredSourceGenerationModule antlr(boolean antlr) {
        return new InferredSourceGenerationModule(configuration,
                location,
                root,
                xjcModule,
                xjc,
                protocModule,
                protoc,
                avroModule,
                avro,
                wsimportModule,
                wsimport,
                openapiModule,
                openapi,
                antlrModule,
                antlr,
                kspModule,
                ksp,
                custom);
    }

    public InferredSourceGenerationModule ksp(boolean ksp) {
        return new InferredSourceGenerationModule(configuration,
                location,
                root,
                xjcModule,
                xjc,
                protocModule,
                protoc,
                avroModule,
                avro,
                wsimportModule,
                wsimport,
                openapiModule,
                openapi,
                antlrModule,
                antlr,
                kspModule,
                ksp,
                custom);
    }

    public InferredSourceGenerationModule custom(SequencedMap<String, BuildExecutorModule> custom) {
        return new InferredSourceGenerationModule(configuration,
                location,
                root,
                xjcModule,
                xjc,
                protocModule,
                protoc,
                avroModule,
                avro,
                wsimportModule,
                wsimport,
                openapiModule,
                openapi,
                antlrModule,
                antlr,
                kspModule,
                ksp,
                custom);
    }

    public InferredSourceGenerationModule custom(String name, BuildExecutorModule module) {
        if (custom.containsKey(name)) {
            throw new IllegalArgumentException("A custom module named " + name + " is added already - give this one"
                    + " another name");
        }
        SequencedMap<String, BuildExecutorModule> added = new LinkedHashMap<>(custom);
        added.put(name, module);
        return custom(added);
    }

    public InferredSourceGenerationModule custom(String name, BuildStep step) {
        return custom(name, step.asModule(name));
    }

    @Override
    public void accept(BuildExecutor buildExecutor, SequencedMap<String, Path> inherited) throws IOException {
        Path xjcFile = BuildStep.locate(configuration, "xjc.properties");
        if (xjcFile != null && xjc) {
            SequencedMap<String, String> values = new LinkedHashMap<>();
            SequencedProperties.ofFiles(xjcFile).forEachProperty(values::put);
            buildExecutor.addModule(XJC, xjcModule.configured(location, root, values), inherited.sequencedKeySet());
        }
        Path protocFile = BuildStep.locate(configuration, "protoc.properties");
        if (protocFile != null && protoc) {
            SequencedMap<String, String> values = new LinkedHashMap<>();
            SequencedProperties.ofFiles(protocFile).forEachProperty(values::put);
            buildExecutor.addModule(PROTOC, protocModule.configured(location, root, values), inherited.sequencedKeySet());
        }
        Path avroFile = BuildStep.locate(configuration, "avro.properties");
        if (avroFile != null && avro) {
            SequencedMap<String, String> values = new LinkedHashMap<>();
            SequencedProperties.ofFiles(avroFile).forEachProperty(values::put);
            buildExecutor.addModule(AVRO, avroModule.configured(location, root, values), inherited.sequencedKeySet());
        }
        Path wsimportFile = BuildStep.locate(configuration, "wsimport.properties");
        if (wsimportFile != null && wsimport) {
            SequencedMap<String, String> values = new LinkedHashMap<>();
            SequencedProperties.ofFiles(wsimportFile).forEachProperty(values::put);
            buildExecutor.addModule(WSIMPORT, wsimportModule.configured(location, root, values), inherited.sequencedKeySet());
        }
        Path openapiFile = BuildStep.locate(configuration, "openapi.properties");
        if (openapiFile != null && openapi) {
            SequencedMap<String, String> values = new LinkedHashMap<>();
            SequencedProperties.ofFiles(openapiFile).forEachProperty(values::put);
            buildExecutor.addModule(OPENAPI, openapiModule.configured(location, root, values), inherited.sequencedKeySet());
        }
        Path antlrFile = BuildStep.locate(configuration, "antlr.properties");
        if (antlrFile != null && antlr) {
            SequencedMap<String, String> values = new LinkedHashMap<>();
            SequencedProperties.ofFiles(antlrFile).forEachProperty(values::put);
            buildExecutor.addModule(ANTLR, antlrModule.configured(location, root, values), inherited.sequencedKeySet());
        }
        Path kspFile = BuildStep.locate(configuration, "ksp.properties");
        if (kspFile != null && ksp) {
            SequencedMap<String, String> values = new LinkedHashMap<>();
            SequencedProperties.ofFiles(kspFile).forEachProperty(values::put);
            buildExecutor.addModule(KSP, kspModule.configured(location, root, values), inherited.sequencedKeySet());
        }
        if (!custom.isEmpty()) {
            buildExecutor.addModule("custom", (nested, nestedInherited) -> custom.forEach((name, module) ->
                    nested.addModule(name, module, nestedInherited.sequencedKeySet())), inherited.sequencedKeySet());
        }
    }
}
