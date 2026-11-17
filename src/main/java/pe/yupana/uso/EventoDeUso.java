package pe.yupana.uso;

import java.util.Optional;
import java.util.Set;

/**
 * Evento anónimo de uso (Marketing 5.0). Solo se aceptan valores cortos y conocidos: nunca datos personales
 * ni contenido de archivos (Ley 29733; ISO 27001 A.5.34).
 */
public record EventoDeUso(String sesion, String tipo, String detalle) {

    static final Set<String> TIPOS = Set.of("demo_abierta", "carga_iniciada", "carga_exitosa", "carga_fallida",
            "ejemplos_usados", "plantilla_descargada", "historial_visto", "demo_reiniciada", "panel_visto", "resultado_exportado",
            "accion_caso", "aviso_simulado", "feedback_recibido", "ensayo_registrado", "landing_vista", "cta_probar_demo",
            "login", "empresa_creada", "sire_propuesta", "sire_importada", "whatsapp_enviado", "wa_me_abierto");

    /** Devuelve el evento solo si cumple las reglas; vacío si no. */
    public static Optional<EventoDeUso> validar(String sesion, String tipo, String detalle) {
        boolean sesionValida = sesion != null && sesion.matches("[a-f0-9-]{8,36}");
        boolean tipoValido = tipo != null && TIPOS.contains(tipo);
        boolean detalleValido = detalle == null || detalle.matches("[a-z0-9_]{0,40}");
        return sesionValida && tipoValido && detalleValido ? Optional.of(new EventoDeUso(sesion, tipo, detalle)) : Optional.empty();
    }
}
