package annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.annotation.ElementType;

// L'annotation s'applique sur une classe
@Target(ElementType.TYPE)
// Elle est visible à l'exécution
@Retention(RetentionPolicy.RUNTIME)
public @interface ScanAnnotation {
    
}
