package pe.yupana.dominio;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Formato de celdas CSV compartido por todas las exportaciones. */
public final class Csv {

    /** Marca de orden de bytes: Excel la necesita para respetar las tildes en UTF-8. */
    public static final String BOM = "﻿";
    public static final String SALTO = "\r\n";

    private Csv() {}

    /** Comillas si hace falta; un apóstrofo delante de =, +, - o @ para que Excel no lo ejecute como fórmula. */
    public static String celda(Object valor) {
        String texto = valor == null ? "" : valor instanceof BigDecimal b ? b.setScale(2, RoundingMode.HALF_UP).toPlainString() : valor.toString();
        if (!(valor instanceof Number) && texto.matches("[=+@-].*")) texto = "'" + texto;
        return texto.matches("(?s).*[,\"\r\n].*") ? '"' + texto.replace("\"", "\"\"") + '"' : texto;
    }
}
