package src;

import jakarta.servlet.*;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.ArrayList;
import java.util.List;


@MultipartConfig
public class FrontServlet extends HttpServlet {
    private final Map<String, Class<?>> controllerMap = new HashMap<>();
    private final Map<String, Method> methodMap = new HashMap<>();
    private final Map<String, Method> getMap = new HashMap<>();
    private final Map<String, Method> postMap = new HashMap<>();
    private final Map<String, Method> dynamicGetMap = new HashMap<>();
    private final Map<String, Method> dynamicPostMap = new HashMap<>();

    // ======== SPRINT 10 : Limite taille fichiers multiples ========
    private static final int MAX_FILES_PER_INPUT = 10; // Limite configurable
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10 MB par fichier


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

                                // Sprint 6-ter : si URL dynamique
                                if (url.contains("{")) {
                                    dynamicGetMap.put(url, m);
                                    dynamicPostMap.put(url, m);
                                }
                            }

                            if (m.isAnnotationPresent(GetMapping.class)) {
                                GetMapping g = m.getAnnotation(GetMapping.class);
                                String url = g.value();
                                if (!url.startsWith("/")) url = "/" + url;
                                getMap.put(url, m);
                                controllerMap.put(url + "_GET", cls);

                                // Sprint 6-ter : si URL dynamique
                                if (url.contains("{")) {
                                    dynamicGetMap.put(url, m);
                                }
                            }

                            if (m.isAnnotationPresent(PostMapping.class)) {
                                PostMapping pm = m.getAnnotation(PostMapping.class);
                                String url = pm.value();
                                if (!url.startsWith("/")) url = "/" + url;
                                postMap.put(url, m);
                                controllerMap.put(url + "_POST", cls);

                                // Sprint 6-ter : si URL dynamique
                                if (url.contains("{")) {
                                    dynamicPostMap.put(url, m);
                                }
                            }


                        }
                    }
                    
                } catch (Throwable t) {
                    log("Erreur lors du scan : " + t.getMessage());
                }
            }
        }
    }

    private String toJson(Object obj) {
    if (obj == null) return "null";

    if (obj instanceof String s)
        return "\"" + s.replace("\"", "\\\"") + "\"";

    if (obj instanceof Number || obj instanceof Boolean)
        return obj.toString();

    if (obj instanceof java.util.Map<?,?> map) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (var e : map.entrySet()) {
            if (!first) sb.append(",");
            sb.append("\"").append(e.getKey()).append("\":");
            sb.append(toJson(e.getValue()));
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }

    if (obj instanceof java.util.List<?> list) {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (var item : list) {
            if (!first) sb.append(",");
            sb.append(toJson(item));
            first = false;
        }
        sb.append("]");
        return sb.toString();
    }

    // Objet POJO → sérialisation par réflexion
    StringBuilder sb = new StringBuilder("{");
    var fields = obj.getClass().getDeclaredFields();
    boolean first = true;

    for (var f : fields) {
        try {
            f.setAccessible(true);
            Object value = f.get(obj);

            if (!first) sb.append(",");

            sb.append("\"").append(f.getName()).append("\":");
            sb.append(toJson(value));

            first = false;

        } catch (Exception ignored) {}
    }

    sb.append("}");
    return sb.toString();
}


    // ========================================================================
    // SPRINT 10 : DÉTECTION DU TYPE GÉNÉRIQUE DU MAP
    // ========================================================================
    /**
     * Détermine si un paramètre Map est destiné aux fichiers ou aux paramètres
     * 
     * @return "files" si Map<String, byte[]> ou Map<String, byte[][]>
     *         "params" si Map<String, Object>
     *         null sinon
     */
    private String detectMapType(java.lang.reflect.Parameter param) {
        Type genericType = param.getParameterizedType();
        
        if (!(genericType instanceof ParameterizedType)) {
            return null; // Map raw, on ignore
        }

        ParameterizedType paramType = (ParameterizedType) genericType;
        Type[] typeArgs = paramType.getActualTypeArguments();

        if (typeArgs.length != 2) return null;
        
        // Premier type doit être String
        if (!typeArgs[0].equals(String.class)) return null;

        Type valueType = typeArgs[1];

        // Map<String, byte[]> → fichiers simples
        if (valueType.equals(byte[].class)) {
            return "files";
        }

        // Map<String, byte[][]> → fichiers multiples
        if (valueType.equals(byte[][].class)) {
            return "files_multi";
        }

        // Map<String, Object> → paramètres
        if (valueType.equals(Object.class)) {
            return "params";
        }

        return null;
    }


    // ========================================================================
    // SPRINT 10 : EXTRACTION DES FICHIERS ET PARAMÈTRES MULTIPART
    // ========================================================================
    /**
     * Parse une requête multipart et sépare paramètres texte et fichiers
     */
    private MultipartData parseMultipartRequest(HttpServletRequest req) throws Exception {
        Map<String, Object> params = new HashMap<>();
        Map<String, byte[]> files = new HashMap<>();
        Map<String, List<byte[]>> filesMulti = new HashMap<>();

        for (Part part : req.getParts()) {
            String fieldName = part.getName();
            String fileName = part.getSubmittedFileName();

            if (fileName != null && !fileName.isEmpty()) {
                // C'EST UN FICHIER
                
                // Vérification taille
                if (part.getSize() > MAX_FILE_SIZE) {
                    log("Fichier " + fileName + " trop volumineux, ignoré");
                    continue;
                }

                byte[] fileContent = part.getInputStream().readAllBytes();

                // Stockage simple (écrasement)
                files.put(fieldName, fileContent);

                // Stockage multiple
                filesMulti.putIfAbsent(fieldName, new ArrayList<>());
                List<byte[]> fileList = filesMulti.get(fieldName);
                
                if (fileList.size() < MAX_FILES_PER_INPUT) {
                    fileList.add(fileContent);
                } else {
                    log("Limite de " + MAX_FILES_PER_INPUT + " fichiers atteinte pour " + fieldName);
                }

            } else {
                // C'EST UN CHAMP TEXTE
                String value = new String(part.getInputStream().readAllBytes(), "UTF-8");
                params.put(fieldName, value);
            }
        }

        return new MultipartData(params, files, filesMulti);
    }

    /**
     * Classe interne pour encapsuler les données multipart
     */
    private static class MultipartData {
        final Map<String, Object> params;
        final Map<String, byte[]> files;
        final Map<String, List<byte[]>> filesMulti;

        MultipartData(Map<String, Object> params, Map<String, byte[]> files, Map<String, List<byte[]>> filesMulti) {
            this.params = params;
            this.files = files;
            this.filesMulti = filesMulti;
        }
    }


    private void invokeMethod(Class<?> controllerClass, Method method,
                          HttpServletRequest req, HttpServletResponse resp)
        throws ServletException, IOException {

    try {
        Object controller = controllerClass.getDeclaredConstructor().newInstance();

        // ========================================================================
        // SPRINT 10 : DÉTECTION MULTIPART
        // ========================================================================
        boolean isMultipart = false;
        MultipartData multipartData = null;

        String contentType = req.getContentType();
        if (contentType != null && contentType.toLowerCase().startsWith("multipart/form-data")) {
            isMultipart = true;
            try {
                multipartData = parseMultipartRequest(req);
            } catch (Exception e) {
                log("Erreur parsing multipart: " + e.getMessage());
                resp.getWriter().println("<pre>Erreur upload: " + e.getMessage() + "</pre>");
                return;
            }
        }

        // binding Sprint 6 + 6bis + 8 + 8bis + 10
        Class<?>[] paramTypes = method.getParameterTypes();
        java.lang.reflect.Parameter[] params = method.getParameters();
        Object[] args = new Object[paramTypes.length];

    for (int i = 0; i < params.length; i++) {

        // ========================================================================
        // SPRINT 10 : INJECTION MAP SELON TYPE GÉNÉRIQUE
        // ========================================================================
        if (paramTypes[i] == Map.class) {
            String mapType = detectMapType(params[i]);

            if (mapType == null) {
                // Map raw ou type inconnu, comportement par défaut (Sprint 8)
                if (!isMultipart) {
                    Map<String, Object> allParams = new HashMap<>();
                    Map<String, String[]> raw = req.getParameterMap();
                    for (String key : raw.keySet()) {
                        String[] values = raw.get(key);
                        if (values == null) {
                            allParams.put(key, null);
                        } else if (values.length == 1) {
                            allParams.put(key, values[0]);
                        } else {
                            allParams.put(key, values);
                        }
                    }
                    args[i] = allParams;
                } else {
                    args[i] = multipartData.params;
                }
                continue;
            }

            // Map<String, Object> → paramètres texte
            if (mapType.equals("params")) {
                if (isMultipart) {
                    args[i] = multipartData.params;
                } else {
                    // Mode normal (non-multipart)
                    Map<String, Object> allParams = new HashMap<>();
                    Map<String, String[]> raw = req.getParameterMap();
                    for (String key : raw.keySet()) {
                        String[] values = raw.get(key);
                        if (values == null) {
                            allParams.put(key, null);
                        } else if (values.length == 1) {
                            allParams.put(key, values[0]);
                        } else {
                            allParams.put(key, values);
                        }
                    }
                    args[i] = allParams;
                }
                continue;
            }

            // Map<String, byte[]> → fichiers (dernier gagne)
            if (mapType.equals("files")) {
                if (isMultipart) {
                    args[i] = multipartData.files;
                } else {
                    args[i] = new HashMap<String, byte[]>(); // Map vide si pas multipart
                }
                continue;
            }

            // Map<String, byte[][]> → fichiers multiples
            if (mapType.equals("files_multi")) {
                if (isMultipart) {
                    // Convertir List<byte[]> en byte[][]
                    Map<String, byte[][]> filesArray = new HashMap<>();
                    for (Map.Entry<String, List<byte[]>> entry : multipartData.filesMulti.entrySet()) {
                        List<byte[]> fileList = entry.getValue();
                        byte[][] arrayOfArrays = fileList.toArray(new byte[0][]);
                        filesArray.put(entry.getKey(), arrayOfArrays);
                    }
                    args[i] = filesArray;
                } else {
                    args[i] = new HashMap<String, byte[][]>(); // Map vide si pas multipart
                }
                continue;
            }
        }


        // === Sprint 8 bis : objets complexes ===
        if (!paramTypes[i].isPrimitive()
            && paramTypes[i] != String.class
            && paramTypes[i] != Map.class
            && !paramTypes[i].isArray()
            && !paramTypes[i].getName().startsWith("java.")) {

            // nom du paramètre = préfixe utilisé dans le formulaire
            String prefix = params[i].getName();

            args[i] = bindObject(paramTypes[i], prefix, req, isMultipart, multipartData);
            continue;
        }


        String key;

        if (params[i].isAnnotationPresent(RequestParam.class)) {
            key = params[i].getAnnotation(RequestParam.class).value();
        } else {
            key = params[i].getName();
        }

        String value;
        
        // En mode multipart, récupérer depuis multipartData.params
        if (isMultipart && multipartData != null && multipartData.params.containsKey(key)) {
            Object obj = multipartData.params.get(key);
            value = (obj != null) ? obj.toString() : null;
        } else {
            value = req.getParameter(key);
        }

        if (value == null) {
            args[i] = null;
            continue;
        }

        if (paramTypes[i] == String.class) args[i] = value;
        else if (paramTypes[i] == int.class || paramTypes[i] == Integer.class) args[i] = Integer.parseInt(value);
        else if (paramTypes[i] == double.class || paramTypes[i] == Double.class) args[i] = Double.parseDouble(value);
        else args[i] = value;
    }


        Object result = method.invoke(controller, args);

                // ======== SPRINT 9 : Gestion JSON ========
        if (method.isAnnotationPresent(Json.class)) {
            resp.setContentType("application/json;charset=UTF-8");

            Object jsonData;

            // Si c'est un ModelView → retourner seulement les données
            if (result instanceof ModelView mv) {
                jsonData = mv.getData();
            } else {
                jsonData = result; // Objet simple
            }

            String json = toJson(jsonData);

            resp.getWriter().write("""
                {
                    "status": "success",
                    "code": 200,
                    "data": """ + json + """
                }
            """);
            return; // IMPORTANT : ne pas continuer vers JSP
        }


        if (result instanceof ModelView mv) {
            for (var e : mv.getData().entrySet()) {
                req.setAttribute(e.getKey(), e.getValue());
            }
            RequestDispatcher dispatcher = req.getRequestDispatcher(mv.getView());
            dispatcher.forward(req, resp);
            return;
        }

        if (result != null) {
            resp.getWriter().println(result.toString());
        }

    } catch (Exception e) {
        resp.getWriter().println("<pre>" + e + "</pre>");
    }
}

private Object convertValue(Class<?> type, String raw) {
    if (raw == null) return null;

    try {
        if (type == String.class) return raw;

        if (type == int.class || type == Integer.class) return Integer.parseInt(raw);

        if (type == double.class || type == Double.class) return Double.parseDouble(raw);

        if (type == long.class || type == Long.class) return Long.parseLong(raw);

        if (type == float.class || type == Float.class) return Float.parseFloat(raw);

        if (type == boolean.class || type == Boolean.class)
            return Boolean.parseBoolean(raw);

        // ======== JAVA TIME API ========
        if (type == java.time.LocalDate.class)
            return java.time.LocalDate.parse(raw);

        if (type == java.time.LocalDateTime.class)
            return java.time.LocalDateTime.parse(raw);

        if (type == java.time.LocalTime.class)
            return java.time.LocalTime.parse(raw);

        // ======== LEGACY DATE ========
        if (type == java.util.Date.class)
            return java.sql.Date.valueOf(raw);

        if (type == java.sql.Date.class)
            return java.sql.Date.valueOf(raw);

        if (type == java.sql.Timestamp.class)
            return java.sql.Timestamp.valueOf(raw);

        // ======== BigDecimal ========
        if (type == java.math.BigDecimal.class)
            return new java.math.BigDecimal(raw);

    } catch (Exception e) {
        // tu peux log l'erreur
        return null;
    }

    // fallback
    return raw;
}



private void setFieldValue(Object target, String path, String value)
        throws Exception {

    String[] parts = path.split("\\.", 2); // split only first dot
    String fieldName = parts[0];

    java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
    field.setAccessible(true);

    // cas 1 → champ simple (nom, age,…)
    if (parts.length == 1) {
        field.set(target, convertValue(field.getType(), value));
        return;
    }

    // cas 2 → champ imbriqué (adresse.rue)
    Object child = field.get(target);

    if (child == null) {
        child = field.getType().getDeclaredConstructor().newInstance();
        field.set(target, child);
    }

    setFieldValue(child, parts[1], value);
}


private Object bindObject(Class<?> type, String prefix, HttpServletRequest req,
                         boolean isMultipart, MultipartData multipartData)
        throws Exception {

    Object instance = type.getDeclaredConstructor().newInstance();

    Map<String, String[]> params;
    
    if (isMultipart && multipartData != null) {
        // Convertir Map<String, Object> en Map<String, String[]>
        params = new HashMap<>();
        for (Map.Entry<String, Object> entry : multipartData.params.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String) {
                params.put(entry.getKey(), new String[]{(String) value});
            } else if (value instanceof String[]) {
                params.put(entry.getKey(), (String[]) value);
            }
        }
    } else {
        params = req.getParameterMap();
    }

    for (String fullKey : params.keySet()) {

        if (!fullKey.startsWith(prefix + ".")) continue;

        String fieldPath = fullKey.substring((prefix + ".").length());
        setFieldValue(instance, fieldPath, params.get(fullKey)[0]);
    }

    return instance;
}

// Surcharge pour compatibilité avec anciens appels
private Object bindObject(Class<?> type, String prefix, HttpServletRequest req)
        throws Exception {
    return bindObject(type, prefix, req, false, null);
}



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

    String httpMethod = req.getMethod(); // GET ou POST

// ==================================================================================
// 🟦 SPRING 7 — PRIORITÉ 1 : GET / POST spécifiques
// ==================================================================================
if (httpMethod.equals("GET") && getMap.containsKey(path)) {

    Class<?> controllerClass = controllerMap.get(path + "_GET");
    Method method = getMap.get(path);

    invokeMethod(controllerClass, method, req, resp);
    return;
}


if (httpMethod.equals("POST") && postMap.containsKey(path)) {

    Class<?> controllerClass = controllerMap.get(path + "_POST");
    Method method = postMap.get(path);

    invokeMethod(controllerClass, method, req, resp);
    return;
}



    // =====================================================
    // SPRINT 2 → URL EXACTE SANS PARAM
    // (comprend Sprint 6 + Sprint 6-bis : Binding automatique)
    // =====================================================
    if (methodMap.containsKey(path)) {

    Class<?> controllerClass = controllerMap.get(path);
    Method method = methodMap.get(path);

    invokeMethod(controllerClass, method, req, resp);
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
// Choisir la map dynamique selon la méthode HTTP
Map<String, Method> dynamicMap = null;
if (httpMethod.equals("GET")) dynamicMap = dynamicGetMap;
else if (httpMethod.equals("POST")) dynamicMap = dynamicPostMap;

if (dynamicMap != null) {
    for (String mappedUrl : dynamicMap.keySet()) {

        Map<String, String> extracted = matchPathAndExtractParams(mappedUrl, path);
        if (extracted == null) continue;

        Method method = dynamicMap.get(mappedUrl);

        // Obtenir le controller correspondant
       // Obtenir le controller correspondant
// Obtenir le controller correspondant pour dynamic URL
Class<?> controllerClass = null;

// Vérifier GetMapping / PostMapping spécifique
if (httpMethod.equals("GET") && controllerMap.containsKey(mappedUrl + "_GET"))
    controllerClass = controllerMap.get(mappedUrl + "_GET");
else if (httpMethod.equals("POST") && controllerMap.containsKey(mappedUrl + "_POST"))
    controllerClass = controllerMap.get(mappedUrl + "_POST");

// Sinon fallback sur UrlAnnotation
if (controllerClass == null)
    controllerClass = controllerMap.get(mappedUrl);

// Si toujours null, passer au suivant (évite NullPointer)
if (controllerClass == null) continue;



        try {
            Object controllerInstance = controllerClass.getDeclaredConstructor().newInstance();

            // Préparer les arguments automatiques (Sprint 6-ter)
            Class<?>[] paramTypes = method.getParameterTypes();
            java.lang.reflect.Parameter[] parameters = method.getParameters();
            Object[] args = new Object[paramTypes.length];

            boolean allDynamicParamsFound = true;
            for (int i = 0; i < parameters.length; i++) {
                String paramName = parameters[i].getName();
                String rawValue = null;

                if (extracted.containsKey(paramName)) rawValue = extracted.get(paramName);
                else if (req.getParameter(paramName) != null) rawValue = req.getParameter(paramName);
                else {
                    allDynamicParamsFound = false;
                    break;
                }

                args[i] = convertValue(paramTypes[i], rawValue);

            }

            if (!allDynamicParamsFound) continue;

            // Appeler la méthode
            Object result = method.invoke(controllerInstance, args);

            if (result instanceof ModelView mv) {
                for (Map.Entry<String, Object> entry : mv.getData().entrySet()) {
                    req.setAttribute(entry.getKey(), entry.getValue());
                }
                RequestDispatcher dispatcher = req.getRequestDispatcher(mv.getView());
                dispatcher.forward(req, resp);
                return;
            }

            if (result != null) resp.getWriter().println(result.toString());

        } catch (Exception e) {
            resp.getWriter().println("<pre>" + e.getMessage() + "</pre>");
        }
        return;
    }
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