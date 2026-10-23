package build.jenesis;

import module java.base;

public class SequencedProperties extends Properties {

    private final SequencedMap<Object, Object> delegate = new LinkedHashMap<>();

    public static SequencedProperties ofFolders(Iterable<Path> folders, String file) throws IOException {
        SequencedProperties properties = new SequencedProperties();
        for (Path folder : folders) {
            Path candidate = folder.resolve(file);
            if (Files.exists(candidate)) {
                try (Reader reader = Files.newBufferedReader(candidate)) {
                    properties.load(reader);
                }
            }
        }
        return properties;
    }

    public static SequencedProperties ofFiles(Path... files) throws IOException {
        SequencedProperties properties = new SequencedProperties();
        for (Path file : files) {
            try (Reader reader = Files.newBufferedReader(file)) {
                properties.load(reader);
            }
        }
        return properties;
    }

    public static List<String> arguments(String... arguments) throws IOException {
        return Make.expanded(arguments);
    }

    private static String trimmed(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static List<String> splitEntries(String value) {
        if (value == null) {
            return null;
        }
        List<String> entries = new ArrayList<>();
        for (String entry : value.split(",")) {
            String trimmed = entry.trim();
            if (!trimmed.isEmpty()) {
                entries.add(trimmed);
            }
        }
        return entries;
    }

    private static List<String> splitWords(String value) {
        if (value == null) {
            return List.of();
        }
        return List.of(value.split("\\s+"));
    }

    public String value(String key) {
        return trimmed(getProperty(key));
    }

    public String value(String key, String defaultValue) {
        String value = value(key);
        return value == null ? defaultValue : value;
    }

    public boolean flag(String key) {
        return flag(key, false);
    }

    public boolean flag(String key, boolean defaultValue) {
        Boolean value = flagOrNull(key);
        return value == null ? defaultValue : value;
    }

    public Boolean flagOrNull(String key) {
        return Make.parsed(key, getProperty(key));
    }

    public List<String> entries(String key) {
        return splitEntries(value(key));
    }

    public List<String> words(String key) {
        return splitWords(value(key));
    }

    public void store(Path file) throws IOException {
        try (Writer writer = Files.newBufferedWriter(file)) {
            store(writer, null);
        }
    }

    public void storeAtomically(Path file) throws IOException {
        Path temporary = file.resolveSibling(file.getFileName() + "~");
        try (Writer writer = Files.newBufferedWriter(temporary)) {
            store(writer, null);
        }
        try {
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException _) {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @Override
    public void store(Writer writer, String comments) throws IOException {
        super.store(new CommentSuppressingWriter(writer), comments);
    }

    @Override
    public String getProperty(String key) {
        if (delegate.get(key) instanceof String value) {
            return value;
        } else if (defaults != null) {
            return defaults.getProperty(key);
        } else {
            return null;
        }
    }

    @Override
    public int size() {
        return delegate.size();
    }

    @Override
    public boolean isEmpty() {
        return delegate.isEmpty();
    }

    @Override
    public Enumeration<Object> keys() {
        return Collections.enumeration(delegate.keySet());
    }

    @Override
    public Enumeration<Object> elements() {
        return Collections.enumeration(delegate.values());
    }

    @Override
    public boolean contains(Object value) {
        return delegate.containsValue(value);
    }

    @Override
    public boolean containsValue(Object value) {
        return delegate.containsValue(value);
    }

    @Override
    public boolean containsKey(Object key) {
        return delegate.containsKey(key);
    }

    @Override
    public Object get(Object key) {
        return delegate.get(key);
    }

    @Override
    public Object put(Object key, Object value) {
        return delegate.put(key, value);
    }

    @Override
    public Object remove(Object key) {
        return delegate.remove(key);
    }

    @Override
    public void putAll(Map<?, ?> t) {
        delegate.putAll(t);
    }

    @Override
    public void clear() {
        delegate.clear();
    }

    @Override
    public String toString() {
        return delegate.toString();
    }

    @Override
    public Set<Object> keySet() {
        return delegate.keySet();
    }

    @Override
    public Collection<Object> values() {
        return delegate.values();
    }

    @Override
    public Set<Map.Entry<Object, Object>> entrySet() {
        return delegate.entrySet();
    }

    @Override
    public boolean equals(Object o) {
        return delegate.equals(o);
    }

    @Override
    public int hashCode() {
        return delegate.hashCode();
    }

    @Override
    public Object getOrDefault(Object key, Object defaultValue) {
        return delegate.getOrDefault(key, defaultValue);
    }

    @Override
    public void forEach(BiConsumer<? super Object, ? super Object> action) {
        delegate.forEach(action);
    }

    @Override
    public void replaceAll(BiFunction<? super Object, ? super Object, ?> function) {
        delegate.replaceAll(function);
    }

    @Override
    public Object putIfAbsent(Object key, Object value) {
        return delegate.putIfAbsent(key, value);
    }

    @Override
    public boolean remove(Object key, Object value) {
        return delegate.remove(key, value);
    }

    @Override
    public boolean replace(Object key, Object oldValue, Object newValue) {
        return delegate.replace(key, oldValue, newValue);
    }

    @Override
    public Object replace(Object key, Object value) {
        return delegate.replace(key, value);
    }

    @Override
    public Object computeIfAbsent(Object key, Function<? super Object, ?> mappingFunction) {
        return delegate.computeIfAbsent(key, mappingFunction);
    }

    @Override
    public Object computeIfPresent(Object key, BiFunction<? super Object, ? super Object, ?> remappingFunction) {
        return delegate.computeIfPresent(key, remappingFunction);
    }

    @Override
    public Object compute(Object key, BiFunction<? super Object, ? super Object, ?> remappingFunction) {
        return delegate.compute(key, remappingFunction);
    }

    @Override
    public Object merge(Object key, Object value, BiFunction<? super Object, ? super Object, ?> remappingFunction) {
        return delegate.merge(key, value, remappingFunction);
    }

    @Override
    public Object clone() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Set<String> stringPropertyNames() {
        SequencedSet<String> keys = new LinkedHashSet<>();
        delegate.forEach((key, value) -> {
            if (key instanceof String string && value instanceof String) {
                keys.add(string);
            }
        });
        return keys;
    }

    public void forEachProperty(BiConsumer<String, String> action) {
        delegate.forEach((key, value) -> {
            if (key instanceof String property && value instanceof String text) {
                action.accept(property, text);
            }
        });
    }

    private static class CommentSuppressingWriter extends BufferedWriter {

        private boolean suppressLine;

        private CommentSuppressingWriter(Writer out) {
            super(out);
        }

        @Override
        public void write(String str) throws IOException {
            if (suppressLine) {
                return;
            }
            if (str.startsWith("#")) {
                suppressLine = true;
            } else {
                super.write(str);
            }
        }

        @Override
        public void newLine() throws IOException {
            if (suppressLine) {
                suppressLine = false;
            } else {
                super.write('\n');
            }
        }
    }
}
