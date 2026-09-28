package ke.ac.strathmore.fmls.module;

/** The Python ML module could not be reached or returned an error. Callers fall back gracefully. */
public class MlUnavailableException extends RuntimeException {
    public MlUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
