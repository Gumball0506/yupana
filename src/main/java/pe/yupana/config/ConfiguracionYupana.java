package pe.yupana.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import pe.yupana.dominio.Conciliador;

/** Une el dominio puro (sin Spring) con la configuración externa. */
@Configuration
class ConfiguracionYupana {

    @Bean
    Conciliador conciliador(PropiedadesYupana propiedades) {
        return new Conciliador(propiedades.tolerancia());
    }

    /** El reloj es un componente inyectable: así el bloqueo de ingreso se prueba sin esperar minutos reales. */
    @Bean
    Clock reloj() {
        return Clock.systemUTC();
    }
}
