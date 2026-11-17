package pe.yupana.config;

import java.time.Clock;
import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import pe.yupana.dominio.Conciliador;
import pe.yupana.seguridad.LimitadorDeTasa;

/** Une el dominio puro (sin Spring) con la configuración externa. */
@Configuration
class ConfiguracionYupana {

    @Bean
    Conciliador conciliador(PropiedadesYupana propiedades) {
        return new Conciliador(propiedades.tolerancia());
    }

    /** Ventana de un minuto para el límite de peticiones por IP. */
    @Bean
    LimitadorDeTasa limitadorDeTasa(Clock reloj) {
        return new LimitadorDeTasa(Duration.ofMinutes(1), reloj);
    }

    /** El reloj es un componente inyectable: así el bloqueo de ingreso se prueba sin esperar minutos reales. */
    @Bean
    Clock reloj() {
        return Clock.systemUTC();
    }
}
