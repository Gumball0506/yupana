package pe.yupana.carga;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Comprobante tal como lo ve la pantalla. Los nombres JSON son el contrato con el panel (snake_case). */
public record VistaComprobante(
        @JsonProperty("ruc_proveedor") String rucProveedor,
        @JsonProperty("tipo_comprobante") String tipoComprobante,
        String serie,
        String numero,
        @JsonProperty("fecha_emision") String fechaEmision,
        @JsonProperty("base_imponible") BigDecimal baseImponible,
        BigDecimal igv,
        BigDecimal total) {
}
