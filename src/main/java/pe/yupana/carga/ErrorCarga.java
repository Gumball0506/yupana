package pe.yupana.carga;

import java.util.List;

/** Error del usuario al cargar: se muestra tal cual en pantalla, con la celda exacta cuando se conoce. */
public class ErrorCarga extends RuntimeException {

    private final List<String> errores;
    private final List<Celda> celdas;
    private final VistaRechazo vista;

    public ErrorCarga(List<String> errores, List<Celda> celdas, VistaRechazo vista) {
        super(String.join(" ", errores));
        this.errores = List.copyOf(errores);
        this.celdas = List.copyOf(celdas);
        this.vista = vista;
    }

    public ErrorCarga(String error) {
        this(List.of(error), List.of(), null);
    }

    public List<String> errores() { return errores; }

    public List<Celda> celdas() { return celdas; }

    /** Filas del archivo rechazado; null cuando no hay nada que mostrar. */
    public VistaRechazo vista() { return vista; }
}
