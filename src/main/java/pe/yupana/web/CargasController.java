package pe.yupana.web;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import java.nio.charset.StandardCharsets;

import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import pe.yupana.carga.RepositorioCargas;
import pe.yupana.carga.ResumenImportacion;
import pe.yupana.carga.ServicioCargas;
import pe.yupana.carga.VistaImportacion;
import pe.yupana.dominio.Textos;
import pe.yupana.carga.PeriodoCarga;
import pe.yupana.seguridad.Sesion;

@RestController
@RequestMapping("/api/cargas")
class CargasController {

    private static final int VISTA_PREVIA = 10;

    private final ServicioCargas cargas;

    private final boolean periodoObligatorio;

    CargasController(ServicioCargas cargas,
            @Value("${yupana.periodo-obligatorio:false}") boolean periodoObligatorio) {
        this.periodoObligatorio = periodoObligatorio;
        this.cargas = cargas;
    }

    @PostMapping
    VistaImportacion cargar(@RequestParam String origen, @RequestParam MultipartFile archivo,
                             @RequestParam(required = false) String periodo, Sesion sesion) throws IOException {
        sesion.exigirEstudio();
        PeriodoCarga.exigirSiCorresponde(periodo, periodoObligatorio);
        String nombre = Textos.nombreSeguro(archivo.getOriginalFilename());
        long id = cargas.importar(sesion.empresa(), origen, nombre, archivo.getBytes(), periodo);
        return cargas.detalle(sesion.empresa(), id, VISTA_PREVIA).orElseThrow();
    }

    @GetMapping
    Map<String, VistaImportacion> ultimas(@RequestParam(required = false) String periodo, Sesion sesion) {
        return cargas.ultimas(sesion.empresa(), periodo);
    }

    @GetMapping("/historial")
    List<ResumenImportacion> historial(Sesion sesion) {
        return cargas.historial(sesion.empresa());
    }

    @GetMapping("/{id}")
    ResponseEntity<?> ver(@PathVariable long id, @RequestParam(defaultValue = "false") boolean todas, Sesion sesion) {
        int limite = todas ? RepositorioCargas.TODAS_LAS_FILAS : VISTA_PREVIA;
        return cargas.detalle(sesion.empresa(), id, limite).<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(404).body(CuerpoErrores.de("Importación no encontrada.")));
    }

    @GetMapping("/{id}/archivo")
    ResponseEntity<?> descargar(@PathVariable long id, Sesion sesion) {
        return cargas.archivo(sesion.empresa(), id).<ResponseEntity<?>>map(archivo -> ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(archivo.nombre(), StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(archivo.contenido()))
                .orElseGet(() -> ResponseEntity.status(404).body(CuerpoErrores.de("Archivo original no disponible.")));
    }

    /** Se puede apagar con yupana.reinicio-habilitado=false: en un servidor público nadie debe poder borrar datos. */
    @DeleteMapping
    Map<String, Integer> reiniciar(Sesion sesion) {
        sesion.exigirEstudio();
        return Map.of("borradas", cargas.reiniciar(sesion.empresa()));
    }
}
