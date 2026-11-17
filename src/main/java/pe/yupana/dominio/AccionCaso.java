package pe.yupana.dominio;

import java.util.Arrays;

/** Lo que el contador decide sobre un caso que no cuadra (tarea 3.1-B). Solo cambia un estado interno de la demo. */
public enum AccionCaso {
    CONFIRMADO("confirmar"), OBSERVADO("observar");

    private final String verbo;

    AccionCaso(String verbo) {
        this.verbo = verbo;
    }

    /** «confirmar» → CONFIRMADO; cualquier otro texto se rechaza (por ejemplo «presentar_sunat»). */
    public static AccionCaso desde(String verbo) {
        return Arrays.stream(values()).filter(a -> a.verbo.equals(verbo)).findFirst()
                .orElseThrow(() -> new ReglaIncumplida("Acción no válida: usa 'confirmar' u 'observar'."));
    }
}
