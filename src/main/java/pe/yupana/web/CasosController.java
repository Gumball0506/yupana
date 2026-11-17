package pe.yupana.web;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import pe.yupana.casos.ServicioCasos;
import pe.yupana.casos.VistaAviso;
import pe.yupana.conciliacion.VistaRevision;
import pe.yupana.seguridad.Sesion;

/** Tarea 3.1: revisar casos (estudio y MYPE) y simular el aviso del día (solo estudio). */
@RestController
@RequestMapping("/api")
class CasosController {

    private final ServicioCasos casos;

    CasosController(ServicioCasos casos) {
        this.casos = casos;
    }

    /** Cuerpo: {"accion": "confirmar" | "observar", "nota": "opcional"}. */
    @PostMapping("/casos/{id}/estado")
    VistaRevision revisar(@PathVariable long id, @RequestBody Map<String, String> cuerpo, Sesion sesion) {
        return casos.revisar(sesion.empresa(), id, cuerpo.get("accion"), cuerpo.get("nota"), sesion.usuario());
    }

    @PostMapping("/avisos")
    VistaAviso simularAviso(Sesion sesion) {
        sesion.exigirEstudio();
        return casos.simularAviso(sesion.empresa());
    }

    @GetMapping("/avisos")
    List<VistaAviso> avisos(Sesion sesion) {
        return casos.avisos(sesion.empresa());
    }
}
