package io.github.ieshishinjin.splice.model;

import java.nio.file.Path;

/**
 * Represents a conflict or issue found during migration that
 * requires human attention to resolve.
 */
public class Conflict {

    public enum Severity {
        /** Information about automatic handling */
        INFO,
        /** Warning about something that might need attention */
        WARNING,
        /** Error that could not be automatically resolved */
        ERROR
    }

    public enum Category {
        /** Ambiguous mapping (one source -> multiple targets) */
        AMBIGUOUS_MAPPING,
        /** Removed class/method/field not present in target version */
        REMOVED_SYMBOL,
        /** Added class/method/field in target with no source equivalent */
        ADDED_SYMBOL,
        /** Syntax issue during source transformation */
        SYNTAX_ISSUE,
        /** Bytecode transformation issue */
        BYTECODE_ISSUE,
        /** Metadata update issue */
        METADATA_ISSUE,
        /** I/O error */
        IO_ERROR,
        /** Unsupported feature */
        UNSUPPORTED
    }

    private final Severity severity;
    private final Category category;
    private final String message;
    private final Path file;
    private final int lineNumber;
    private final String suggestion;

    public Conflict(Severity severity, Category category, String message,
                    Path file, int lineNumber, String suggestion) {
        this.severity = severity;
        this.category = category;
        this.message = message;
        this.file = file;
        this.lineNumber = lineNumber;
        this.suggestion = suggestion;
    }

    public Severity getSeverity() { return severity; }
    public Category getCategory() { return category; }
    public String getMessage() { return message; }
    public Path getFile() { return file; }
    public int getLineNumber() { return lineNumber; }
    public String getSuggestion() { return suggestion; }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append('[').append(severity).append("] ");
        sb.append('[').append(category).append("] ");
        sb.append(message);
        if (file != null) {
            sb.append(" at ").append(file);
            if (lineNumber > 0) {
                sb.append(':').append(lineNumber);
            }
        }
        if (suggestion != null && !suggestion.isEmpty()) {
            sb.append("\n    Suggestion: ").append(suggestion);
        }
        return sb.toString();
    }
}
