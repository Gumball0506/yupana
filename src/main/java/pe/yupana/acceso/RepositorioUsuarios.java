package pe.yupana.acceso;

import java.util.List;
import java.util.Optional;

/** Puerto de salida de las cuentas de acceso. */
public interface RepositorioUsuarios {

    Optional<Usuario> buscar(String usuario);

    boolean existe(String usuario);

    /** Reemplaza el hash de la clave del usuario. */
    void cambiarClave(String usuario, String claveCifrada);

    void crear(Usuario usuario);

    /** Nombres de usuario de la MYPE de esa empresa. */
    List<String> deEmpresa(long empresaId);
}
