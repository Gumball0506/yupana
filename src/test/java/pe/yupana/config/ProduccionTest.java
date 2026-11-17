package pe.yupana.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import pe.yupana.YupanaApplication;

/** El perfil «produccion» exige una clave propia: sin ella, o con la de demostración, el servidor no arranca. */
class ProduccionTest {

    /** La clave llega como argumento: tiene más precedencia que application-produccion.properties (como una variable de entorno). */
    private static ConfigurableApplicationContext arrancar(String baseDeDatos, String clave) {
        var builder = new SpringApplicationBuilder(YupanaApplication.class).profiles("produccion").web(WebApplicationType.SERVLET)
                .properties("server.port=0", "spring.datasource.url=jdbc:h2:mem:" + baseDeDatos + ";DATABASE_TO_LOWER=TRUE");
        return clave == null ? builder.run() : builder.run("--yupana.semilla-clave=" + clave);
    }

    private static String causaRaiz(Throwable e) {
        while (e.getCause() != null) e = e.getCause();
        return e.getMessage();
    }

    @Test
    void sinVariablesNoArrancaYDiceQueFalta() {
        // YUPANA_SEED_CLAVE vacía = no definida (así queda con el perfil, sin variable de entorno)
        var error = assertThrows(Exception.class, () -> arrancar("prod_sin", null));
        assertTrue(causaRaiz(error).contains("falta la variable de entorno YUPANA_SEED_CLAVE"), causaRaiz(error));
    }

    @Test
    void conLaClaveDeDemostracionNoArranca() {
        var error = assertThrows(Exception.class, () -> arrancar("prod_demo", "Yupana2026*"));
        assertTrue(causaRaiz(error).contains("no puede ser la clave de demostración"), causaRaiz(error));
    }

    @Test
    void conUnaClaveCortaNoArranca() {
        var error = assertThrows(Exception.class, () -> arrancar("prod_corta", "corta1"));
        assertTrue(causaRaiz(error).contains("al menos 12 caracteres"), causaRaiz(error));
    }

    @Test
    void conVariablesArrancaYEndurece() {
        try (ConfigurableApplicationContext contexto = arrancar("prod_ok", "Una-clave-propia-2026")) {
            var env = contexto.getEnvironment();
            assertEquals("true", env.getProperty("server.servlet.session.cookie.secure"));
            assertEquals("false", env.getProperty("yupana.reinicio-habilitado"));
            assertEquals("false", env.getProperty("yupana.semilla-datos"));
            assertEquals("0.0.0.0", env.getProperty("server.address"));
            assertTrue(contexto.getBean(PropiedadesYupana.class).hsts());
        }
    }
}
