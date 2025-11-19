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
    // Map URL -> Controller class
    private final Map<String, Class<?>> controllerMap = new HashMap<>();
    // Map URL -> Method
    private final Map<String, Method> methodMap = new HashMap<>();

    @Override
    public void init() throws ServletException {
        super.init();

        // Scan classes under /WEB-INF/classes/src/ for @Controller classes
        ServletContext ctx = getServletContext();
        String base = "/WEB-INF/classes/src/"; // package 'src'

        Set<String> resources = ctx.getResourcePaths(base);
        if (resources != null) {
            scanResources(base, ctx);
        }

        // expose mapping to other components if needed
        ctx.setAttribute("routeControllers", controllerMap);
        ctx.setAttribute("routeMethods", methodMap);
    }

    private void scanResources(String path, ServletContext ctx) {
        Set<String> children = ctx.getResourcePaths(path);
        if (children == null) return;

        for (String p : children) {
            if (p.endsWith("/")) {
                scanResources(p, ctx);
            } else if (p.endsWith(".class")) {
                try {
                    String prefix = "/WEB-INF/classes/";
                    if (!p.startsWith(prefix)) continue;
                    String classPath = p.substring(prefix.length(), p.length() - 6); // remove .class
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
                } catch (ClassNotFoundException e) {
                    log("Class not found while scanning: " + e.getMessage());
                } catch (Throwable t) {
                    log("Error scanning class " + p + " : " + t);
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

            // 🔹 1. Vérifier si la ressource statique existe
            InputStream res = getServletContext().getResourceAsStream(path);
            if (res != null) {
                OutputStream out = resp.getOutputStream();
                res.transferTo(out);
                res.close();
                return;
            }

            // 🔹 2. Vérifier si l’URL correspond à une méthode annotée
            if (methodMap.containsKey(path)) {
                Class<?> controllerClass = controllerMap.get(path);
                Method method = methodMap.get(path);

                try {
                    // 🔹 3. Créer une instance du contrôleur
                    Object controllerInstance = controllerClass.getDeclaredConstructor().newInstance();

                    // 🔹 4. Vérifier les paramètres attendus par la méthode
                    Object result;
                    if (method.getParameterCount() == 2 &&
                        method.getParameterTypes()[0] == HttpServletRequest.class &&
                        method.getParameterTypes()[1] == HttpServletResponse.class) {
                        
                        // Si la méthode prend (HttpServletRequest, HttpServletResponse)
                        result = method.invoke(controllerInstance, req, resp);

                    } else {
                        // Sinon, invoquer sans paramètres
                        result = method.invoke(controllerInstance);
                    }

                    // 🔹 5. Si la méthode renvoie quelque chose, l’afficher dans la réponse
                    if (result != null) {

                        // Si la méthode retourne ModelView → FORWARD vers JSP
                    if (result instanceof ModelView) {
                        ModelView mv = (ModelView) result;

                        // 🔹 transmettre les données à la JSP
                        for (Map.Entry<String, Object> entry : mv.getData().entrySet()) {
                            req.setAttribute(entry.getKey(), entry.getValue());
                        }

                        // Forward vers la vue
                        RequestDispatcher dispatcher = req.getRequestDispatcher(mv.getView());
                        dispatcher.forward(req, resp);
                        return;
                    }


                        // Sinon → affichage normal (String, int, etc.)
                        resp.getWriter().println(result.toString());
                }


                } catch (Exception e) {
                    // 🔹 6. Gestion des erreurs
                    resp.getWriter().println("<html><body>");
                    resp.getWriter().println("<h3>Erreur lors de l'exécution du contrôleur</h3>");
                    resp.getWriter().println("<pre>" + e.getMessage() + "</pre>");
                    resp.getWriter().println("</body></html>");
                    e.printStackTrace();
                }
                return;
            }

            // 🔹 7. Si aucune méthode correspondante → 404
            resp.getWriter().println("<html><body>");
            resp.getWriter().println("<p>404 - Route non trouvée</p>");
            resp.getWriter().println("<p>URL demandée : " + path + "</p>");
            resp.getWriter().println("</body></html>");
        }

    
}
