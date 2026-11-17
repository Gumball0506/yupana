package pe.yupana.mensajes;

import java.util.List;

/** Lo que pinta la pantalla del chat: el teléfono para el enlace wa.me (puede ser null) y la conversación. */
public record ChatEmpresa(String telefono, List<VistaMensaje> mensajes) {
}
