package pe.yupana.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.support.ResourcePropertySource;

/** Cómo se arma la conexión a PostgreSQL y el puerto a partir de las variables de entorno (Render, Docker, VPS). */
class ConexionPorEntornoTest {

    private static StandardEnvironment entorno(Map<String, Object> variables) throws IOException {
        var env = new StandardEnvironment();
        // como Spring Boot: el archivo del perfil gana al general, y las variables de entorno ganan a los dos
        env.getPropertySources().addFirst(new ResourcePropertySource("classpath:application.properties"));
        env.getPropertySources().addFirst(new ResourcePropertySource("classpath:application-produccion.properties"));
        env.getPropertySources().addFirst(new MapPropertySource("variables", variables));
        return env;
    }

    @Test
    void armaLaUrlConHostPuertoYNombre() throws IOException {
        var env = entorno(Map.of("YUPANA_DB_HOST", "dpg-abc.oregon-postgres.render.com", "YUPANA_DB_PORT", "5432", "YUPANA_DB_NAME", "yupana"));
        assertEquals("jdbc:postgresql://dpg-abc.oregon-postgres.render.com:5432/yupana", env.getProperty("spring.datasource.url"));
    }

    @Test
    void laUrlCompletaTienePrioridad() throws IOException {
        var env = entorno(Map.of("YUPANA_DB_URL", "jdbc:postgresql://db:5432/otra", "YUPANA_DB_HOST", "ignorado"));
        assertEquals("jdbc:postgresql://db:5432/otra", env.getProperty("spring.datasource.url"));
    }

    @Test
    void usuarioYClaveSalenDeLasVariables() throws IOException {
        var env = entorno(Map.of("YUPANA_DB_USUARIO", "yupana", "YUPANA_DB_CLAVE", "secreta"));
        assertEquals("yupana", env.getProperty("spring.datasource.username"));
        assertEquals("secreta", env.getProperty("spring.datasource.password"));
    }

    @Test
    void elPuertoSaleDeLaVariablePORTYEnLocalEs8770() throws IOException {
        assertEquals("10000", entorno(Map.of("PORT", "10000")).getProperty("server.port"));
        assertEquals("8770", entorno(Map.of()).getProperty("server.port"));
    }
}
