package build.jenesis.step;

import module java.base;
import module java.xml;
import build.jenesis.BuildExecutor;
import build.jenesis.BuildStepContext;
import org.xml.sax.Attributes;

public record Findings(String tool, Path report, int count) {

    public static Findings ofXml(String tool, Path report, String element) throws IOException {
        if (!Files.isRegularFile(report)) {
            return new Findings(tool, report, -1);
        }
        AtomicInteger count = new AtomicInteger();
        return new Findings(tool, report, ProcessBuildStep.parsed(report, new DefaultHandler() {
            @Override
            public void startElement(String uri, String localName, String qualifiedName, Attributes attributes) {
                if (qualifiedName.equals(element)) {
                    count.incrementAndGet();
                }
            }
        }) ? count.get() : -1);
    }

    public boolean acceptable(int code,
                              BuildStepContext context,
                              boolean judged,
                              boolean strict,
                              String setting,
                              Consumer<String> reporting) {
        if (count < 0) {
            return code == 0;
        }
        String found = tool + " found " + count + (count == 1 ? " finding" : " findings");
        if (strict && count > 0 && (code != 0 || !judged)) {
            throw new IllegalStateException(found + ", reported in " + report
                    + ", and fails the build on them as jenesis." + setting
                    + " is true: fix them, or set it to false to print them without failing");
        }
        if (strict && code != 0) {
            return false;
        }
        if (count > 0 && reporting != null) {
            Path step = context.next().getParent();
            String name = step == null ? "" : step.getFileName().toString();
            reporting.accept(found + ", reported in " + (name.endsWith(BuildExecutor.NEXT)
                    ? step.resolveSibling(name.substring(0, name.length() - BuildExecutor.NEXT.length()))
                            .resolve(step.relativize(report))
                    : report));
        }
        return true;
    }
}
