package pe.yupana.web;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import pe.yupana.carga.Celda;
import pe.yupana.carga.ErrorCarga;
import pe.yupana.carga.VistaRechazo;

/** Error de carga: mensajes, celdas exactas y, si hay, las filas rechazadas para marcarlas en rojo. */
public record CuerpoErrorCarga(List<String> errores, List<Celda> celdas, @JsonInclude(JsonInclude.Include.NON_NULL) VistaRechazo vista) {

    static CuerpoErrorCarga de(ErrorCarga e) {
        return new CuerpoErrorCarga(e.errores(), e.celdas(), e.vista());
    }
}
