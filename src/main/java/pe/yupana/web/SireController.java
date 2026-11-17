package pe.yupana.web;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import pe.yupana.carga.ServicioCargas;
import pe.yupana.carga.VistaImportacion;
import pe.yupana.seguridad.Sesion;
import pe.yupana.sire.ServicioSire;
import pe.yupana.sire.VistaTicket;

/** Simulador SIRE: solo para el estudio, siempre sobre la empresa activa. No se conecta con SUNAT. */
@RestController
@RequestMapping("/api/sire")
class SireController {

    private static final int VISTA_PREVIA = 10;

    private final ServicioSire sire;
    private final ServicioCargas cargas;

    SireController(ServicioSire sire, ServicioCargas cargas) {
        this.sire = sire;
        this.cargas = cargas;
    }

    @GetMapping("/periodos")
    List<String> periodos(Sesion sesion) {
        sesion.exigirEstudio();
        return sire.periodos(sesion.empresa());
    }

    @GetMapping("/tickets")
    List<VistaTicket> recientes(Sesion sesion) {
        sesion.exigirEstudio();
        return sire.recientes(sesion.empresa());
    }

    @PostMapping("/propuesta")
    VistaTicket solicitar(@RequestBody Map<String, String> cuerpo, Sesion sesion) {
        sesion.exigirEstudio();
        return sire.solicitar(sesion.empresa(), cuerpo.get("periodo"));
    }

    @GetMapping("/tickets/{id}")
    VistaTicket ticket(@PathVariable long id, Sesion sesion) {
        sesion.exigirEstudio();
        return sire.ticket(sesion.empresa(), id);
    }

    @GetMapping("/tickets/{id}/archivo")
    ResponseEntity<String> archivo(@PathVariable long id, Sesion sesion) {
        sesion.exigirEstudio();
        String contenido = sire.archivo(sesion.empresa(), id);
        return Descargas.csv(sire.ticket(sesion.empresa(), id).archivo(), contenido);
    }

    @PostMapping("/tickets/{id}/importar")
    VistaImportacion importar(@PathVariable long id, Sesion sesion) {
        sesion.exigirEstudio();
        return cargas.detalle(sesion.empresa(), sire.importar(sesion.empresa(), id), VISTA_PREVIA).orElseThrow();
    }

    @PostMapping("/tickets/{id}/aceptar")
    VistaTicket aceptar(@PathVariable long id, Sesion sesion) {
        sesion.exigirEstudio();
        return sire.aceptar(sesion.empresa(), id);
    }
}
