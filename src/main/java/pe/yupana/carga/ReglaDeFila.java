package pe.yupana.carga;

/** Una regla de validación. Las reglas forman una cadena: agregar una es agregar un método, no editar las demás. */
@FunctionalInterface
interface ReglaDeFila {

    void aplicar(FilaEnRevision fila, Hallazgos hallazgos);
}
