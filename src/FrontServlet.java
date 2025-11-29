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

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        resp.setContentType("text/html;charset=UTF-8");

        String uri = req.getRequestURI();
        String path = uri.replaceFirst(req.getContextPath(), "");

        // ===== 1. Ressource statique =====
        InputStream res = getServletContext().getResourceAsStream(path);
        if (res != null) {
            OutputStream out = resp.getOutputStream();
            res.transferTo(out);
            res.close();
            return;
        }

        // ===== 2. URL EXACTE =====
        if (methodMap.containsKey(path)) {
            Class<?> controllerClass = controllerMap.get(path);
            Method method = methodMap.get(path);

            try {
                Object controllerInstance = controllerClass.getDeclaredConstructor().newInstance();
                Object result;

                if (method.getParameterCount() == 2 &&
                    method.getParameterTypes()[0] == HttpServletRequest.class &&
                    method.getParameterTypes()[1] == HttpServletResponse.class) {
                    result = method.invoke(controllerInstance, req, resp);
                } else {
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

        // ===== 3. URL AVEC PARAMÈTRE /tiko/{valeur} =====
        for (String mappedUrl : methodMap.keySet()) {

            if (mappedUrl.contains("{") && mappedUrl.contains("}")) {

                String base = mappedUrl.substring(0, mappedUrl.indexOf("/{"));

                if (path.startsWith(base + "/")) {

                    String valeur = path.substring(base.length() + 1);

                    Class<?> controllerClass = controllerMap.get(mappedUrl);
                    Method method = methodMap.get(mappedUrl);

                    try {
                        Object controllerInstance = controllerClass.getDeclaredConstructor().newInstance();

                        req.setAttribute("param", valeur);

                        Object result;

                        if (method.getParameterCount() == 2 &&
                            method.getParameterTypes()[0] == HttpServletRequest.class &&
                            method.getParameterTypes()[1] == HttpServletResponse.class) {
                            result = method.invoke(controllerInstance, req, resp);
                        } else {
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

        // ===== 4. 404 =====
        resp.getWriter().println("<html><body>");
        resp.getWriter().println("<p>404 - Route non trouvée</p>");
        resp.getWriter().println("<p>URL demandée : " + path + "</p>");
        resp.getWriter().println("</body></html>");
    }
}
