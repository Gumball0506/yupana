package pe.yupana.seguridad;

/** El ingreso está bloqueado temporalmente por demasiados intentos fallidos. */
public class DemasiadosIntentos extends RuntimeException {

    private final long segundosRestantes;

    public DemasiadosIntentos(long segundosRestantes) {
        super("Demasiados intentos fallidos. Vuelve a intentar en " + Math.max(1, (segundosRestantes + 59) / 60) + " minuto(s).");
        this.segundosRestantes = segundosRestantes;
    }

    public long segundosRestantes() {
        return segundosRestantes;
    }
}
