package pe.yupana.persistencia;

import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import pe.yupana.sire.EstadoTicket;
import pe.yupana.sire.RepositorioSire;

@Repository
class RepositorioSireJdbc implements RepositorioSire {

    private final JdbcClient jdbc;

    RepositorioSireJdbc(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public long guardar(long empresaId, String periodo, String creado, int filas, String contenido) {
        var clave = new GeneratedKeyHolder();
        jdbc.sql("INSERT INTO sire_tickets(empresa_id, periodo, creado, filas, contenido, estado) VALUES (?,?,?,?,?,?)")
                .params(empresaId, periodo, creado, filas, contenido, EstadoTicket.GENERADO.name()).update(clave, "id");
        return clave.getKey().longValue();
    }

    @Override
    public Optional<TicketGuardado> buscar(long empresaId, long id) {
        return jdbc.sql("SELECT id, periodo, creado, filas, estado FROM sire_tickets WHERE id = ? AND empresa_id = ?")
                .params(id, empresaId)
                .query((rs, i) -> new TicketGuardado(rs.getLong("id"), rs.getString("periodo"), rs.getString("creado"),
                        rs.getInt("filas"), EstadoTicket.valueOf(rs.getString("estado"))))
                .optional();
    }

    @Override
    public String contenido(long id) {
        return jdbc.sql("SELECT contenido FROM sire_tickets WHERE id = ?").param(id).query(String.class).single();
    }

    @Override
    public void cambiarEstado(long id, EstadoTicket estado) {
        jdbc.sql("UPDATE sire_tickets SET estado = ? WHERE id = ?").params(estado.name(), id).update();
    }

    @Override
    public List<Long> recientes(long empresaId) {
        return jdbc.sql("SELECT id FROM sire_tickets WHERE empresa_id = ? ORDER BY id DESC LIMIT 10").param(empresaId)
                .query(Long.class).list();
    }

    @Override
    public void borrarDeEmpresa(long empresaId) {
        jdbc.sql("DELETE FROM sire_tickets WHERE empresa_id = ?").param(empresaId).update();
    }
}
