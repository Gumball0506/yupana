package pe.yupana.dominio;

/**
 * RUC de una empresa cliente como objeto de valor: 11 dígitos y dígito verificador módulo 11 (pesos de SUNAT).
 * Regla del curso: solo se aceptan RUC ficticios con el prefijo 209999100, y con él existen exactamente 10 válidos.
 */
public record Ruc(String valor) {

    public static final String PREFIJO_FICTICIO = "209999100";
    private static final int[] PESOS = {5, 4, 3, 2, 7, 6, 5, 4, 3, 2};

    public Ruc {
        if (valor == null || !valor.matches("\\d{11}")) throw new ReglaIncumplida("El RUC debe tener 11 dígitos.");
        if (!valor.startsWith(PREFIJO_FICTICIO))
            throw new ReglaIncumplida("Solo se aceptan RUC ficticios que empiecen con " + PREFIJO_FICTICIO + " (regla de la demo).");
        int esperado = digitoVerificador(valor.substring(0, 10));
        if (esperado != valor.charAt(10) - '0')
            throw new ReglaIncumplida("Dígito verificador incorrecto: para " + valor.substring(0, 10) + " debe ser " + esperado + ".");
    }

    /** 11 − (Σ dígito × peso mód 11); el 10 se vuelve 0 y el 11 se vuelve 1. */
    public static int digitoVerificador(String diezDigitos) {
        int suma = 0;
        for (int i = 0; i < PESOS.length; i++) suma += (diezDigitos.charAt(i) - '0') * PESOS[i];
        int resultado = 11 - suma % 11;
        return resultado == 10 ? 0 : resultado == 11 ? 1 : resultado;
    }
}
