package pe.yupana.web;

import java.io.IOException;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import pe.yupana.config.PropiedadesYupana;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Cabeceras de seguridad en toda respuesta (ISO 27001 A.8.26 requisitos de seguridad de aplicaciones; A.8.23 filtrado web).
 * La consola H2 (solo con el perfil «docente») necesita marcos y scripts propios, por eso se exime.
 */
@Component
public class FiltroCabecerasSeguridad extends OncePerRequestFilter {

    static final String POLITICA_CONTENIDO = "default-src 'self'; script-src 'self'; "
            + "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; font-src https://fonts.gstatic.com; "
            + "img-src 'self' data:; connect-src 'self'; object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'";

    static final String POLITICA_TRANSPORTE = "max-age=31536000; includeSubDomains";

    private final boolean hsts;

    public FiltroCabecerasSeguridad(PropiedadesYupana propiedades) {
        this.hsts = propiedades.hsts();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest peticion, HttpServletResponse respuesta, FilterChain cadena)
            throws ServletException, IOException {
        if (!peticion.getRequestURI().startsWith("/h2-console")) {
            respuesta.setHeader("Content-Security-Policy", POLITICA_CONTENIDO);
            respuesta.setHeader("X-Frame-Options", "DENY");
        }
        respuesta.setHeader("X-Content-Type-Options", "nosniff");
        respuesta.setHeader("Referrer-Policy", "no-referrer");
        respuesta.setHeader("Permissions-Policy", "camera=(), microphone=(), geolocation=()");
        if (hsts) respuesta.setHeader("Strict-Transport-Security", POLITICA_TRANSPORTE);
        if (peticion.getRequestURI().startsWith("/api/")) respuesta.setHeader("Cache-Control", "no-store");
        cadena.doFilter(peticion, respuesta);
    }
}
