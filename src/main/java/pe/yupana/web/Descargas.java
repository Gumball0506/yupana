package pe.yupana.web;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/** Respuesta de descarga de un CSV. El nombre lo arma el servidor, nunca viene del usuario. */
final class Descargas {

    private Descargas() {}

    static ResponseEntity<String> csv(String nombreArchivo, String contenido) {
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"" + nombreArchivo + "\"")
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(contenido);
    }
}
