package pe.yupana.sire;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import pe.yupana.dominio.SimuladorSire.Variacion;

/**
 * Ticket del simulador como lo ve la pantalla. «estado» = EN_PROCESO los primeros segundos; «archivo» es null hasta
 * que está listo. «variaciones» solo viene al solicitarlo (explica por qué la propuesta no es igual a tus compras).
 */
public record VistaTicket(
        long id,
        String periodo,
        String creado,
        int filas,
        String estado,
        @JsonProperty("numero_ticket") String numeroTicket,
        String archivo,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<Variacion> variaciones) {

    VistaTicket conVariaciones(List<Variacion> lista) {
        return new VistaTicket(id, periodo, creado, filas, estado, numeroTicket, archivo, lista);
    }
}
