package com.framework.annotation;

import java.io.File;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.net.URL;

public class AnnotationScanner {

    public static List<Class<?>> getAnnotatedClasses(String packageName, Class<? extends Annotation> annotation) {
        List<Class<?>> result = new ArrayList<>();

        // Convertir le package en chemin de ressources
        String path = packageName.replace('.', '/');
        try {
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            // Récupérer les ressources dans ce package
            Enumeration<URL> resources = classLoader.getResources(path);
            while (resources.hasMoreElements()) {
                URL resource = resources.nextElement();
                File directory = new File(resource.toURI());
                if (directory.exists()) {
                    for (File file : directory.listFiles()) {
                        if (file.getName().endsWith(".class")) {
                            String className = packageName + "." + file.getName().replace(".class", "");
                            Class<?> clazz = Class.forName(className);
                            if (clazz.isAnnotationPresent(annotation)) {
                                result.add(clazz);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }



    public static Method findMethodByUrl(Class<?> clazz, Class<? extends Annotation> annotation, String url) {
        for (Method method : clazz.getDeclaredMethods()) {
            if (method.isAnnotationPresent(annotation)) {
                try {
                    Annotation ann = method.getAnnotation(annotation);
                    Method valueMethod = annotation.getMethod("url");
                    String value = (String) valueMethod.invoke(ann);
                    if (value.equals(url)) {
                        return method;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        return null;
    }


}
