package build.jenesis;

import module java.base;

record Reproduction(Path first, SequencedSet<String> firstSteps, Path second, SequencedSet<String> secondSteps) {

    private static final String CHECKSUM = "checksum", ARGUMENTS = "arguments.properties", PROPERTIES = ".properties";

    boolean verify(Consumer<String> out, boolean detailed) throws IOException {
        SortedSet<String> steps = new TreeSet<>(firstSteps);
        steps.addAll(secondSteps);
        List<String> causes = new ArrayList<>(), consequences = new ArrayList<>(), single = new ArrayList<>();
        MessageDigest fingerprint;
        try {
            fingerprint = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        for (String step : steps) {
            if (!firstSteps.contains(step) || !secondSteps.contains(step)) {
                single.add(step + " (" + (firstSteps.contains(step) ? "first" : "second") + " build only)");
                continue;
            }
            Path left = first.resolve(step).resolve(CHECKSUM), right = second.resolve(step).resolve(CHECKSUM);
            SortedMap<String, String> firstOutput = digests(left.resolve("output" + PROPERTIES)),
                    secondOutput = digests(right.resolve("output" + PROPERTIES));
            firstOutput.forEach((file, digest) -> fingerprint.update(
                    (step + "/" + file + " " + digest + "\n").getBytes(StandardCharsets.UTF_8)));
            if (firstOutput.equals(secondOutput)) {
                continue;
            }
            if (arguments(left).equals(arguments(right))) {
                SortedSet<String> files = new TreeSet<>(firstOutput.keySet());
                files.addAll(secondOutput.keySet());
                files.removeIf(file -> Objects.equals(firstOutput.get(file), secondOutput.get(file)));
                causes.add(step + ": " + String.join(", ", files)
                        + (Objects.equals(properties(left.resolve("step" + PROPERTIES)).getProperty("serialization"),
                        properties(right.resolve("step" + PROPERTIES)).getProperty("serialization"))
                        ? ""
                        : " - the step itself differs between the builds, as it holds a value that differs"
                        + " between them, such as a path into the target folder"));
            } else {
                consequences.add(step);
            }
        }
        String hash = " - fingerprint SHA-256:" + HexFormat.of().formatHex(fingerprint.digest());
        if (causes.isEmpty() && consequences.isEmpty() && single.isEmpty()) {
            out.accept("The build reproduced: " + steps.size() + " step" + (steps.size() == 1 ? "" : "s")
                    + " wrote the same output into " + second + " as into " + first + hash);
            return true;
        }
        int differing = causes.size() + consequences.size() + single.size();
        StringBuilder message = new StringBuilder("The build did not reproduce: ")
                .append(differing)
                .append(" of ")
                .append(steps.size())
                .append(" step")
                .append(steps.size() == 1 ? "" : "s")
                .append(" wrote a different output into ")
                .append(second)
                .append(" than into ")
                .append(first)
                .append(", which is kept to compare")
                .append(hash);
        if (detailed) {
            if (!causes.isEmpty()) {
                message.append("\nCaused by these steps, whose output differs although they were handed the same input"
                        + " in both builds, in the files named:");
                causes.forEach(cause -> message.append("\n  ").append(cause));
            }
            if (!consequences.isEmpty()) {
                message.append("\nAs a consequence, these steps were handed a different input:");
                consequences.forEach(consequence -> message.append("\n  ").append(consequence));
            }
            if (!single.isEmpty()) {
                message.append("\nThese steps ran in one of the builds only:");
                single.forEach(step -> message.append("\n  ").append(step));
            }
        }
        out.accept(message.toString());
        return false;
    }

    private static SequencedProperties properties(Path file) throws IOException {
        return Files.isRegularFile(file) ? SequencedProperties.ofFiles(file) : new SequencedProperties();
    }

    private static SortedMap<String, String> digests(Path file) throws IOException {
        SortedMap<String, String> digests = new TreeMap<>();
        SequencedProperties properties = properties(file);
        for (String name : properties.stringPropertyNames()) {
            StringJoiner joined = new StringJoiner("/");
            Path.of(name).forEach(element -> joined.add(element.toString()));
            digests.put(joined.toString(), properties.getProperty(name));
        }
        return digests;
    }

    private static SortedMap<String, SortedMap<String, String>> arguments(Path checksum) throws IOException {
        SortedMap<String, SortedMap<String, String>> arguments = new TreeMap<>();
        Path index = checksum.resolve(ARGUMENTS);
        if (Files.isRegularFile(index)) {
            SequencedProperties recorded = SequencedProperties.ofFiles(index);
            for (String key : recorded.stringPropertyNames()) {
                arguments.put(key, digests(checksum.resolve(recorded.getProperty(key))));
            }
        }
        return arguments;
    }
}
