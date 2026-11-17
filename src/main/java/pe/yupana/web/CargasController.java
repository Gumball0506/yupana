package pe.yupana.web;

import java.io.IOException;
import java.util.List;
import java.util.Map;

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
import pe.yupana.seguridad.Sesion;

@RestController
@RequestMapping("/api/cargas")
class CargasController {

    private static final int VISTA_PREVIA = 10;

    private final ServicioCargas cargas;

    CargasController(ServicioCargas cargas) {
        this.cargas = cargas;
    }

    @PostMapping
    VistaImportacion cargar(@RequestParam String origen, @RequestParam MultipartFile archivo, Sesion sesion) throws IOException {
        sesion.exigirEstudio();
        String nombre = Textos.nombreSeguro(archivo.getOriginalFilename());
        long id = cargas.importar(sesion.empresa(), origen, nombre, archivo.getBytes());
        return cargas.detalle(sesion.empresa(), id, VISTA_PREVIA).orElseThrow();
    }

    @GetMapping
    Map<String, VistaImportacion> ultimas(Sesion sesion) {
        return cargas.ultimas(sesion.empresa());
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

    /** Se puede apagar con yupana.reinicio-habilitado=false: en un servidor público nadie debe poder borrar datos. */
    @DeleteMapping
    Map<String, Integer> reiniciar(Sesion sesion) {
        sesion.exigirEstudio();
        return Map.of("borradas", cargas.reiniciar(sesion.empresa()));
    }
}
