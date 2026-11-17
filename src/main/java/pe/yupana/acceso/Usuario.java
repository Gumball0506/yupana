package pe.yupana.acceso;

import pe.yupana.dominio.Rol;

/** Cuenta de acceso. La clave solo existe cifrada (ver {@link pe.yupana.seguridad.Claves}). MYPE siempre tiene empresa. */
public record Usuario(String usuario, String claveCifrada, Rol rol, Long empresaId) {
}
