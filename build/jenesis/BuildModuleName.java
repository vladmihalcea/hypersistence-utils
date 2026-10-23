package build.jenesis;

import module java.base;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface BuildModuleName {

    String value();
}
