package pe.yupana.conciliacion;

import com.fasterxml.jackson.annotation.JsonProperty;

public record VistaDiferencia(
        String campo,
        @JsonProperty("valor_compras") String valorCompras,
        @JsonProperty("valor_rce") String valorRce) {
}
