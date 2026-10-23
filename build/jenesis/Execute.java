package build.jenesis;

import module java.base;

public final class Execute {

    public static void main(String... arguments) throws Exception {
        SequencedMap<String, String> named = new LinkedHashMap<>();
        String[] remaining = Make.partitioned(arguments, named);
        List<String> options = Make.options(named);
        Map<String, String> ambient = Make.ambient(named);
        Make configured = new Make("build.jenesis.Execution", ambient),
                make = configured.mode() == Make.Mode.DAEMON ? configured.mode(Make.Mode.COMPILED) : configured;
        Integer code = Make.relaunched(Execute.class, ambient, options, remaining);
        System.exit(code == null ? make.run(remaining) : code);
    }
}
