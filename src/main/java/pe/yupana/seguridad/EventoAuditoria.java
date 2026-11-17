package pe.yupana.seguridad;

import pe.yupana.dominio.Textos;

/**
 * Hecho relevante para la seguridad (ISO 27001 A.8.15 registro de eventos). Nunca lleva el contenido de los archivos
 * ni datos personales: solo qué pasó, con qué origen y cuántas filas.
 */
public record EventoAuditoria(Tipo tipo, String detalle) {

    private static final int DETALLE_MAXIMO = 400;

    public enum Tipo {
        CARGA_ACEPTADA, CARGA_RECHAZADA, CONCILIACION_EJECUTADA, DEMO_REINICIADA, REINICIO_DENEGADO, ERROR_INTERNO,
        INGRESO_EXITOSO, INGRESO_FALLIDO, ACCESO_DENEGADO, EMPRESA_CREADA, ACCESO_CREADO, CASO_REVISADO, SIRE_IMPORTADO,
        INGRESO_BLOQUEADO
    }

    public EventoAuditoria {
        String limpio = Textos.paraBitacora(detalle);
        detalle = limpio.length() > DETALLE_MAXIMO ? limpio.substring(0, DETALLE_MAXIMO) : limpio;
    }
}
