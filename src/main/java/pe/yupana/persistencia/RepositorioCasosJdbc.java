package pe.yupana.persistencia;

import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import pe.yupana.casos.RepositorioCasos;
import pe.yupana.casos.VistaAviso;
import pe.yupana.conciliacion.VistaRevision;

@Repository
class RepositorioCasosJdbc implements RepositorioCasos {

    private final JdbcClient jdbc;

    RepositorioCasosJdbc(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<String> resultadoDe(long empresaId, long idResultado) {
        return jdbc.sql("""
                SELECT r.resultado FROM resultados r JOIN conciliaciones c ON c.id = r.id_conciliacion
                WHERE r.id = ? AND c.empresa_id = ?""").params(idResultado, empresaId).query(String.class).optional();
    }

    @Override
    public void registrar(long idResultado, VistaRevision r) {
        jdbc.sql("INSERT INTO acciones_caso(id_resultado, accion, nota, fecha, usuario) VALUES (?,?,?,?,?)")
                .params(idResultado, r.accion(), r.nota(), r.fecha(), r.usuario()).update();
    }

    @Override
    public void heredarRevisiones(long empresaId, long idConciliacion) {
        jdbc.sql("""
                INSERT INTO acciones_caso(id_resultado, accion, nota, fecha, usuario)
                SELECT rn.id, a.accion, a.nota, a.fecha, a.usuario
                FROM resultados rn
                JOIN conciliaciones actual ON actual.id = rn.id_conciliacion
                JOIN importaciones ia ON ia.id = actual.id_compras
                JOIN resultados ro ON ro.id_conciliacion = (
                    SELECT MAX(anterior.id)
                    FROM conciliaciones anterior JOIN importaciones ip ON ip.id = anterior.id_compras
                    WHERE anterior.id < actual.id AND anterior.empresa_id = ?
                      AND ip.periodo IS NOT DISTINCT FROM ia.periodo
                )
                     AND ro.ruc_proveedor = rn.ruc_proveedor AND ro.tipo_comprobante = rn.tipo_comprobante
                     AND ro.serie = rn.serie AND ro.numero = rn.numero AND ro.causa = rn.causa
                JOIN acciones_caso a ON a.id = (SELECT MAX(id) FROM acciones_caso WHERE id_resultado = ro.id)
                WHERE rn.id_conciliacion = ? AND rn.resultado <> 'CONCILIADO'""")
                .params(empresaId, idConciliacion).update();
    }

    @Override
    public long guardarAviso(long idConciliacion, String fecha, String asunto, String cuerpo) {
        var clave = new GeneratedKeyHolder();
        jdbc.sql("INSERT INTO avisos(id_conciliacion, fecha, asunto, cuerpo) VALUES (?,?,?,?)")
                .params(idConciliacion, fecha, asunto, cuerpo).update(clave, "id");
        return clave.getKey().longValue();
    }

    @Override
    public List<VistaAviso> avisos(long empresaId) {
        return jdbc.sql("""
                SELECT a.id, a.id_conciliacion, a.fecha, a.asunto, a.cuerpo
                FROM avisos a JOIN conciliaciones c ON c.id = a.id_conciliacion
                WHERE c.empresa_id = ? ORDER BY a.id DESC LIMIT 10""").param(empresaId)
                .query((rs, i) -> new VistaAviso(rs.getLong("id"), rs.getLong("id_conciliacion"), rs.getString("fecha"),
                        rs.getString("asunto"), rs.getString("cuerpo")))
                .list();
    }
}
