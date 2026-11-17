package pe.yupana.uso;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ResumenEventos(
        long sesiones,
        Map<String, Long> eventos,
        @JsonProperty("motivos_fallo") Map<String, Long> motivosFallo) {
}
