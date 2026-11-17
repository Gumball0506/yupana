package pe.yupana.persistencia;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import pe.yupana.uso.EventoDeUso;
import pe.yupana.uso.RepositorioEventos;
import pe.yupana.uso.ResumenEventos;

@Repository
class RepositorioEventosJdbc implements RepositorioEventos {

    private final JdbcClient jdbc;

    RepositorioEventosJdbc(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void registrar(EventoDeUso evento) {
        jdbc.sql("INSERT INTO eventos(sesion, tipo, detalle, fecha) VALUES (?,?,?,?)")
                .params(evento.sesion(), evento.tipo(), evento.detalle(), LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS).toString())
                .update();
    }

    @Override
    public ResumenEventos resumen() {
        long sesiones = jdbc.sql("SELECT COUNT(DISTINCT sesion) FROM eventos").query(Long.class).single();
        return new ResumenEventos(sesiones,
                conteo("SELECT tipo AS clave, COUNT(*) AS n FROM eventos GROUP BY tipo"),
                conteo("SELECT detalle AS clave, COUNT(*) AS n FROM eventos WHERE tipo = 'carga_fallida' GROUP BY detalle"));
    }

    private Map<String, Long> conteo(String sql) {
        Map<String, Long> conteo = new TreeMap<>();
        jdbc.sql(sql).query((rs, i) -> Map.entry(String.valueOf(rs.getString("clave")), rs.getLong("n")))
                .list().forEach(e -> conteo.put(e.getKey(), e.getValue()));
        return conteo;
    }
}
