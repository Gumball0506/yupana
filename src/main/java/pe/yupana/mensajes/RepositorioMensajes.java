package pe.yupana.mensajes;

import java.util.List;
import java.util.Optional;

/** Puerto de salida del chat simulado. */
public interface RepositorioMensajes {

    long guardar(long empresaId, String direccion, String texto, String fecha, String usuario);

    /** La conversación de la empresa, la más antigua primero. */
    List<VistaMensaje> listar(long empresaId);

    Optional<String> ultimaFecha(long empresaId);

    void borrarDeEmpresa(long empresaId);
}
