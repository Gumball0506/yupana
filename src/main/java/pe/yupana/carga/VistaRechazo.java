package pe.yupana.carga;

import java.util.List;

/** Lo que se muestra de un archivo rechazado: encabezado y solo las filas con error. */
public record VistaRechazo(List<String> encabezado, List<String> columnas, List<FilaRechazada> filas) {

    public record FilaRechazada(int n, List<String> celdas) {}
}
