package pe.yupana.config;

import java.math.BigDecimal;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Parámetros del negocio y de seguridad, todos en application.properties (ISO 27001 A.8.9: configuración
 * gestionada fuera del código). Prefijo «yupana.».
 *
 * @param tolerancia          diferencia en soles que se acepta como redondeo
 * @param maxFilas            tope de filas por archivo (evita agotar memoria con un archivo hostil)
 * @param reinicioHabilitado  si «Reiniciar demo» borra datos; en un servidor público debe estar en false
 * @param semillaClave        clave de los usuarios de demostración que se crean con la base vacía (variable YUPANA_SEED_CLAVE)
 * @param login               bloqueo temporal tras varios ingresos fallidos, por usuario y por IP (yupana.login.*)
 * @param semillaDatos        si además se cargan y concilian los archivos de ejemplo en la empresa A
 * @param limite              peticiones por minuto y por IP en el ingreso y en la carga de archivos (yupana.limite.*)
 * @param hsts                si las respuestas llevan Strict-Transport-Security (solo cuando se sirve por HTTPS)
 */
@ConfigurationProperties(prefix = "yupana")
public record PropiedadesYupana(
        @DefaultValue("0.01") BigDecimal tolerancia,
        @DefaultValue("5000") int maxFilas,
        @DefaultValue("true") boolean reinicioHabilitado,
        String semillaClave,
        @DefaultValue("false") boolean semillaDatos,
        @DefaultValue("false") boolean hsts,
        @DefaultValue Login login,
        @DefaultValue Limite limite) {

    /**
     * Bloqueo de ingreso (ISO 27001 A.8.5): tras {@code maxIntentos} fallos seguidos se bloquea {@code bloqueoMinutos}.
     *
     * @param maxIntentos    ingresos fallidos seguidos que disparan el bloqueo
     * @param bloqueoMinutos minutos que dura el bloqueo (y la ventana en que se cuentan los fallos)
     */
    /**
     * Límite de peticiones por IP en una ventana de un minuto (ISO 27001 A.8.6).
     *
     * @param loginPorMinuto peticiones a /api/login y /api/clave por minuto
     * @param cargaPorMinuto cargas de archivos por minuto
     */
    public record Limite(@DefaultValue("20") int loginPorMinuto, @DefaultValue("30") int cargaPorMinuto) {
    }

    public record Login(@DefaultValue("5") int maxIntentos, @DefaultValue("15") int bloqueoMinutos) {
    }
}
