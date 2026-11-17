package pe.yupana.web;

import java.util.List;

/** Cuerpo de error estándar: solo mensajes pensados para el usuario, nunca detalles internos. */
public record CuerpoErrores(List<String> errores) {

    static CuerpoErrores de(String mensaje) {
        return new CuerpoErrores(List.of(mensaje));
    }
}
