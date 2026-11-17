package pe.yupana.seguridad;

/** La operación existe pero está deshabilitada por configuración (ISO 27001 A.8.3 restricción de acceso). */
public class AccesoDenegado extends RuntimeException {

    public AccesoDenegado(String mensaje) {
        super(mensaje);
    }
}
