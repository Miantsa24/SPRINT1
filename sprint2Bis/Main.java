

import annotations.ScanAnnotation;
import org.reflections.Reflections;
import java.util.Set;

public class Main {
    public static void main(String[] args) {
        // Scanner tout le package "com.example"
        Reflections reflections = new Reflections("services");

        // Trouver toutes les classes annotées avec @AutoRun
        Set<Class<?>> annotatedClasses = reflections.getTypesAnnotatedWith(ScanAnnotation.class);

        // Parcourir les classes détectées
        for (Class<?> clazz : annotatedClasses) {
            System.out.println("🚀 Classe détectée : " + clazz.getName());

            try {
                Object instance = clazz.getDeclaredConstructor().newInstance();
                clazz.getMethod("execute").invoke(instance);

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
