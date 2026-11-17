package pe.yupana.sire;

import java.util.List;
import java.util.Optional;

/** Puerto de salida de los tickets del simulador SIRE. */
public interface RepositorioSire {

    /** Ticket tal como se guardó: el estado «EN_PROCESO» no se guarda, se calcula con la hora de creación. */
    record TicketGuardado(long id, String periodo, String creado, int filas, EstadoTicket estado) {}

    long guardar(long empresaId, String periodo, String creado, int filas, String contenido);

    /** Vacío si el ticket no existe o es de otra empresa. */
    Optional<TicketGuardado> buscar(long empresaId, long id);

    String contenido(long id);

    void cambiarEstado(long id, EstadoTicket estado);

    /** Ids de los 10 más recientes de la empresa. */
    List<Long> recientes(long empresaId);

    void borrarDeEmpresa(long empresaId);
}
