package pe.yupana.seguridad;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import pe.yupana.acceso.RelojMutable;

/** Límite de peticiones por minuto, sin Spring. */
class LimitadorDeTasaTest {

    private final RelojMutable reloj = new RelojMutable();
    private final LimitadorDeTasa limitador = new LimitadorDeTasa(Duration.ofMinutes(1), reloj);

    @Test
    void permiteHastaElMaximoYLuegoDiceCuantoEsperar() {
        for (int i = 1; i <= 3; i++) assertEquals(0, limitador.intentar("ip|/api/login", 3), "petición " + i);
        long espera = limitador.intentar("ip|/api/login", 3);
        assertTrue(espera > 0 && espera <= 60, "espera " + espera);
    }

    @Test
    void laVentanaSeReabreAlPasarUnMinuto() {
        for (int i = 0; i < 3; i++) limitador.intentar("ip|/api/login", 3);
        assertTrue(limitador.intentar("ip|/api/login", 3) > 0);
        reloj.avanzar(Duration.ofSeconds(61));
        assertEquals(0, limitador.intentar("ip|/api/login", 3));
    }

    @Test
    void cadaLlaveTieneSuPropiaCuenta() {
        for (int i = 0; i < 3; i++) limitador.intentar("10.0.0.1|/api/login", 3);
        assertTrue(limitador.intentar("10.0.0.1|/api/login", 3) > 0);
        assertEquals(0, limitador.intentar("10.0.0.2|/api/login", 3));      // otra IP
        assertEquals(0, limitador.intentar("10.0.0.1|/api/cargas", 3));     // otra ruta
    }
}
