import java.lang.reflect.Method;

public class AnnotationChecker {

  
    public static void checkAnnotations(Class<?> clazz) {
        try {
 
            Object obj = clazz.getDeclaredConstructor().newInstance();
            
            
            Method[] methods = clazz.getDeclaredMethods();
            
            for (Method method : methods) {
                
                if (method.isAnnotationPresent(MyMethodAnnotation.class)) {
                    MyMethodAnnotation anno = method.getAnnotation(MyMethodAnnotation.class);
                    System.out.println("Méthode annotée trouvée : " + method.getName());
                    System.out.println("Description : " + anno.description());
                    
                    
                    try {
                        method.invoke(obj);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                } else {
                    System.out.println("Méthode non annotée : " + method.getName());
                }
            }
        } catch (Exception e) {
            System.err.println("Erreur lors de la vérification des annotations : " + e.getMessage());
        }
    }
}