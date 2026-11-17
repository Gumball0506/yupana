package pe.yupana.uso;

import java.util.List;

/** Puerto de salida del feedback anónimo. */
public interface RepositorioFeedback {

    void registrar(Feedback feedback);

    ResumenFeedback resumen();

    /** Todas las respuestas, la más antigua primero (para la matriz crítica → decisión → evidencia). */
    List<ComentarioFeedback> todos();
}
