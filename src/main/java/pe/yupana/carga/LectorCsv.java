package pe.yupana.carga;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Lee CSV en UTF-8 con coma o punto y coma (Excel en español guarda con «;»). */
public class LectorCsv implements LectorArchivo {

    private static final String BOM = "﻿";

    private final int maxFilas;

    public LectorCsv(int maxFilas) {
        this.maxFilas = maxFilas;
    }

    @Override
    public List<Fila> leer(byte[] contenido) {
        String texto = decodificar(contenido);
        if (texto.startsWith(BOM)) texto = texto.substring(1);
        if (texto.isBlank()) throw new ErrorCarga("El archivo está vacío.");

        String[] lineas = texto.split("\\r?\\n", -1);
        if (lineas.length > maxFilas + 2) throw new ErrorCarga("El archivo supera el máximo de " + maxFilas + " filas.");
        char separador = elegirSeparador(lineas[0]);
        List<Fila> filas = new ArrayList<>();
        for (int i = 0; i < lineas.length; i++) filas.add(new Fila(i + 1, partir(lineas[i], separador)));
        return filas;
    }

    private static String decodificar(byte[] contenido) {
        try {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(contenido)).toString();
        } catch (CharacterCodingException e) {
            throw new ErrorCarga("El CSV no está en UTF-8. Guárdalo como 'CSV UTF-8' e inténtalo de nuevo.");
        }
    }

    private static char elegirSeparador(String encabezado) {
        long puntoYComa = encabezado.chars().filter(c -> c == ';').count();
        long comas = encabezado.chars().filter(c -> c == ',').count();
        return puntoYComa > comas ? ';' : ',';
    }

    /** Separa una línea respetando comillas: "a,b" es un solo campo. */
    static List<String> partir(String linea, char separador) {
        List<String> campos = new ArrayList<>();
        StringBuilder actual = new StringBuilder();
        boolean entreComillas = false;
        for (int i = 0; i < linea.length(); i++) {
            char c = linea.charAt(i);
            if (c == '"') {
                if (entreComillas && i + 1 < linea.length() && linea.charAt(i + 1) == '"') {
                    actual.append('"');
                    i++;
                } else {
                    entreComillas = !entreComillas;
                }
            } else if (c == separador && !entreComillas) {
                campos.add(actual.toString());
                actual.setLength(0);
            } else {
                actual.append(c);
            }
        }
        campos.add(actual.toString());
        return campos;
    }
}
