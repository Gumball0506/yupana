package pe.yupana.web;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;

/**
 * Qué sesiones HTTP tiene abiertas cada usuario, para poder cerrarlas (ISO 27001 A.8.5): al cambiar la clave se cierran las
 * demás y al desactivar una empresa se cierran las de su MYPE. Las sesiones viven en memoria del servidor, igual que este
 * registro; con varias instancias haría falta un almacén de sesiones compartido.
 */
@Component
class RegistroDeSesiones implements HttpSessionListener {

    private final Map<String, Set<HttpSession>> porUsuario = new ConcurrentHashMap<>();

    void registrar(String usuario, HttpSession sesion) {
        porUsuario.computeIfAbsent(usuario, u -> ConcurrentHashMap.newKeySet()).add(sesion);
    }

    /** Cierra todas las sesiones del usuario salvo la indicada. */
    void cerrarOtras(String usuario, HttpSession conservar) {
        for (HttpSession sesion : Set.copyOf(porUsuario.getOrDefault(usuario, Set.of()))) {
            if (sesion == conservar) continue;
            cerrar(usuario, sesion);
        }
    }

    void cerrarTodas(String usuario) {
        cerrarOtras(usuario, null);
    }

    private void cerrar(String usuario, HttpSession sesion) {
        Set<HttpSession> sesiones = porUsuario.get(usuario);
        if (sesiones != null) sesiones.remove(sesion);
        try {
            sesion.invalidate();
        } catch (IllegalStateException yaCerrada) {
            // ya estaba cerrada: nada que hacer
        }
    }

    @Override
    public void sessionDestroyed(HttpSessionEvent evento) {
        porUsuario.values().forEach(sesiones -> sesiones.remove(evento.getSession()));
    }
}
