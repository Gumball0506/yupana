package pe.yupana.carga;

import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Component;

import pe.yupana.config.PropiedadesYupana;

/** Factory Method: elige el lector según la extensión. Un formato nuevo = una entrada más, sin tocar el servicio. */
@Component
public class FabricaLectores {

    private final Map<String, LectorArchivo> porExtension;

    public FabricaLectores(PropiedadesYupana propiedades) {
        this.porExtension = Map.of(
                ".csv", new LectorCsv(propiedades.maxFilas()),
                ".xlsx", new LectorXlsx(propiedades.maxFilas()));
    }

    public LectorArchivo para(String nombreArchivo) {
        int punto = nombreArchivo.lastIndexOf('.');
        String extension = punto < 0 ? "" : nombreArchivo.substring(punto).toLowerCase(Locale.ROOT);
        LectorArchivo lector = porExtension.get(extension);
        if (lector == null)
            throw new ErrorCarga("Formato '" + (extension.isEmpty() ? "sin extensión" : "." + soloLetras(extension))
                    + "' no admitido. Usa CSV o XLSX.");
        return lector;
    }

    /** La extensión viene del usuario: se repite en el mensaje solo si es inofensiva. */
    private static String soloLetras(String extension) {
        String limpia = extension.substring(1).replaceAll("[^a-z0-9]", "");
        return limpia.length() > 10 ? limpia.substring(0, 10) : limpia;
    }
}
