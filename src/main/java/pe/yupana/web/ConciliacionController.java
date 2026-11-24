package pe.yupana.web;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import pe.yupana.conciliacion.ServicioConciliacion;
import pe.yupana.conciliacion.VistaConciliacion;
import pe.yupana.carga.PeriodoCarga;
import pe.yupana.seguridad.Sesion;

@RestController
@RequestMapping("/api")
class ConciliacionController {

    private final ServicioConciliacion conciliacion;

    private final boolean periodoObligatorio;

    ConciliacionController(ServicioConciliacion conciliacion,
            @Value("${yupana.periodo-obligatorio:false}") boolean periodoObligatorio) {
        this.periodoObligatorio = periodoObligatorio;
        this.conciliacion = conciliacion;
    }

    @PostMapping("/conciliar")
    VistaConciliacion conciliar(@RequestParam(required = false) String periodo, Sesion sesion) {
        sesion.exigirEstudio();
        PeriodoCarga.exigirSiCorresponde(periodo, periodoObligatorio);
        return conciliacion.detalle(sesion.empresa(), conciliacion.conciliar(sesion.empresa(), periodo)).orElseThrow();
    }

    /** Última conciliación: el panel la vuelve a mostrar tras recargar. «{}» si aún no hay ninguna. */
    @GetMapping("/conciliaciones/ultima")
    Object ultima(@RequestParam(required = false) String periodo, Sesion sesion) {
        return conciliacion.ultima(sesion.empresa(), periodo).<Object>map(c -> c).orElse(Map.of());
    }

    @GetMapping("/conciliaciones/{id}/csv")
    ResponseEntity<?> exportar(@PathVariable long id, Sesion sesion) {
        return conciliacion.csv(sesion.empresa(), id).<ResponseEntity<?>>map(csv -> Descargas.csv("yupana_conciliacion_" + id + ".csv", csv))
                .orElseGet(() -> ResponseEntity.status(404).body(CuerpoErrores.de("Conciliación no encontrada.")));
    }
}
