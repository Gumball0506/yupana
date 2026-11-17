package pe.yupana.carga;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Una importación con su resumen y sus filas (la vista previa o todas). */
public record VistaImportacion(
        long id,
        String origen,
        @JsonProperty("nombre_archivo") String nombreArchivo,
        String fecha,
        @JsonProperty("total_filas") int totalFilas,
        List<String> advertencias,
        @JsonProperty("origen_nombre") String origenNombre,
        ResumenLote resumen,
        List<VistaComprobante> filas) {
}
