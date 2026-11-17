package pe.yupana.conciliacion;

import java.math.BigDecimal;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import pe.yupana.carga.VistaComprobante;

/** Resultado de un comprobante con los valores de ambas fuentes (la «hoja de cuadre» del panel) y su revisión (null = pendiente). */
public record VistaResultado(
        long id,
        @JsonProperty("id_conciliacion") long idConciliacion,
        int orden,
        @JsonProperty("ruc_proveedor") String rucProveedor,
        @JsonProperty("tipo_comprobante") String tipoComprobante,
        String serie,
        String numero,
        String resultado,
        String causa,
        @JsonProperty("igv_compras") BigDecimal igvCompras,
        @JsonProperty("igv_rce") BigDecimal igvRce,
        VistaComprobante compras,
        VistaComprobante rce,
        List<VistaDiferencia> diferencias,
        VistaRevision revision) {
}
