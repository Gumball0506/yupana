package pe.yupana.acceso;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bloqueo temporal por ingresos fallidos (ISO 27001 A.8.5 autenticación segura). Lleva la cuenta por una llave
 * cualquiera (usuario tecleado o IP); no sabe si el usuario existe, así que bloquear no revela qué cuentas hay.
 * Los fallos solo cuentan dentro de la ventana de bloqueo: un fallo viejo se olvida.
 */
public class LimitadorDeIntentos {

    private static final int LIMITE_DE_LLAVES = 10_000;

    private record Estado(int fallos, Instant ultimoFallo, Instant bloqueadoHasta) {}

    private final int maxIntentos;
    private final Duration bloqueo;
    private final Clock reloj;
    private final ConcurrentHashMap<String, Estado> estados = new ConcurrentHashMap<>();

    public LimitadorDeIntentos(int maxIntentos, Duration bloqueo, Clock reloj) {
        if (maxIntentos < 1 || bloqueo.isNegative() || bloqueo.isZero())
            throw new IllegalArgumentException("yupana.login: max-intentos ≥ 1 y bloqueo-minutos ≥ 1");
        this.maxIntentos = maxIntentos;
        this.bloqueo = bloqueo;
        this.reloj = reloj;
    }

    /** Segundos que faltan para poder reintentar; 0 si la llave no está bloqueada. */
    public long segundosDeBloqueo(String llave) {
        Estado e = estados.get(llave);
        if (e == null || e.bloqueadoHasta() == null) return 0;
        long restantes = Duration.between(reloj.instant(), e.bloqueadoHasta()).toSeconds();
        return Math.max(0, restantes);
    }

    /** Registra un fallo; devuelve true si con este fallo la llave queda bloqueada. */
    public boolean registrarFallo(String llave) {
        Instant ahora = reloj.instant();
        if (estados.size() > LIMITE_DE_LLAVES) depurar(ahora);
        boolean[] bloqueada = {false};
        estados.compute(llave, (k, anterior) -> {
            int fallos = 1;
            if (anterior != null && anterior.ultimoFallo().plus(bloqueo).isAfter(ahora)) fallos = anterior.fallos() + 1;
            if (fallos >= maxIntentos) {
                bloqueada[0] = true;
                return new Estado(0, ahora, ahora.plus(bloqueo)); // al bloquear, la cuenta vuelve a empezar
            }
            return new Estado(fallos, ahora, null);
        });
        return bloqueada[0];
    }

    /** Un ingreso correcto reinicia el contador. */
    public void registrarExito(String llave) {
        estados.remove(llave);
    }

    private void depurar(Instant ahora) {
        estados.entrySet().removeIf(e -> e.getValue().ultimoFallo().plus(bloqueo).isBefore(ahora)
                && (e.getValue().bloqueadoHasta() == null || e.getValue().bloqueadoHasta().isBefore(ahora)));
    }
}
