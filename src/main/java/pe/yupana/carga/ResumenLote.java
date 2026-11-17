package pe.yupana.carga;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Totales de una importación para la tarjeta de resumen. */
public record ResumenLote(
        long comprobantes,
        String desde,
        String hasta,
        @JsonProperty("base_imponible") BigDecimal baseImponible,
        BigDecimal igv,
        BigDecimal total) {
}
