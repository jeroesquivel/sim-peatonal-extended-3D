package ar.edu.itba.simped.input.csv;

import java.util.ArrayList;
import java.util.List;

public final class CsvLine {

    private CsvLine() {
    }

    public static List<String> parse(String line) {
        if (line == null) {
            throw new IllegalArgumentException("line is null");
        }
        String[] parts = line.split(",", -1);
        List<String> tokens = new ArrayList<>(parts.length);
        for (String p : parts) {
            tokens.add(p.trim());
        }
        return tokens;
    }
}
