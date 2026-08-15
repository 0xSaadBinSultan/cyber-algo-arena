import java.util.ArrayList;
import java.util.List;

/** Shared RFC-4180-style CSV encoding and single-record parsing. */
final class CsvCodec {

    private CsvCodec() {
    }

    static String join(Iterable<? extends String> fields) {
        StringBuilder row = new StringBuilder();
        boolean first = true;
        for (String field : fields) {
            if (!first) {
                row.append(',');
            }
            row.append(escape(field));
            first = false;
        }
        return row.toString();
    }

    static String escape(String value) {
        String safeValue = value == null ? "" : value;
        boolean requiresQuotes = safeValue.indexOf(',') >= 0
                || safeValue.indexOf('"') >= 0
                || safeValue.indexOf('\n') >= 0
                || safeValue.indexOf('\r') >= 0;
        return requiresQuotes ? '"' + safeValue.replace("\"", "\"\"") + '"' : safeValue;
    }

    static List<String> parseLine(String line, int lineNumber) throws CorruptedFileException {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        boolean quoteClosed = false;

        for (int i = 0; i < line.length(); i++) {
            char currentChar = line.charAt(i);

            if (inQuotes) {
                if (currentChar == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        current.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                        quoteClosed = true;
                    }
                } else {
                    current.append(currentChar);
                }
                continue;
            }

            if (currentChar == ',') {
                fields.add(current.toString());
                current.setLength(0);
                quoteClosed = false;
            } else if (currentChar == '"' && current.length() == 0 && !quoteClosed) {
                inQuotes = true;
            } else {
                if (quoteClosed) {
                    throw new CorruptedFileException("Unexpected character after closing quote at line " + lineNumber);
                }
                current.append(currentChar);
            }
        }

        if (inQuotes) {
            throw new CorruptedFileException("Unterminated quoted CSV field at line " + lineNumber);
        }
        fields.add(current.toString());
        return fields;
    }
}
