package pe.yupana.web;

import java.io.IOException;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import pe.yupana.carga.ErrorCarga;
import pe.yupana.dominio.ReglaIncumplida;
import pe.yupana.seguridad.AccesoDenegado;
import pe.yupana.seguridad.DemasiadosIntentos;
import pe.yupana.seguridad.EmpresaNoElegida;
import pe.yupana.seguridad.EventoAuditoria;
import pe.yupana.seguridad.EventoAuditoria.Tipo;
import pe.yupana.seguridad.SinSesion;

/**
 * Único lugar donde una excepción se convierte en respuesta HTTP. Al cliente solo llegan mensajes controlados;
 * el detalle técnico va a la bitácora con un código de correlación (ISO 27001 A.8.15 y A.8.12).
 */
@RestControllerAdvice
class ManejadorErrores {

    private static final Logger LOG = LoggerFactory.getLogger(ManejadorErrores.class);

    private final ApplicationEventPublisher auditoria;

    ManejadorErrores(ApplicationEventPublisher auditoria) {
        this.auditoria = auditoria;
    }

    @ExceptionHandler(ErrorCarga.class)
    ResponseEntity<CuerpoErrorCarga> errorDeCarga(ErrorCarga e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).body(CuerpoErrorCarga.de(e));
    }

    @ExceptionHandler(ReglaIncumplida.class)
    ResponseEntity<CuerpoErrores> reglaIncumplida(ReglaIncumplida e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).body(CuerpoErrores.de(e.getMessage()));
    }

    @ExceptionHandler(SinSesion.class)
    ResponseEntity<CuerpoErrores> sinSesion(SinSesion e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(CuerpoErrores.de(e.getMessage()));
    }

    @ExceptionHandler(DemasiadosIntentos.class)
    ResponseEntity<CuerpoErrores> demasiadosIntentos(DemasiadosIntentos e) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).header("Retry-After", String.valueOf(e.segundosRestantes()))
                .body(CuerpoErrores.de(e.getMessage()));
    }

    @ExceptionHandler(EmpresaNoElegida.class)
    ResponseEntity<CuerpoErrores> empresaNoElegida(EmpresaNoElegida e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(CuerpoErrores.de(e.getMessage()));
    }

    /** Un intento de saltarse el rol queda en la auditoría: es la señal de que alguien probó lo que no debía. */
    @ExceptionHandler(AccesoDenegado.class)
    ResponseEntity<CuerpoErrores> accesoDenegado(AccesoDenegado e) {
        auditoria.publishEvent(new EventoAuditoria(Tipo.ACCESO_DENEGADO, e.getMessage()));
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(CuerpoErrores.de(e.getMessage()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<CuerpoErrores> archivoMuyGrande() {
        return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE).body(CuerpoErrores.de("El archivo supera el tamaño máximo permitido (2 MB)."));
    }

    @ExceptionHandler({DataAccessException.class, IOException.class})
    ResponseEntity<CuerpoErrores> falloInterno(Exception e) {
        String codigo = UUID.randomUUID().toString().substring(0, 8);
        LOG.error("Fallo interno codigo={}", codigo, e);
        auditoria.publishEvent(new EventoAuditoria(Tipo.ERROR_INTERNO, "codigo=" + codigo + " tipo=" + e.getClass().getSimpleName()));
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(CuerpoErrores.de("Ocurrió un error interno. Código de soporte: " + codigo + "."));
    }
}
