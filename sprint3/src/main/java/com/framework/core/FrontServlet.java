package com.framework.core;


import java.io.*;
import java.lang.reflect.Method;
import java.util.List;

import com.framework.annotation.AnnotationScanner;
import com.framework.annotation.AppClass;
import com.framework.annotation.MonAnnotation;

import javax.servlet.*;
import javax.servlet.annotation.*;
import javax.servlet.http.*;

@WebServlet("/")
public class FrontServlet extends HttpServlet {
    
    RequestDispatcher defaultDispatcher;

    @Override
    public void init() {
        defaultDispatcher = getServletContext().getNamedDispatcher("default");
    }

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        String path = req.getRequestURI().substring(req.getContextPath().length());
        
        boolean resourceExists = getServletContext().getResource(path) != null;

        if (resourceExists) {
            defaultServe(req, res);
        } else {
            customServe(req, res);
        }
    }


    private void customServe(HttpServletRequest req, HttpServletResponse res) throws IOException {

        res.setContentType("text/html;charset=UTF-8");
        res.setCharacterEncoding("UTF-8");
        PrintWriter out = res.getWriter();

        String url = req.getPathInfo();
        if (url == null) {
            url = req.getRequestURI().substring(req.getContextPath().length());
        }

        boolean found = false;

        // 🔍 Scanner les classes annotées @AppClass dans ton package test
        List<Class<?>> annotatedClasses = AnnotationScanner.getAnnotatedClasses("com.test", AppClass.class);

        for (Class<?> clazz : annotatedClasses) {
            Method method = AnnotationScanner.findMethodByUrl(clazz, MonAnnotation.class, url);
            if (method != null) {
                try {
                    Object instance = clazz.getDeclaredConstructor().newInstance();
                    Object result = method.invoke(instance);

                    out.println("<html><body>");
                    out.println("<h3>Classe : " + clazz.getSimpleName() + "</h3>");
                    out.println("<h4>Methode : " + method.getName() + "</h4>");
                    out.println("<p>Resultat : " + (result != null ? result : "(aucun retour)") + "</p>");
                    out.println("</body></html>");
                    found = true;
                    break;
                } catch (Exception e) {
                    e.printStackTrace(out);
                }
            }
        }

        if (!found) {
            out.println("<html><body>");
            out.println("<p>Aucune methode ni classe associee a cette URL : " + url + "</p>");
            out.println("</body></html>");
        }
    }



    private void defaultServe(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        defaultDispatcher.forward(req, res);
    }
}