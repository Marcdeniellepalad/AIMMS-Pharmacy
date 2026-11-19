package repository.csv;

import java.nio.file.Path;

/** Reports a CSV formatting or record conversion error with its source location. */
public class CsvDataException extends RuntimeException {

    private final Path file;
    private final int recordNumber;

    public CsvDataException(Path file, String message) {
        this(file, 0, message, null);
    }

    public CsvDataException(Path file, String message, Throwable cause) {
        this(file, 0, message, cause);
    }

    public CsvDataException(Path file, int recordNumber, String message, Throwable cause) {
        super(formatMessage(file, recordNumber, message), cause);
        this.file = file;
        this.recordNumber = recordNumber;
    }

    public Path getFile() {
        return file;
    }

    /** Returns the one-based data record number, or zero for a file-level error. */
    public int getRecordNumber() {
        return recordNumber;
    }

    private static String formatMessage(Path file, int recordNumber, String message) {
        String location = file == null ? "CSV file" : file.toString();
        if (recordNumber > 0) {
            location += " (record " + recordNumber + ")";
        }
        return location + ": " + message;
    }
}
