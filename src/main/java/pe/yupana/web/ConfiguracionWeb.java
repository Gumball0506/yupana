package pe.yupana.web;

import java.util.List;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import pe.yupana.config.PropiedadesYupana;
import pe.yupana.seguridad.DemasiadasPeticiones;
import pe.yupana.seguridad.LimitadorDeTasa;
import pe.yupana.seguridad.Sesion;
import pe.yupana.seguridad.SinSesion;

/**
 * Control de acceso en dos capas (ISO 27001 A.8.3): un interceptor que exige sesión en toda la API (salvo el ingreso y
 * los eventos anónimos) y un resolutor que entrega la {@link Sesion} a cada controlador que la pida como parámetro.
 */
@Configuration
class ConfiguracionWeb implements WebMvcConfigurer {

    /** Atributo de la sesión HTTP donde vive la {@link Sesion}. */
    static final String ATRIBUTO = "yupana.sesion";

    static Sesion leer(HttpSession http) {
        Object sesion = http == null ? null : http.getAttribute(ATRIBUTO);
        if (sesion instanceof Sesion s) return s;
        throw new SinSesion("Inicia sesión para continuar.");
    }

    private final LimitadorDeTasa limitador;
    private final PropiedadesYupana propiedades;

    ConfiguracionWeb(LimitadorDeTasa limitador, PropiedadesYupana propiedades) {
        this.limitador = limitador;
        this.propiedades = propiedades;
    }

    /** Peticiones por minuto permitidas desde una IP según el grupo de rutas; 0 = sin límite. */
    private int maximoPara(String ruta) {
        if (ruta.equals("/api/login") || ruta.equals("/api/clave")) return propiedades.limite().loginPorMinuto();
        if (ruta.equals("/api/cargas")) return propiedades.limite().cargaPorMinuto();
        return 0;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registro) {
        // Primero el límite por IP (también protege el ingreso, que no tiene sesión); luego el control de sesión.
        registro.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest peticion, HttpServletResponse respuesta, Object controlador) {
                if (!"POST".equals(peticion.getMethod())) return true;
                String ruta = peticion.getRequestURI();
                int maximo = maximoPara(ruta);
                if (maximo == 0) return true;
                long espera = limitador.intentar(peticion.getRemoteAddr() + "|" + ruta, maximo);
                if (espera > 0) throw new DemasiadasPeticiones(espera);
                return true;
            }
        }).addPathPatterns("/api/login", "/api/clave", "/api/cargas");

        registro.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest peticion, HttpServletResponse respuesta, Object controlador) {
                leer(peticion.getSession(false));
                return true;
            }
        }).addPathPatterns("/api/**").excludePathPatterns("/api/login", "/api/eventos");
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolutores) {
        resolutores.add(new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter parametro) {
                return parametro.getParameterType() == Sesion.class;
            }

            @Override
            public Object resolveArgument(MethodParameter parametro, ModelAndViewContainer modelo, NativeWebRequest peticion,
                                          WebDataBinderFactory fabrica) {
                return leer(peticion.getNativeRequest(HttpServletRequest.class).getSession(false));
            }
        });
    }
}
