package pe.yupana.config;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Con el perfil «produccion» el arranque FALLA si faltan las garantías mínimas (ISO 27001 A.8.9 configuración segura,
 * A.5.17 información de autenticación): una clave de demostración conocida nunca debe llegar a un servidor real.
 */
@Component
@Profile("produccion")
class ExigenciasDeProduccion {

    /** Clave de demostración de application.properties; es pública, por eso está prohibida en producción. */
    static final String CLAVE_DE_DEMOSTRACION = "Yupana2026*";

    ExigenciasDeProduccion(PropiedadesYupana propiedades) {
        String clave = propiedades.semillaClave();
        if (clave == null || clave.isBlank())
            throw new IllegalStateException("Perfil «produccion»: falta la variable de entorno YUPANA_SEED_CLAVE. "
                    + "Define una clave propia (mínimo 12 caracteres) para los usuarios iniciales.");
        if (CLAVE_DE_DEMOSTRACION.equals(clave))
            throw new IllegalStateException("Perfil «produccion»: YUPANA_SEED_CLAVE no puede ser la clave de demostración. "
                    + "Define una clave propia (mínimo 12 caracteres).");
        if (clave.length() < 12)
            throw new IllegalStateException("Perfil «produccion»: YUPANA_SEED_CLAVE debe tener al menos 12 caracteres.");
    }
}
