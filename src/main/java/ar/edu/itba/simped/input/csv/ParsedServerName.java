package ar.edu.itba.simped.input.csv;

import java.util.OptionalInt;

public record ParsedServerName(String base, int id, ServerKind kind, OptionalInt queueIndex) {
}
