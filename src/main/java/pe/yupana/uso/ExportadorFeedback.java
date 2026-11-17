package pe.yupana.uso;

import java.util.List;

import org.springframework.stereotype.Component;

import pe.yupana.dominio.Csv;

/** Todo el feedback en CSV para armar la matriz crítica → decisión → evidencia (tarea 3.3-A). */
@Component
public class ExportadorFeedback {

    public String generar(List<ComentarioFeedback> comentarios) {
        StringBuilder csv = new StringBuilder(Csv.BOM).append("fecha,respuesta,comentario").append(Csv.SALTO);
        for (ComentarioFeedback c : comentarios)
            csv.append(Csv.celda(c.fecha())).append(',').append(Csv.celda(c.respuesta())).append(',')
                    .append(Csv.celda(c.comentario())).append(Csv.SALTO);
        return csv.toString();
    }
}
