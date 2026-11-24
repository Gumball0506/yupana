package pe.yupana.carga;

public final class PeriodoCarga {

    private PeriodoCarga() {}

    public static void exigirSiCorresponde(String periodo, boolean obligatorio) {
        if (obligatorio && periodo == null) throw new ErrorCarga("Selecciona el periodo tributario.");
    }

    public static String validar(String periodo) {
        if (periodo == null) return null;
        if (!periodo.matches("[1-9][0-9]{3}-(0[1-9]|1[0-2])"))
            throw new ErrorCarga("El periodo debe tener el formato AAAA-MM.");
        return periodo;
    }
}
