package pe.yupana.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import pe.yupana.config.PropiedadesYupana;

/** HSTS solo cuando se sirve por HTTPS (propiedad yupana.hsts). */
class HstsTest {

    private static String hsts(boolean activo) throws Exception {
        var filtro = new FiltroCabecerasSeguridad(new PropiedadesYupana(new BigDecimal("0.01"), 5000, true, null, false, activo, new PropiedadesYupana.Login(5, 15), new PropiedadesYupana.Limite(20, 30)));
        var respuesta = new MockHttpServletResponse();
        filtro.doFilter(new MockHttpServletRequest("GET", "/"), respuesta, new MockFilterChain());
        return respuesta.getHeader("Strict-Transport-Security");
    }

    @Test
    void conHstsActivoLaCabeceraViaja() throws Exception {
        assertEquals("max-age=31536000; includeSubDomains", hsts(true));
    }

    @Test
    void sinHstsNoSeEnvia() throws Exception {
        assertNull(hsts(false));
    }
}
