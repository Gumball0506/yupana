package pe.yupana.web;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import pe.yupana.acceso.ServicioAcceso;
import pe.yupana.empresas.Empresa;
import pe.yupana.empresas.ServicioEmpresas;
import pe.yupana.seguridad.Sesion;

@RestController
@RequestMapping("/api")
class AccesoController {

    /** Quién soy y qué empresa tengo activa (null si el estudio aún no eligió). */
    record Yo(String usuario, String rol, Empresa empresa) {}

    private final ServicioAcceso acceso;
    private final ServicioEmpresas empresas;
    private final RegistroDeSesiones sesiones;

    AccesoController(ServicioAcceso acceso, ServicioEmpresas empresas, RegistroDeSesiones sesiones) {
        this.acceso = acceso;
        this.empresas = empresas;
        this.sesiones = sesiones;
    }

    @PostMapping("/login")
    Yo ingresar(@RequestBody Map<String, String> cuerpo, HttpServletRequest peticion) {
        Sesion sesion = acceso.ingresar(cuerpo.get("usuario"), cuerpo.get("clave"), peticion.getRemoteAddr());
        HttpSession http = peticion.getSession(true);
        peticion.changeSessionId(); // nuevo identificador tras ingresar: evita la fijación de sesión
        http.setAttribute(ConfiguracionWeb.ATRIBUTO, sesion);
        sesiones.registrar(sesion.usuario(), http);
        return yo(sesion);
    }

    /** Cambia la clave del usuario que tiene la sesión y cierra todas sus demás sesiones (la actual sigue abierta). */
    @PostMapping("/clave")
    ResponseEntity<Void> cambiarClave(@RequestBody Map<String, String> cuerpo, Sesion sesion, HttpServletRequest peticion) {
        acceso.cambiarClave(sesion, cuerpo.get("actual"), cuerpo.get("nueva"), peticion.getRemoteAddr());
        sesiones.cerrarOtras(sesion.usuario(), peticion.getSession(false));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/logout")
    ResponseEntity<Void> salir(HttpSession http) {
        http.invalidate();
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/yo")
    Yo yo(Sesion sesion) {
        Empresa empresa = sesion.empresaId() == null ? null : empresas.buscar(sesion.empresaId()).orElse(null);
        return new Yo(sesion.usuario(), sesion.rol().name(), empresa);
    }
}
