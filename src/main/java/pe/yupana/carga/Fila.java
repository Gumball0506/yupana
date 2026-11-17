package pe.yupana.carga;

import java.util.List;

/** Fila leída del archivo, con su número tal como se ve en Excel. */
public record Fila(int n, List<String> celdas) {

    boolean estaVacia() {
        return celdas.stream().allMatch(String::isBlank);
    }
}
