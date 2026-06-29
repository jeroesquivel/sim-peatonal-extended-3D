package ar.edu.itba.simped.core.validation;

public record ValidationError(ValidationCode code, String location, String detail) {

    @Override
    public String toString() {
        return code.name() + " @ " + location + ": " + detail;
    }
}
