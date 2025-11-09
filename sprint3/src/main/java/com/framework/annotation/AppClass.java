package com.framework.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.annotation.ElementType;

@Retention(RetentionPolicy.RUNTIME)  // visible à l'exécution
@Target(ElementType.TYPE)             // s'applique aux classes
public @interface AppClass {
    String value() default ""; // optionnel
}
