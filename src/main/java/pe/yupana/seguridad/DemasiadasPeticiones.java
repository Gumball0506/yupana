package pe.yupana.seguridad;

/** Se superó el límite de peticiones por minuto desde una misma IP. */
public class DemasiadasPeticiones extends RuntimeException {

    private final long segundosRestantes;

    public DemasiadasPeticiones(long segundosRestantes) {
        super("Demasiadas peticiones seguidas. Espera " + segundosRestantes + " segundo(s) e inténtalo de nuevo.");
        this.segundosRestantes = segundosRestantes;
    }

    public long segundosRestantes() {
        return segundosRestantes;
    }
}
