package pe.yupana.config;

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
}
