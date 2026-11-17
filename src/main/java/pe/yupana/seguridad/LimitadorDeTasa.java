package pe.yupana.seguridad;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Límite de peticiones por clave (por ejemplo «IP + grupo de rutas») en ventanas fijas (ISO 27001 A.8.6 gestión de la
 * capacidad). Protege el ingreso y la carga de archivos de una ráfaga de peticiones, aunque no fallen.
 */
public class LimitadorDeTasa {

    private static final int LIMITE_DE_LLAVES = 10_000;

    private record Ventana(Instant inicio, int cuenta) {}

    private final Duration duracion;
    private final Clock reloj;
    private final ConcurrentHashMap<String, Ventana> ventanas = new ConcurrentHashMap<>();

    public LimitadorDeTasa(Duration duracion, Clock reloj) {
        this.duracion = duracion;
        this.reloj = reloj;
    }

    /**
     * Cuenta una petición. Devuelve 0 si está permitida o los segundos que faltan para que se abra otra ventana si ya se
     * superó el máximo.
     */
    public long intentar(String llave, int maximo) {
        Instant ahora = reloj.instant();
        if (ventanas.size() > LIMITE_DE_LLAVES) depurar(ahora);
        long[] espera = {0};
        ventanas.compute(llave, (k, v) -> {
            if (v == null || !v.inicio().plus(duracion).isAfter(ahora)) return new Ventana(ahora, 1);
            if (v.cuenta() >= maximo) {
                espera[0] = Math.max(1, Duration.between(ahora, v.inicio().plus(duracion)).toSeconds());
                return v;
            }
            return new Ventana(v.inicio(), v.cuenta() + 1);
        });
        return espera[0];
    }

    private void depurar(Instant ahora) {
        ventanas.entrySet().removeIf(e -> !e.getValue().inicio().plus(duracion).isAfter(ahora));
    }
}
