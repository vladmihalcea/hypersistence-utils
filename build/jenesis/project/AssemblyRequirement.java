package build.jenesis.project;

import module java.base;

public final class AssemblyRequirement {

    private final SequencedSet<String> after, upstream, tested;

    private AssemblyRequirement(SequencedSet<String> after, SequencedSet<String> upstream, SequencedSet<String> tested) {
        this.after = after;
        this.upstream = upstream;
        this.tested = tested;
    }

    public static AssemblyRequirement of() {
        return new AssemblyRequirement(Collections.emptyNavigableSet(),
                Collections.emptyNavigableSet(),
                Collections.emptyNavigableSet());
    }

    public AssemblyRequirement after(String phase) {
        return new AssemblyRequirement(adding(after, Set.of(phase)), upstream, tested);
    }

    public AssemblyRequirement upstream(String phase) {
        return new AssemblyRequirement(after, adding(upstream, Set.of(phase)), tested);
    }

    public AssemblyRequirement tested(String phase) {
        return new AssemblyRequirement(after, upstream, adding(tested, Set.of(phase)));
    }

    AssemblyRequirement and(AssemblyRequirement requirement) {
        return new AssemblyRequirement(adding(after, requirement.after),
                adding(upstream, requirement.upstream),
                adding(tested, requirement.tested));
    }

    public SequencedSet<String> after() {
        return after;
    }

    public SequencedSet<String> upstream() {
        return upstream;
    }

    public SequencedSet<String> tested() {
        return tested;
    }

    private static SequencedSet<String> adding(SequencedSet<String> phases, Collection<String> named) {
        SequencedSet<String> added = new LinkedHashSet<>(phases);
        added.addAll(named);
        return Collections.unmodifiableSequencedSet(added);
    }
}
