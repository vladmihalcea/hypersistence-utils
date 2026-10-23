package build.jenesis.project;

import module java.base;

public final class TestSelection {

    private final Map<String, Set<String>> dependents;

    private TestSelection(Map<String, Set<String>> dependents) {
        this.dependents = dependents;
    }

    public static TestSelection of(Map<String, Set<String>> references) {
        Map<String, Set<String>> dependents = new HashMap<>();
        for (String name : references.keySet()) {
            dependents.put(name, new HashSet<>());
        }
        for (Map.Entry<String, Set<String>> entry : references.entrySet()) {
            String origin = entry.getKey();
            for (String reference : entry.getValue()) {
                if (!reference.equals(origin) && references.containsKey(reference)) {
                    dependents.get(reference).add(origin);
                }
            }
        }
        return new TestSelection(dependents);
    }

    public static Set<String> references(byte[] bytes) {
        return references(ClassFile.of().parse(bytes));
    }

    public SequencedSet<String> impacted(Collection<String> changed) {
        SequencedSet<String> visited = new LinkedHashSet<>();
        Queue<String> pending = new ArrayDeque<>();
        for (String origin : changed) {
            if (dependents.containsKey(origin) && visited.add(origin)) {
                pending.add(origin);
            }
        }
        while (!pending.isEmpty()) {
            for (String dependent : dependents.getOrDefault(pending.poll(), Set.of())) {
                if (visited.add(dependent)) {
                    pending.add(dependent);
                }
            }
        }
        return visited;
    }

    private static Set<String> references(ClassModel model) {
        Set<String> references = new HashSet<>();
        for (PoolEntry entry : model.constantPool()) {
            if (entry instanceof ClassEntry classEntry) {
                addType(references, classEntry.asSymbol().descriptorString());
            }
        }
        for (FieldModel field : model.fields()) {
            addType(references, field.fieldTypeSymbol().descriptorString());
        }
        for (MethodModel method : model.methods()) {
            MethodTypeDesc descriptor = method.methodTypeSymbol();
            addType(references, descriptor.returnType().descriptorString());
            for (ClassDesc parameter : descriptor.parameterArray()) {
                addType(references, parameter.descriptorString());
            }
        }
        return references;
    }

    private static void addType(Set<String> references, String descriptor) {
        int index = 0;
        while ((index = descriptor.indexOf('L', index)) >= 0) {
            int end = descriptor.indexOf(';', index);
            if (end < 0) {
                break;
            }
            references.add(descriptor.substring(index + 1, end).replace('/', '.'));
            index = end + 1;
        }
    }
}
