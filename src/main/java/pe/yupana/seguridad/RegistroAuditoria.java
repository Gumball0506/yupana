package pe.yupana.seguridad;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Observer de los eventos de auditoría: los servicios publican, este componente persiste y registra.
 * Usa su propia transacción: un rechazo (que revierte la carga) igual deja su rastro.
 */
@Component
public class RegistroAuditoria {

    private static final Logger BITACORA = LoggerFactory.getLogger("AUDITORIA");

    private final JdbcClient jdbc;

    RegistroAuditoria(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrar(EventoAuditoria evento) {
        BITACORA.info("evento={} detalle={}", evento.tipo(), evento.detalle());
        jdbc.sql("INSERT INTO auditoria(fecha, evento, detalle) VALUES (?,?,?)")
                .params(LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS).toString(), evento.tipo().name(), evento.detalle())
                .update();
    }
}
