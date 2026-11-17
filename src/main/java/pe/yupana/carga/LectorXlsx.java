package pe.yupana.carga;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

/** Lee la primera hoja de un .xlsx. Nunca evalúa fórmulas: usa el último valor guardado en la celda. */
public class LectorXlsx implements LectorArchivo {

    private static final int FIRMA_ZIP_0 = 'P';
    private static final int FIRMA_ZIP_1 = 'K';

    private final int maxFilas;

    public LectorXlsx(int maxFilas) {
        this.maxFilas = maxFilas;
    }

    @Override
    public List<Fila> leer(byte[] contenido) {
        exigirFirmaZip(contenido);
        try (Workbook libro = WorkbookFactory.create(new ByteArrayInputStream(contenido))) {
            Sheet hoja = libro.getSheetAt(0);
            DataFormatter formato = new DataFormatter();
            List<Fila> filas = new ArrayList<>();
            for (Row fila : hoja) {
                if (filas.size() >= maxFilas + 1) throw new ErrorCarga("El archivo supera el máximo de " + maxFilas + " filas.");
                List<String> celdas = new ArrayList<>();
                for (int c = 0; c < fila.getLastCellNum(); c++) celdas.add(aTexto(fila.getCell(c), formato));
                filas.add(new Fila(fila.getRowNum() + 1, celdas));
            }
            return filas;
        } catch (ErrorCarga e) {
            throw e;
        } catch (Exception e) {
            throw new ErrorCarga("No se pudo abrir el Excel. Verifica que sea un .xlsx válido.");
        }
    }

    /** Un .xlsx es un ZIP: se descarta antes de abrirlo cualquier archivo con otra firma (extensión falsa). */
    private static void exigirFirmaZip(byte[] contenido) {
        boolean esZip = contenido.length > 1 && contenido[0] == FIRMA_ZIP_0 && contenido[1] == FIRMA_ZIP_1;
        if (!esZip) throw new ErrorCarga("No se pudo abrir el Excel. Verifica que sea un .xlsx válido.");
    }

    static String aTexto(Cell celda, DataFormatter formato) {
        if (celda == null) return "";
        CellType tipo = celda.getCellType() == CellType.FORMULA ? celda.getCachedFormulaResultType() : celda.getCellType();
        return switch (tipo) {
            case STRING -> celda.getStringCellValue();
            case NUMERIC -> numeroATexto(celda, formato);
            case BOOLEAN -> String.valueOf(celda.getBooleanCellValue());
            default -> "";
        };
    }

    private static String numeroATexto(Cell celda, DataFormatter formato) {
        if (DateUtil.isCellDateFormatted(celda)) return celda.getLocalDateTimeCellValue().toLocalDate().toString();
        // Con formato «000001» se respeta lo que se ve (ceros a la izquierda)
        String visto = formato.formatCellValue(celda);
        return visto.matches("\\d+") ? visto : BigDecimal.valueOf(celda.getNumericCellValue()).stripTrailingZeros().toPlainString();
    }
}
