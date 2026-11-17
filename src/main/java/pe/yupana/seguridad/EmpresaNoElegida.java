package pe.yupana.seguridad;

/** El estudio pidió datos de una empresa sin haber elegido cuál en el panel. */
public class EmpresaNoElegida extends RuntimeException {

    public EmpresaNoElegida() {
        super("Elige una empresa en el panel.");
    }
}
