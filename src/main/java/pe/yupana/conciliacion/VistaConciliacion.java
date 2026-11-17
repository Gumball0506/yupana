package pe.yupana.conciliacion;

import java.math.BigDecimal;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Una conciliación completa tal como la pinta el panel. */
public record VistaConciliacion(
        long id,
        @JsonProperty("id_compras") long idCompras,
        @JsonProperty("id_rce") long idRce,
        String fecha,
        int total,
        int conciliados,
        @JsonProperty("con_diferencia") int conDiferencia,
        @JsonProperty("igv_revision") BigDecimal igvRevision,
        @JsonProperty("diferencia_igv") BigDecimal diferenciaIgv,
        @JsonProperty("archivo_compras") String archivoCompras,
        @JsonProperty("archivo_rce") String archivoRce,
        List<VistaResultado> resultados,
        String periodo) {
}
