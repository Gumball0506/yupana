package pe.yupana.acceso;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** Reloj de prueba que solo avanza cuando se le pide. */
public class RelojMutable extends Clock {

    private volatile Instant ahora = Instant.parse("2026-11-17T12:00:00Z");

    public void avanzar(Duration duracion) {
        ahora = ahora.plus(duracion);
    }

    @Override public ZoneId getZone() { return ZoneOffset.UTC; }

    @Override public Clock withZone(ZoneId zona) { return this; }

    @Override public Instant instant() { return ahora; }
}
