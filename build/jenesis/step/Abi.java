package build.jenesis.step;

import module java.base;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.BuildStepResult;
import build.jenesis.Checksum;
import build.jenesis.ChecksumStatus;
import build.jenesis.SequencedProperties;
import java.lang.classfile.Annotation;
import java.lang.classfile.Attributes;

public record Abi(Mode mode) implements BuildStep {

    public static final String ABI = "abi.properties", ABIS = "abis.properties", FOLDER = "abi/";
    private static final String STATE = "abi-state.properties",
            FORMAT = "1",
            MODULE_INFO = "module-info",
            PACKAGE_INFO = "package-info",
            NONE = "-";
    private static final int CLASS_FLAGS = ClassFile.ACC_PUBLIC
            | ClassFile.ACC_FINAL
            | ClassFile.ACC_INTERFACE
            | ClassFile.ACC_ABSTRACT
            | ClassFile.ACC_ANNOTATION
            | ClassFile.ACC_ENUM
            | ClassFile.ACC_MODULE,
            NESTED_FLAGS = ClassFile.ACC_PUBLIC
                    | ClassFile.ACC_PROTECTED
                    | ClassFile.ACC_PRIVATE
                    | ClassFile.ACC_STATIC
                    | ClassFile.ACC_FINAL
                    | ClassFile.ACC_INTERFACE
                    | ClassFile.ACC_ABSTRACT
                    | ClassFile.ACC_ANNOTATION
                    | ClassFile.ACC_ENUM,
            FIELD_FLAGS = ClassFile.ACC_PUBLIC
                    | ClassFile.ACC_PROTECTED
                    | ClassFile.ACC_STATIC
                    | ClassFile.ACC_FINAL
                    | ClassFile.ACC_ENUM,
            METHOD_FLAGS = ClassFile.ACC_PUBLIC
                    | ClassFile.ACC_PROTECTED
                    | ClassFile.ACC_STATIC
                    | ClassFile.ACC_FINAL
                    | ClassFile.ACC_ABSTRACT
                    | ClassFile.ACC_VARARGS;

    public enum Mode {

        MODULAR, CLASSPATH;

        public static Mode of(String value) {
            return switch (value) {
                case "modular" -> MODULAR;
                case "classpath" -> CLASSPATH;
                default -> throw new IllegalArgumentException("jenesis.abi.mode is " + value
                        + " - expected modular, for what other modules reach through the exports,"
                        + " or classpath, for what any class on the class path reaches");
            };
        }
    }

    private enum Access {
        API, PACKAGE, HIDDEN
    }

    public Abi mode(Mode mode) {
        return new Abi(mode);
    }

    public static String companion(String jar) {
        return (jar.endsWith(".jar") ? jar.substring(0, jar.length() - ".jar".length()) : jar) + "." + ABI;
    }

    @Override
    public CompletionStage<BuildStepResult> apply(Executor executor,
                                                  BuildStepContext context,
                                                  SequencedMap<String, BuildStepArgument> arguments) throws IOException {
        Path state = context.persistent().resolve(STATE);
        SequencedProperties recorded = context.previous() != null && Files.isRegularFile(state)
                ? SequencedProperties.ofFiles(state)
                : new SequencedProperties();
        if (!FORMAT.equals(recorded.getProperty("format")) || !mode.name().equals(recorded.getProperty("mode"))) {
            recorded = new SequencedProperties();
        }
        Path classes = Path.of(CLASSES);
        SortedMap<String, Entry> entries = new TreeMap<>();
        for (BuildStepArgument argument : arguments.values()) {
            if (argument.removed()) {
                continue;
            }
            for (Map.Entry<Path, Checksum> file : argument.files().entrySet()) {
                Path path = file.getKey();
                if (!path.startsWith(classes)
                        || !path.getFileName().toString().endsWith(".class")
                        || file.getValue().status() == ChecksumStatus.REMOVED) {
                    continue;
                }
                String key = classes.relativize(path).toString().replace(File.separatorChar, '/'),
                        line = file.getValue().status() == ChecksumStatus.RETAINED ? recorded.getProperty(key) : null;
                Entry entry = line == null ? entry(argument.folder().resolve(path)) : Entry.parse(line);
                if (entries.put(key, entry) != null) {
                    throw new IllegalStateException("The class file " + key + " is handed by more than one input"
                            + " - hand the ABI the classes of one module");
                }
            }
        }
        SequencedProperties written = new SequencedProperties();
        written.setProperty("format", FORMAT);
        written.setProperty("mode", mode.name());
        entries.forEach((key, entry) -> written.setProperty(key, entry.toString()));
        written.store(state);
        Map<String, List<Entry>> named = new HashMap<>();
        entries.values().forEach(entry -> named.computeIfAbsent(entry.name(), _ -> new ArrayList<>()).add(entry));
        Set<String> exports = entries.values().stream()
                .filter(entry -> entry.name().equals(MODULE_INFO))
                .flatMap(entry -> entry.exports().stream())
                .collect(Collectors.toCollection(HashSet::new));
        boolean modular = mode == Mode.MODULAR && named.containsKey(MODULE_INFO);
        Map<String, Access> effective = new HashMap<>();
        Set<String> included = new HashSet<>();
        Deque<String> pending = new ArrayDeque<>();
        for (Entry entry : entries.values()) {
            String name = entry.name(), owner = name.substring(0, Math.max(0, name.lastIndexOf('/')));
            boolean visible = switch (mode) {
                case MODULAR -> entry.access(named, effective) == Access.API;
                case CLASSPATH -> entry.access(named, effective) != Access.HIDDEN;
            };
            if (name.equals(MODULE_INFO)
                    || visible && (!modular || exports.contains(owner))) {
                if (included.add(name)) {
                    pending.add(name);
                }
            }
        }
        while (!pending.isEmpty()) {
            for (Entry entry : named.get(pending.remove())) {
                Stream.concat(Stream.of(entry.superclass()), entry.interfaces().stream())
                        .filter(named::containsKey)
                        .filter(included::add)
                        .forEach(pending::add);
            }
        }
        SequencedProperties abi = new SequencedProperties();
        entries.forEach((key, entry) -> {
            if (included.contains(entry.name())) {
                abi.setProperty(key, entry.hash());
            }
        });
        abi.store(context.next().resolve(ABI));
        return CompletableFuture.completedStage(new BuildStepResult(true));
    }

    private Entry entry(Path file) throws IOException {
        ClassModel model = ClassFile.of().parse(file);
        String name = model.thisClass().asInternalName();
        Optional<InnerClassInfo> nested = model.findAttribute(Attributes.innerClasses()).stream()
                .flatMap(attribute -> attribute.classes().stream())
                .filter(info -> info.innerClass().asInternalName().equals(name))
                .findFirst();
        Access access;
        String outer = NONE;
        if (name.equals(MODULE_INFO) || name.equals(PACKAGE_INFO) || name.endsWith("/" + PACKAGE_INFO)) {
            access = Access.API;
        } else if ((model.flags().flagsMask() & ClassFile.ACC_SYNTHETIC) != 0) {
            access = Access.HIDDEN;
        } else if (nested.isPresent()) {
            if (nested.get().outerClass().isEmpty()) {
                access = Access.HIDDEN;
            } else {
                outer = nested.get().outerClass().get().asInternalName();
                access = access(nested.get().flagsMask());
            }
        } else {
            access = access(model.flags().flagsMask());
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeInt(model.majorVersion());
        out.writeInt(model.minorVersion());
        out.writeInt(model.flags().flagsMask() & CLASS_FLAGS);
        out.writeInt(nested.map(InnerClassInfo::flagsMask).orElse(0) & NESTED_FLAGS);
        write(out, name);
        write(out, model.superclass().map(ClassEntry::asInternalName).orElse(""));
        List<String> interfaces = model.interfaces().stream().map(ClassEntry::asInternalName).toList();
        out.writeInt(interfaces.size());
        for (String type : interfaces) {
            write(out, type);
        }
        element(out, model);
        List<String> permitted = model.findAttribute(Attributes.permittedSubclasses()).stream()
                .flatMap(attribute -> attribute.permittedSubclasses().stream())
                .map(ClassEntry::asInternalName)
                .sorted()
                .toList();
        out.writeInt(permitted.size());
        for (String type : permitted) {
            write(out, type);
        }
        List<RecordComponentInfo> components = model.findAttribute(Attributes.record())
                .map(RecordAttribute::components)
                .orElse(List.of());
        out.writeInt(components.size());
        for (RecordComponentInfo component : components) {
            write(out, component.name().stringValue());
            write(out, component.descriptor().stringValue());
            element(out, component);
        }
        List<String> exports = List.of();
        Optional<ModuleAttribute> module = model.findAttribute(Attributes.module());
        if (module.isPresent()) {
            write(out, module.get().moduleName().name().stringValue());
            out.writeInt(module.get().moduleFlagsMask());
            SortedMap<String, List<String>> exported = new TreeMap<>();
            for (ModuleExportInfo export : module.get().exports()) {
                exported.put(export.exportedPackage().name().stringValue(), export.exportsTo().stream()
                        .map(target -> target.name().stringValue())
                        .sorted()
                        .toList());
            }
            for (Map.Entry<String, List<String>> export : exported.entrySet()) {
                write(out, export.getKey());
                out.writeInt(export.getValue().size());
                for (String target : export.getValue()) {
                    write(out, target);
                }
            }
            exports = List.copyOf(exported.keySet());
            List<String> transitive = module.get().requires().stream()
                    .filter(require -> (require.requiresFlagsMask() & ClassFile.ACC_TRANSITIVE) != 0)
                    .map(require -> require.requires().name().stringValue())
                    .sorted()
                    .toList();
            out.writeInt(transitive.size());
            for (String require : transitive) {
                write(out, require);
            }
        }
        SortedMap<String, byte[]> members = new TreeMap<>();
        for (FieldModel field : model.fields()) {
            if (!included(field.flags().flagsMask())) {
                continue;
            }
            ByteArrayOutputStream member = new ByteArrayOutputStream();
            DataOutputStream fieldOut = new DataOutputStream(member);
            fieldOut.writeInt(field.flags().flagsMask() & FIELD_FLAGS);
            Optional<ConstantValueAttribute> constant = field.findAttribute(Attributes.constantValue());
            fieldOut.writeBoolean(constant.isPresent());
            if (constant.isPresent()) {
                ConstantDesc value = constant.get().constant().constantValue();
                write(fieldOut, value.getClass().getName());
                write(fieldOut, String.valueOf(value));
            }
            element(fieldOut, field);
            members.put("field " + field.fieldName().stringValue() + " " + field.fieldType().stringValue(),
                    member.toByteArray());
        }
        for (MethodModel method : model.methods()) {
            if (method.methodName().equalsString("<clinit>") || !included(method.flags().flagsMask())) {
                continue;
            }
            ByteArrayOutputStream member = new ByteArrayOutputStream();
            DataOutputStream methodOut = new DataOutputStream(member);
            methodOut.writeInt(method.flags().flagsMask() & METHOD_FLAGS);
            List<String> exceptions = method.findAttribute(Attributes.exceptions()).stream()
                    .flatMap(attribute -> attribute.exceptions().stream())
                    .map(ClassEntry::asInternalName)
                    .sorted()
                    .toList();
            methodOut.writeInt(exceptions.size());
            for (String exception : exceptions) {
                write(methodOut, exception);
            }
            Optional<AnnotationDefaultAttribute> defaultValue = method.findAttribute(Attributes.annotationDefault());
            methodOut.writeBoolean(defaultValue.isPresent());
            if (defaultValue.isPresent()) {
                value(methodOut, defaultValue.get().defaultValue());
            }
            List<List<Annotation>> visible = method.findAttribute(Attributes.runtimeVisibleParameterAnnotations())
                    .map(RuntimeVisibleParameterAnnotationsAttribute::parameterAnnotations)
                    .orElse(List.of()),
                    invisible = method.findAttribute(Attributes.runtimeInvisibleParameterAnnotations())
                            .map(RuntimeInvisibleParameterAnnotationsAttribute::parameterAnnotations)
                            .orElse(List.of());
            for (List<List<Annotation>> parameters : List.of(visible, invisible)) {
                methodOut.writeInt(parameters.size());
                for (List<Annotation> annotations : parameters) {
                    annotations(methodOut, annotations);
                }
            }
            element(methodOut, method);
            members.put("method " + method.methodName().stringValue() + method.methodType().stringValue(),
                    member.toByteArray());
        }
        out.writeInt(members.size());
        for (Map.Entry<String, byte[]> member : members.entrySet()) {
            write(out, member.getKey());
            out.writeInt(member.getValue().length);
            out.write(member.getValue());
        }
        out.flush();
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        return new Entry(HexFormat.of().formatHex(digest.digest(bytes.toByteArray())),
                access,
                name,
                outer,
                model.superclass().map(ClassEntry::asInternalName).orElse(NONE),
                interfaces,
                exports);
    }

    private boolean included(int flags) {
        if ((flags & (ClassFile.ACC_SYNTHETIC | ClassFile.ACC_PRIVATE)) != 0) {
            return false;
        }
        return mode == Mode.CLASSPATH || (flags & (ClassFile.ACC_PUBLIC | ClassFile.ACC_PROTECTED)) != 0;
    }

    private static Access access(int flags) {
        if ((flags & ClassFile.ACC_PRIVATE) != 0) {
            return Access.HIDDEN;
        } else if ((flags & (ClassFile.ACC_PUBLIC | ClassFile.ACC_PROTECTED)) != 0) {
            return Access.API;
        } else {
            return Access.PACKAGE;
        }
    }

    private static void element(DataOutputStream out, AttributedElement element) throws IOException {
        write(out, element.findAttribute(Attributes.signature())
                .map(attribute -> attribute.signature().stringValue())
                .orElse(""));
        out.writeBoolean(element.findAttribute(Attributes.deprecated()).isPresent());
        annotations(out, element.findAttribute(Attributes.runtimeVisibleAnnotations())
                .map(RuntimeVisibleAnnotationsAttribute::annotations)
                .orElse(List.of()));
        annotations(out, element.findAttribute(Attributes.runtimeInvisibleAnnotations())
                .map(RuntimeInvisibleAnnotationsAttribute::annotations)
                .orElse(List.of()));
        List<TypeAnnotation> visible = element.findAttribute(Attributes.runtimeVisibleTypeAnnotations())
                .map(RuntimeVisibleTypeAnnotationsAttribute::annotations)
                .orElse(List.of()),
                invisible = element.findAttribute(Attributes.runtimeInvisibleTypeAnnotations())
                        .map(RuntimeInvisibleTypeAnnotationsAttribute::annotations)
                        .orElse(List.of());
        for (List<TypeAnnotation> annotations : List.of(visible, invisible)) {
            out.writeInt(annotations.size());
            for (TypeAnnotation annotation : annotations) {
                out.writeInt(annotation.targetInfo().targetType().targetTypeValue());
                switch (annotation.targetInfo()) {
                    case TypeAnnotation.TypeParameterTarget target -> out.writeInt(target.typeParameterIndex());
                    case TypeAnnotation.SupertypeTarget target -> out.writeInt(target.supertypeIndex());
                    case TypeAnnotation.TypeParameterBoundTarget target -> {
                        out.writeInt(target.typeParameterIndex());
                        out.writeInt(target.boundIndex());
                    }
                    case TypeAnnotation.FormalParameterTarget target -> out.writeInt(target.formalParameterIndex());
                    case TypeAnnotation.ThrowsTarget target -> out.writeInt(target.throwsTargetIndex());
                    default -> {
                    }
                }
                out.writeInt(annotation.targetPath().size());
                for (TypeAnnotation.TypePathComponent component : annotation.targetPath()) {
                    out.writeInt(component.typePathKind().tag());
                    out.writeInt(component.typeArgumentIndex());
                }
                annotation(out, annotation.annotation());
            }
        }
    }

    private static void annotations(DataOutputStream out, List<Annotation> annotations) throws IOException {
        out.writeInt(annotations.size());
        for (Annotation annotation : annotations) {
            annotation(out, annotation);
        }
    }

    private static void annotation(DataOutputStream out, Annotation annotation) throws IOException {
        write(out, annotation.className().stringValue());
        out.writeInt(annotation.elements().size());
        for (AnnotationElement element : annotation.elements()) {
            write(out, element.name().stringValue());
            value(out, element.value());
        }
    }

    private static void value(DataOutputStream out, AnnotationValue value) throws IOException {
        out.writeInt(value.tag());
        switch (value) {
            case AnnotationValue.OfConstant constant -> write(out, String.valueOf(constant.resolvedValue()));
            case AnnotationValue.OfClass type -> write(out, type.className().stringValue());
            case AnnotationValue.OfEnum constant -> {
                write(out, constant.className().stringValue());
                write(out, constant.constantName().stringValue());
            }
            case AnnotationValue.OfAnnotation nested -> annotation(out, nested.annotation());
            case AnnotationValue.OfArray array -> {
                out.writeInt(array.values().size());
                for (AnnotationValue element : array.values()) {
                    value(out, element);
                }
            }
        }
    }

    private static void write(DataOutputStream out, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    private record Entry(String hash,
                         Access access,
                         String name,
                         String outer,
                         String superclass,
                         List<String> interfaces,
                         List<String> exports) {

        private static Entry parse(String line) {
            String[] tokens = line.split(" ");
            return new Entry(tokens[0],
                    Access.valueOf(tokens[1]),
                    tokens[2],
                    tokens[3],
                    tokens[4],
                    tokens[5].equals(NONE) ? List.of() : List.of(tokens[5].split(",")),
                    tokens[6].equals(NONE) ? List.of() : List.of(tokens[6].split(",")));
        }

        private Access access(Map<String, List<Entry>> named, Map<String, Access> effective) {
            Access cached = effective.get(name);
            if (cached != null) {
                return cached;
            }
            List<Entry> enclosing = outer.equals(NONE) ? null : named.get(outer);
            Access resolved = enclosing == null
                    ? access
                    : Collections.max(List.of(access, enclosing.getFirst().access(named, effective)));
            effective.put(name, resolved);
            return resolved;
        }

        @Override
        public String toString() {
            return String.join(" ",
                    hash,
                    access.name(),
                    name,
                    outer,
                    superclass,
                    interfaces.isEmpty() ? NONE : String.join(",", interfaces),
                    exports.isEmpty() ? NONE : String.join(",", exports));
        }
    }
}
