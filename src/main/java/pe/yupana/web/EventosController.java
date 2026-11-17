package pe.yupana.web;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import pe.yupana.seguridad.Sesion;
import pe.yupana.uso.EventoDeUso;
import pe.yupana.uso.ExportadorFeedback;
import pe.yupana.uso.Feedback;
import pe.yupana.uso.RepositorioEventos;
import pe.yupana.uso.RepositorioFeedback;
import pe.yupana.uso.ResumenEventos;
import pe.yupana.uso.ResumenFeedback;

/** Marketing 5.0: eventos anónimos de uso y feedback. Registrar es libre; los resúmenes son solo para el estudio. */
@RestController
@RequestMapping("/api")
class EventosController {

    private final RepositorioEventos eventos;
    private final RepositorioFeedback feedback;
    private final ExportadorFeedback exportador;

    EventosController(RepositorioEventos eventos, RepositorioFeedback feedback, ExportadorFeedback exportador) {
        this.eventos = eventos;
        this.feedback = feedback;
        this.exportador = exportador;
    }

    @PostMapping("/eventos")
    ResponseEntity<?> registrar(@RequestBody Map<String, String> cuerpo) {
        return EventoDeUso.validar(cuerpo.get("sesion"), cuerpo.get("tipo"), cuerpo.get("detalle"))
                .<ResponseEntity<?>>map(evento -> {
                    eventos.registrar(evento);
                    return ResponseEntity.noContent().build();
                })
                .orElseGet(() -> ResponseEntity.badRequest().body(CuerpoErrores.de("Evento no válido.")));
    }

    @GetMapping("/eventos/resumen")
    ResumenEventos resumen(Sesion sesion) {
        sesion.exigirEstudio();
        return eventos.resumen();
    }

    @PostMapping("/feedback")
    ResponseEntity<?> opinar(@RequestBody Map<String, String> cuerpo) {
        return Feedback.validar(cuerpo.get("sesion"), cuerpo.get("respuesta"), cuerpo.get("comentario"))
                .<ResponseEntity<?>>map(f -> {
                    feedback.registrar(f);
                    return ResponseEntity.noContent().build();
                })
                .orElseGet(() -> ResponseEntity.badRequest().body(CuerpoErrores.de("Feedback no válido.")));
    }

    @GetMapping("/feedback/resumen")
    ResumenFeedback resumenFeedback(Sesion sesion) {
        sesion.exigirEstudio();
        return feedback.resumen();
    }

    @GetMapping("/feedback/csv")
    ResponseEntity<String> feedbackCsv(Sesion sesion) {
        sesion.exigirEstudio();
        return Descargas.csv("yupana_feedback.csv", exportador.generar(feedback.todos()));
    }
}
