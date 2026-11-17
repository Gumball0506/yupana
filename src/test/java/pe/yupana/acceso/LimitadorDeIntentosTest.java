package pe.yupana.acceso;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;

import org.junit.jupiter.api.Test;

/** El bloqueo temporal, sin Spring ni base de datos. */
class LimitadorDeIntentosTest {

    private final RelojMutable reloj = new RelojMutable();
    private final LimitadorDeIntentos limitador = new LimitadorDeIntentos(5, Duration.ofMinutes(15), reloj);

    @Test
    void elQuintoFalloBloquea() {
        for (int i = 1; i <= 4; i++) assertFalse(limitador.registrarFallo("u:ana"), "fallo " + i);
        assertEquals(0, limitador.segundosDeBloqueo("u:ana"));
        assertTrue(limitador.registrarFallo("u:ana"));
        assertEquals(15 * 60, limitador.segundosDeBloqueo("u:ana"));
    }

    @Test
    void sePuedeReintentarAlTerminarElBloqueo() {
        for (int i = 0; i < 5; i++) limitador.registrarFallo("u:ana");
        reloj.avanzar(Duration.ofMinutes(14));
        assertTrue(limitador.segundosDeBloqueo("u:ana") > 0);
        reloj.avanzar(Duration.ofMinutes(1));
        assertEquals(0, limitador.segundosDeBloqueo("u:ana"));
        // y la cuenta empieza de nuevo: hacen falta otros 5 fallos
        for (int i = 1; i <= 4; i++) assertFalse(limitador.registrarFallo("u:ana"));
        assertTrue(limitador.registrarFallo("u:ana"));
    }

    @Test
    void unIngresoCorrectoReiniciaElContador() {
        for (int i = 0; i < 4; i++) limitador.registrarFallo("u:ana");
        limitador.registrarExito("u:ana");
        for (int i = 1; i <= 4; i++) assertFalse(limitador.registrarFallo("u:ana"));
    }

    @Test
    void losFallosViejosSeOlvidan() {
        for (int i = 0; i < 4; i++) limitador.registrarFallo("u:ana");
        reloj.avanzar(Duration.ofMinutes(16));
        assertFalse(limitador.registrarFallo("u:ana"), "un fallo fuera de la ventana no suma");
    }

    @Test
    void lasLlavesSonIndependientes() {
        for (int i = 0; i < 5; i++) limitador.registrarFallo("u:ana");
        assertEquals(0, limitador.segundosDeBloqueo("u:luis"));
        assertEquals(0, limitador.segundosDeBloqueo("i:10.0.0.1"));
    }

    @Test
    void rechazaParametrosInvalidos() {
        assertThrows(IllegalArgumentException.class, () -> new LimitadorDeIntentos(0, Duration.ofMinutes(1), reloj));
        assertThrows(IllegalArgumentException.class, () -> new LimitadorDeIntentos(5, Duration.ZERO, reloj));
    }
}
