package src;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class FrontServlet extends HttpServlet {
    private final Map<String, Class<?>> controllerMap = new HashMap<>();
    private final Map<String, Method> methodMap = new HashMap<>();

    @Override
    public void init() throws ServletException {
        super.init();

        ServletContext ctx = getServletContext();
        String base = "/WEB-INF/classes/src/";

        Set<String> resources = ctx.getResourcePaths(base);
        if (resources != null) {
            scanResources(base, ctx);
        }

        ctx.setAttribute("routeControllers", controllerMap);
        ctx.setAttribute("routeMethods", methodMap);
    }

    private void scanResources(String path, ServletContext ctx) {
        Set<String> children = ctx.getResourcePaths(path);
        if (children == null) return;

        for (String p : children) {
            if (p.endsWith("/")) {
                scanResources(p, ctx);
            } 
            else if (p.endsWith(".class")) {
                try {
                    String prefix = "/WEB-INF/classes/";
                    if (!p.startsWith(prefix)) continue;
                    String classPath = p.substring(prefix.length(), p.length() - 6);
                    String className = classPath.replace('/', '.');

                    Class<?> cls = Class.forName(className);

                    if (cls.isAnnotationPresent(Controller.class)) {
                        for (Method m : cls.getDeclaredMethods()) {
                            if (m.isAnnotationPresent(UrlAnnotation.class)) {
                                UrlAnnotation a = m.getAnnotation(UrlAnnotation.class);
                                String url = a.value();
                                if (!url.startsWith("/")) url = "/" + url;
                                controllerMap.put(url, cls);
                                methodMap.put(url, m);
                            }
                        }
                    }
                } catch (Throwable t) {
                    log("Erreur lors du scan : " + t.getMessage());
                }
            }
        }
    }

// … (tout le haut du fichier reste IDENTIQUE)
@Override
protected void service(HttpServletRequest req, HttpServletResponse resp)
        throws ServletException, IOException {

    req.setCharacterEncoding("UTF-8");
    resp.setContentType("text/html;charset=UTF-8");

    String uri = req.getRequestURI();
    String path = uri.replaceFirst(req.getContextPath(), "");

    // =====================================================
    // SPRINT 1 → Ressources statiques
    // =====================================================
    InputStream res = getServletContext().getResourceAsStream(path);
    if (res != null) {
        OutputStream out = resp.getOutputStream();
        res.transferTo(out);
        res.close();
        return;
    }

    // =====================================================
    // SPRINT 2 → URL EXACTE SANS PARAM
    // (comprend Sprint 6 + Sprint 6-bis : Binding automatique)
    // =====================================================
    if (methodMap.containsKey(path)) {

        Class<?> controllerClass = controllerMap.get(path);
        Method method = methodMap.get(path);

        try {
            Object controllerInstance = controllerClass.getDeclaredConstructor().newInstance();

            // ===== Sprint 6 + 6-bis : Binding des paramètres de formulaire =====
            Class<?>[] paramTypes = method.getParameterTypes();
            java.lang.reflect.Parameter[] parameters = method.getParameters();
            Object[] args = new Object[paramTypes.length];

            for (int i = 0; i < paramTypes.length; i++) {
                String paramKey;

                // Sprint 6-bis : annotation @RequestParam
                if (parameters[i].isAnnotationPresent(RequestParam.class)) {
                    RequestParam annotation = parameters[i].getAnnotation(RequestParam.class);
                    paramKey = annotation.value();
                } 
                else {
                    paramKey = parameters[i].getName(); // Sprint 6 : nom du paramètre Java
                }

                String value = req.getParameter(paramKey);

                if (value == null) {
                    args[i] = null;
                    continue;
                }

                // Conversion automatique
                if (paramTypes[i] == String.class) args[i] = value;
                else if (paramTypes[i] == int.class || paramTypes[i] == Integer.class) args[i] = Integer.parseInt(value);
                else if (paramTypes[i] == double.class || paramTypes[i] == Double.class) args[i] = Double.parseDouble(value);
                else args[i] = value;
            }

            Object result;

            // Si la méthode accepte (HttpServletRequest, HttpServletResponse)
            if (method.getParameterCount() == 2 &&
                method.getParameterTypes()[0] == HttpServletRequest.class &&
                method.getParameterTypes()[1] == HttpServletResponse.class) {

                result = method.invoke(controllerInstance, req, resp);
            } 
            else {
                result = method.invoke(controllerInstance, args);
            }

            // Gestion ModelView
            if (result instanceof ModelView mv) {
                for (Map.Entry<String, Object> entry : mv.getData().entrySet()) {
                    req.setAttribute(entry.getKey(), entry.getValue());
                }
                RequestDispatcher dispatcher = req.getRequestDispatcher(mv.getView());
                dispatcher.forward(req, resp);
                return;
            }

            if (result != null) {
                resp.getWriter().println(result.toString());
            }

        } catch (Exception e) {
            resp.getWriter().println("<pre>" + e.getMessage() + "</pre>");
            e.printStackTrace();
        }
        return;
    }


    // ====================================================================================


    // ====================================================================================
    // 🟩 SPRINT 6-TER — URL DYNAMIQUE /route/{id} AVEC BINDING AUTOMATIQUE
    // ====================================================================================
    //
    // ⚡ Objectif :
    //    - lire les variables dynamiques dans l'URL
    //    - les injecter automatiquement dans les paramètres de la méthode
    //    - faire la conversion automatique (int, double, …)
    //
    // Exemple :
    //   @UrlAnnotation("/user/{id}/{age}")
    //   public String test(int id, int age)
    //
    //   URL : /user/12/30  → injection automatique dans la méthode
    // ====================================================================================

for (String mappedUrl : methodMap.keySet()) {

    if (!mappedUrl.contains("{")) continue;

    Map<String, String> extracted = matchPathAndExtractParams(mappedUrl, path);
    if (extracted == null) continue; // pas correspondance

    Class<?> controllerClass = controllerMap.get(mappedUrl);
    Method method = methodMap.get(mappedUrl);

    try {
        Object controllerInstance = controllerClass.getDeclaredConstructor().newInstance();


        // ================================================================
        // 🔥🔥🔥 INSERTION DU NOUVEAU CODE Sprint 6-ter ICI 🔥🔥🔥
        // → Vérifier si la méthode accepte bien toutes les variables dynamiques
        // → Sinon passer au Sprint 3-bis (request parameters)
        // ================================================================

        boolean allDynamicParamsFound = true;

        for (java.lang.reflect.Parameter p : method.getParameters()) {

            // priorité 1 : paramètre dans URL {id}
            if (extracted.containsKey(p.getName())) continue;

            // priorité 2 : paramètre dans la request ?id=12
            if (req.getParameter(p.getName()) != null) continue;

            // ni dans URL, ni dans request → pas possible → ignorer cette méthode
            allDynamicParamsFound = false;
            break;
        }

        if (!allDynamicParamsFound) continue;

        // ================================================================
        // 🔥🔥🔥 FIN DU NOUVEAU CODE
        // ================================================================


        // === Préparer les arguments automatiques ===
        Class<?>[] paramTypes = method.getParameterTypes();
        java.lang.reflect.Parameter[] parameters = method.getParameters();
        Object[] args = new Object[paramTypes.length];

        for (int i = 0; i < parameters.length; i++) {
            String paramName = parameters[i].getName();

            String rawValue = null;

            // ----- PRIORITÉ 1 : URL dynamique -----
            if (extracted.containsKey(paramName)) {
                rawValue = extracted.get(paramName);
            }
            // ----- PRIORITÉ 2 : Request param -----
            else if (req.getParameter(paramName) != null) {
                rawValue = req.getParameter(paramName);
            }
            // ----- PRIORITÉ 3 : null -----
            else {
                args[i] = null;
                continue;
            }

            // Conversion
            if (paramTypes[i] == String.class) args[i] = rawValue;
            else if (paramTypes[i] == int.class || paramTypes[i] == Integer.class)
                args[i] = Integer.parseInt(rawValue);
            else if (paramTypes[i] == double.class || paramTypes[i] == Double.class)
                args[i] = Double.parseDouble(rawValue);
            else
                args[i] = rawValue;
        }

        Object result = method.invoke(controllerInstance, args);

        if (result instanceof ModelView mv) {
            for (Map.Entry<String, Object> entry : mv.getData().entrySet()) {
                req.setAttribute(entry.getKey(), entry.getValue());
            }
            RequestDispatcher dispatcher = req.getRequestDispatcher(mv.getView());
            dispatcher.forward(req, resp);
            return;
        }

        if (result != null) {
            resp.getWriter().println(result.toString());
        }

    } catch (Exception e) {
        resp.getWriter().println("<pre>" + e.getMessage() + "</pre>");
    }
    return;
}

    // ====================================================================================

        // ====================================================================================
    // 🟦 SPRINT 3-BIS — URL DYNAMIQUE /route/{id} (sans injection automatique)
    // ====================================================================================
    //
    // ⚠ Objectif pédagogique :
    //    - reconnaître les routes de type "/user/{id}"
    //    - extraire "id"
    //    - mettre la valeur dans req.setAttribute("param")
    //    - appeler la méthode SANS binding automatique
    // ====================================================================================

    for (String mappedUrl : methodMap.keySet()) {

        if (mappedUrl.contains("{") && mappedUrl.contains("}")) {

            String base = mappedUrl.substring(0, mappedUrl.indexOf("/{"));

            if (path.startsWith(base + "/")) {

                String paramValue = path.substring(base.length() + 1);

                Class<?> controllerClass = controllerMap.get(mappedUrl);
                Method method = methodMap.get(mappedUrl);

                try {
                    Object controllerInstance = controllerClass.getDeclaredConstructor().newInstance();

                    // 🚩 Sprint 3-bis : mettre la valeur dans req
                    req.setAttribute("param", paramValue);

                    Object result;

                    // Si la méthode a (req, resp)
                    if (method.getParameterCount() == 2 &&
                            method.getParameterTypes()[0] == HttpServletRequest.class &&
                            method.getParameterTypes()[1] == HttpServletResponse.class) {

                        result = method.invoke(controllerInstance, req, resp);
                    } 
                    else {
                        result = method.invoke(controllerInstance);
                    }

                    if (result != null) {
                        resp.getWriter().println(result.toString());
                    }

                } catch (Exception e) {
                    resp.getWriter().println("<pre>" + e.getMessage() + "</pre>");
                }
                return;
            }
        }
    }

        // =====================================================
        // 404 — Route non trouvée
        // =====================================================
        resp.getWriter().println("<p>404 - Route non trouvée</p>");
        resp.getWriter().println("<p>URL demandée : " + path + "</p>");
    }

    // ======== METHODE UTILITAIRE POUR 6-TER ==========
    private Map<String, String> matchPathAndExtractParams(String mappedUrl, String actualPath) {

        String[] mappedParts = mappedUrl.split("/");
        String[] actualParts = actualPath.split("/");

        if (mappedParts.length != actualParts.length) return null;

        Map<String, String> params = new HashMap<>();

        for (int i = 0; i < mappedParts.length; i++) {

            if (mappedParts[i].startsWith("{") && mappedParts[i].endsWith("}")) {
                String paramName = mappedParts[i].substring(1, mappedParts[i].length() - 1);
                params.put(paramName, actualParts[i]);
            } 
            else if (!mappedParts[i].equals(actualParts[i])) {
                return null;
            }
        }

        return params;
    }
}
