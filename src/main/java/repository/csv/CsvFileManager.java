package repository.csv;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;

/** Reads and writes header-validated UTF-8 CSV files for repository records. */
public class CsvFileManager {

    /** Reads records, creating a header-only file when the runtime file is absent. */
    public List<Map<String, String>> readRecords(Path file, List<String> expectedHeaders) {
        validateHeaders(file, expectedHeaders);
        if (Files.notExists(file)) {
            writeRecords(file, expectedHeaders, List.of());
            return List.of();
        }

        final String contents;
        try {
            contents = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new CsvDataException(file, "could not read CSV data", ex);
        }

        List<List<String>> rows = parse(file, contents);
        if (rows.isEmpty()) {
            throw new CsvDataException(file, "missing CSV header");
        }

        List<String> actualHeaders = new ArrayList<>(rows.get(0));
        if (!actualHeaders.isEmpty() && actualHeaders.get(0).startsWith("\uFEFF")) {
            actualHeaders.set(0, actualHeaders.get(0).substring(1));
        }
        if (!expectedHeaders.equals(actualHeaders)) {
            throw new CsvDataException(file,
                    "unexpected CSV header; expected " + String.join(",", expectedHeaders));
        }

        List<Map<String, String>> records = new ArrayList<>();
        for (int rowIndex = 1; rowIndex < rows.size(); rowIndex++) {
            List<String> values = rows.get(rowIndex);
            if (values.size() == 1 && values.get(0).isEmpty()) {
                continue;
            }
            int recordNumber = records.size() + 1;
            if (values.size() != expectedHeaders.size()) {
                throw new CsvDataException(file, recordNumber,
                        "expected " + expectedHeaders.size() + " fields but found " + values.size(), null);
            }

            Map<String, String> record = new LinkedHashMap<>();
            for (int column = 0; column < expectedHeaders.size(); column++) {
                record.put(expectedHeaders.get(column), values.get(column));
            }
            records.add(record);
        }
        return List.copyOf(records);
    }

    /** Replaces one CSV file with the supplied records using a temporary sibling file. */
    public void writeRecords(
            Path file,
            List<String> headers,
            List<Map<String, String>> records) {
        validateHeaders(file, headers);
        Objects.requireNonNull(records, "records must not be null");

        Path absoluteFile = file.toAbsolutePath();
        Path parent = absoluteFile.getParent();
        Path temporaryFile = null;
        try {
            Files.createDirectories(parent);
            temporaryFile = Files.createTempFile(parent, absoluteFile.getFileName().toString(), ".tmp");
            StringBuilder contents = new StringBuilder();
            contents.append(encodeRow(headers)).append("\r\n");
            for (Map<String, String> record : records) {
                Objects.requireNonNull(record, "record must not be null");
                List<String> values = new ArrayList<>(headers.size());
                for (String header : headers) {
                    values.add(record.get(header));
                }
                contents.append(encodeRow(values)).append("\r\n");
            }
            Files.writeString(temporaryFile, contents, StandardCharsets.UTF_8);
            try {
                Files.move(temporaryFile, absoluteFile,
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temporaryFile, absoluteFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            throw new CsvDataException(file, "could not write CSV data", ex);
        } finally {
            if (temporaryFile != null) {
                try {
                    Files.deleteIfExists(temporaryFile);
                } catch (IOException ignored) {
                    // A successful move removes the temporary file; cleanup is best-effort on failure.
                }
            }
        }
    }

    private static void validateHeaders(Path file, List<String> headers) {
        Objects.requireNonNull(file, "file must not be null");
        Objects.requireNonNull(headers, "headers must not be null");
        if (headers.isEmpty() || headers.stream().anyMatch(header -> header == null || header.isBlank())) {
            throw new CsvDataException(file, "expected CSV headers must not be empty");
        }
        if (headers.stream().distinct().count() != headers.size()) {
            throw new CsvDataException(file, "expected CSV headers must be unique");
        }
    }

    private static String encodeRow(List<String> values) {
        StringJoiner row = new StringJoiner(",");
        for (String value : values) {
            String safeValue = value == null ? "" : value;
            if (safeValue.contains(",") || safeValue.contains("\"")
                    || safeValue.contains("\r") || safeValue.contains("\n")) {
                row.add("\"" + safeValue.replace("\"", "\"\"") + "\"");
            } else {
                row.add(safeValue);
            }
        }
        return row.toString();
    }

    private static List<List<String>> parse(Path file, String contents) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        boolean quoteClosed = false;

        for (int index = 0; index < contents.length(); index++) {
            char current = contents.charAt(index);
            if (inQuotes) {
                if (current == '"') {
                    if (index + 1 < contents.length() && contents.charAt(index + 1) == '"') {
                        field.append('"');
                        index++;
                    } else {
                        inQuotes = false;
                        quoteClosed = true;
                    }
                } else {
                    field.append(current);
                }
                continue;
            }

            if (quoteClosed && current != ',' && current != '\r' && current != '\n') {
                throw new CsvDataException(file, rows.size(),
                        "unexpected character after closing quote", null);
            }
            if (current == '"') {
                if (field.length() != 0 || quoteClosed) {
                    throw new CsvDataException(file, rows.size(), "quote inside an unquoted field", null);
                }
                inQuotes = true;
            } else if (current == ',') {
                row.add(field.toString());
                field.setLength(0);
                quoteClosed = false;
            } else if (current == '\r' || current == '\n') {
                if (current == '\r' && index + 1 < contents.length()
                        && contents.charAt(index + 1) == '\n') {
                    index++;
                }
                row.add(field.toString());
                rows.add(row);
                row = new ArrayList<>();
                field.setLength(0);
                quoteClosed = false;
            } else {
                field.append(current);
            }
        }

        if (inQuotes) {
            throw new CsvDataException(file, rows.size(), "unterminated quoted field", null);
        }
        if (field.length() > 0 || !row.isEmpty() || quoteClosed
                || (!contents.isEmpty() && contents.charAt(contents.length() - 1) == ',')) {
            row.add(field.toString());
            rows.add(row);
        }
        return rows;
    }
}
