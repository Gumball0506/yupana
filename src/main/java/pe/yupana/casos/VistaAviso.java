package pe.yupana.casos;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Aviso del día (tarea 3.1-A): vista previa de lo que recibiría el contador. Nunca se envía. */
public record VistaAviso(long id, @JsonProperty("id_conciliacion") long idConciliacion, String fecha, String asunto, String cuerpo) {
}
