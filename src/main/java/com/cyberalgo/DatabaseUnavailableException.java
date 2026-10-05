package com.cyberalgo;

/** Deliberately excludes driver messages, settings and exception causes. */
public final class DatabaseUnavailableException extends RuntimeException {
    public DatabaseUnavailableException() {
        super("Persistent database unavailable. Please retry later.");
    }
}
