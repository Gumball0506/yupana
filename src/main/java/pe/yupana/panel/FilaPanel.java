package pe.yupana.panel;

import java.math.BigDecimal;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Una empresa en el panel del estudio. «conciliacion» es null si aún no se concilia. */
public record FilaPanel(
        long id,
        String ruc,
        @JsonProperty("razon_social") String razonSocial,
        String telefono,
        boolean activa,
        ResumenConciliacion conciliacion,
        long cargas,
        List<String> accesos,
        @JsonProperty("ultimo_mensaje") String ultimoMensaje) {

    /** Última conciliación resumida; «pendientes» = casos que no cuadran y nadie revisó aún. */
    public record ResumenConciliacion(
            long id,
            String fecha,
            String periodo,
            int total,
            @JsonProperty("con_diferencia") int conDiferencia,
            @JsonProperty("igv_revision") BigDecimal igvRevision,
            long pendientes) {
    }
}
