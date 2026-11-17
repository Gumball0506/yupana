package pe.yupana.persistencia;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import pe.yupana.dominio.Fechas;
import pe.yupana.uso.ComentarioFeedback;
import pe.yupana.uso.Feedback;
import pe.yupana.uso.RepositorioFeedback;
import pe.yupana.uso.ResumenFeedback;

@Repository
class RepositorioFeedbackJdbc implements RepositorioFeedback {

    private static final RowMapper<ComentarioFeedback> A_COMENTARIO = (rs, i) ->
            new ComentarioFeedback(rs.getString("fecha"), rs.getString("respuesta"), rs.getString("comentario"));

    private final JdbcClient jdbc;

    RepositorioFeedbackJdbc(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void registrar(Feedback f) {
        jdbc.sql("INSERT INTO feedback(sesion, respuesta, comentario, fecha) VALUES (?,?,?,?)")
                .params(f.sesion(), f.respuesta(), f.comentario(), Fechas.ahora()).update();
    }

    @Override
    public ResumenFeedback resumen() {
        Map<String, Long> conteo = new LinkedHashMap<>(Map.of("si", 0L, "mas_o_menos", 0L, "no", 0L));
        jdbc.sql("SELECT respuesta, COUNT(*) AS n FROM feedback GROUP BY respuesta")
                .query((rs, i) -> Map.entry(rs.getString("respuesta"), rs.getLong("n"))).list()
                .forEach(e -> conteo.put(e.getKey(), e.getValue()));
        return new ResumenFeedback(conteo, jdbc.sql(
                "SELECT fecha, respuesta, comentario FROM feedback WHERE comentario IS NOT NULL ORDER BY id DESC LIMIT 20")
                .query(A_COMENTARIO).list());
    }

    @Override
    public List<ComentarioFeedback> todos() {
        return jdbc.sql("SELECT fecha, respuesta, comentario FROM feedback ORDER BY id").query(A_COMENTARIO).list();
    }
}
