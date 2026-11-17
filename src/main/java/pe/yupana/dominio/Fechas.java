package pe.yupana.dominio;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/** Fechas como las guarda y muestra la demo: ISO al segundo y «27 oct 2026, 10:48» en textos. */
public final class Fechas {

    private static final String[] MESES = {"ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "set", "oct", "nov", "dic"};

    private Fechas() {}

    public static String ahora() {
        return LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS).toString();
    }

    public static String enTexto(String iso) {
        LocalDateTime t = LocalDateTime.parse(iso);
        return t.getDayOfMonth() + " " + MESES[t.getMonthValue() - 1] + " " + t.getYear() + ", " + iso.substring(11, 16);
    }
}
