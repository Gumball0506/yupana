package pe.yupana.persistencia;

import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import pe.yupana.mensajes.RepositorioMensajes;
import pe.yupana.mensajes.VistaMensaje;

@Repository
class RepositorioMensajesJdbc implements RepositorioMensajes {

    private final JdbcClient jdbc;

    RepositorioMensajesJdbc(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public long guardar(long empresaId, String direccion, String texto, String fecha, String usuario) {
        var clave = new GeneratedKeyHolder();
        jdbc.sql("INSERT INTO mensajes(empresa_id, direccion, texto, fecha, usuario) VALUES (?,?,?,?,?)")
                .params(empresaId, direccion, texto, fecha, usuario).update(clave, "id");
        return clave.getKey().longValue();
    }

    @Override
    public List<VistaMensaje> listar(long empresaId) {
        return jdbc.sql("SELECT id, direccion, texto, fecha, usuario FROM mensajes WHERE empresa_id = ? ORDER BY id").param(empresaId)
                .query((rs, i) -> new VistaMensaje(rs.getLong("id"), rs.getString("direccion"), rs.getString("texto"),
                        rs.getString("fecha"), rs.getString("usuario")))
                .list();
    }

    @Override
    public Optional<String> ultimaFecha(long empresaId) {
        return jdbc.sql("SELECT fecha FROM mensajes WHERE empresa_id = ? ORDER BY id DESC LIMIT 1").param(empresaId)
                .query(String.class).optional();
    }

    @Override
    public void borrarDeEmpresa(long empresaId) {
        jdbc.sql("DELETE FROM mensajes WHERE empresa_id = ?").param(empresaId).update();
    }
}
