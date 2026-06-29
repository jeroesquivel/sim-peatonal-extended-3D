package ar.edu.itba.simped.input;

import ar.edu.itba.simped.core.validation.ValidationCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class FormatDetectorTest {

    @Test
    void detectsFormatBWhenOnlyParametersJsonPresent(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("parameters.json"), "{}");

        ErrorAccumulator acc = new ErrorAccumulator();
        FormatDetector.Format f = FormatDetector.detect(dir, acc);

        assertThat(f).isEqualTo(FormatDetector.Format.FORMAT_B);
        assertThat(acc.hasErrors()).isFalse();
    }

    @Test
    void detectsFormatAWhenAnyFormatACsvPresent(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("SIM_PARAMS.csv"), "key,value\n");

        ErrorAccumulator acc = new ErrorAccumulator();
        FormatDetector.Format f = FormatDetector.detect(dir, acc);

        assertThat(f).isEqualTo(FormatDetector.Format.FORMAT_A);
        assertThat(acc.hasErrors()).isFalse();
    }

    @Test
    void defaultsToFormatAWhenEmptyDir(@TempDir Path dir) {
        ErrorAccumulator acc = new ErrorAccumulator();
        FormatDetector.Format f = FormatDetector.detect(dir, acc);
        assertThat(f).isEqualTo(FormatDetector.Format.FORMAT_A);
    }

    @Test
    void firesV20WhenBothFormatsPresent(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("parameters.json"), "{}");
        Files.writeString(dir.resolve("PLANS.csv"), "template,step_order,target_type,target_block_name\n");

        ErrorAccumulator acc = new ErrorAccumulator();
        FormatDetector.Format f = FormatDetector.detect(dir, acc);

        assertThat(f).isEqualTo(FormatDetector.Format.FORMAT_A);
        assertThat(acc.errors())
                .extracting(e -> e.code())
                .containsExactly(ValidationCode.V20);
    }
}
