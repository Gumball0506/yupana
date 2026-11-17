package pe.yupana.seguridad;

/** No hay sesión iniciada, o los datos de acceso no son correctos. */
public class SinSesion extends RuntimeException {

    public SinSesion(String mensaje) {
        super(mensaje);
    }
}
