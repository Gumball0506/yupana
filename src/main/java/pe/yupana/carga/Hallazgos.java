package pe.yupana.carga;

import java.util.ArrayList;
import java.util.List;

/** Acumula lo que encuentran las reglas: errores que bloquean, celdas señaladas y advertencias que no bloquean. */
public class Hallazgos {

    private final List<String> errores = new ArrayList<>();
    private final List<Celda> celdas = new ArrayList<>();
    private final List<String> advertencias = new ArrayList<>();

    void errorEn(int fila, String columna, String mensaje) {
        errores.add("Fila " + fila + ", columna " + columna + ": " + mensaje);
        celdas.add(new Celda(fila, columna));
    }

    void errorDeFila(int fila, String mensaje) {
        errores.add(mensaje);
        celdas.add(new Celda(fila, null));
    }

    void advertir(String mensaje) {
        advertencias.add(mensaje);
    }

    int cantidadErrores() { return errores.size(); }

    boolean hayErrores() { return !errores.isEmpty(); }

    List<String> errores() { return errores; }

    List<Celda> celdas() { return celdas; }

    List<String> advertencias() { return advertencias; }
}
