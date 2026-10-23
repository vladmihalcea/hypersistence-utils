package build.jenesis;

import module java.base;

public record TestTags(SequencedSet<String> alternatives) {

    private static final Pattern TAG = Pattern.compile("-?[A-Za-z0-9_.][A-Za-z0-9_.\\-]*");
    public static final TestTags ALL = new TestTags(Collections.emptyNavigableSet());

    public TestTags {
        alternatives = Collections.unmodifiableSequencedSet(new TreeSet<>(alternatives));
    }

    public static TestTags parse(String expression) {
        if (expression == null || expression.isBlank()) {
            return ALL;
        }
        SequencedSet<String> alternatives = new TreeSet<>();
        for (String alternative : expression.split(",")) {
            if (alternative.isEmpty()) {
                continue;
            }
            SequencedSet<String> literals = new TreeSet<>(Comparator.comparing((String literal) -> literal.startsWith("-"))
                    .thenComparing(literal -> literal.startsWith("-") ? literal.substring(1) : literal));
            for (String literal : alternative.split("\\+", -1)) {
                if (!TAG.matcher(literal).matches()) {
                    throw new IllegalArgumentException("A test tag selection is a comma-separated list of alternatives, each"
                            + " a tag or several joined by + for the tests carrying all of them, where a tag preceded by -"
                            + " selects the tests not carrying it, not " + alternative + " in " + expression);
                }
                literals.add(literal);
            }
            for (String literal : literals) {
                if (literal.startsWith("-") && literals.contains(literal.substring(1))) {
                    throw new IllegalArgumentException("The alternative " + alternative + " in " + expression
                            + " asks for tests that carry " + literal.substring(1) + " and do not, so it selects none");
                }
            }
            alternatives.add(String.join("+", literals));
        }
        return new TestTags(alternatives);
    }

    public static List<String> literals(String alternative) {
        return List.of(alternative.split("\\+"));
    }

    public boolean all() {
        return alternatives.isEmpty();
    }

    public boolean coveredBy(List<TestTags> ran) {
        return all()
                ? ran.stream().anyMatch(TestTags::all)
                : alternatives.stream().allMatch(alternative -> ran.stream().anyMatch(earlier -> earlier.all()
                        || earlier.alternatives.stream().anyMatch(before -> literals(alternative).containsAll(literals(before)))));
    }

    @Override
    public String toString() {
        return String.join(",", alternatives);
    }
}
