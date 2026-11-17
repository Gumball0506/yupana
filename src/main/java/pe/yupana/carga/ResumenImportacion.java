package pe.yupana.carga;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Fila del historial de importaciones. */
public record ResumenImportacion(
        long id,
        String origen,
        @JsonProperty("nombre_archivo") String nombreArchivo,
        String fecha,
        @JsonProperty("total_filas") int totalFilas) {
}
