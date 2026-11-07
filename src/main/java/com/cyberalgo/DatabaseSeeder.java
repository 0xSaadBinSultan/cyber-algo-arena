package com.cyberalgo;

/** Idempotent installation of complete original practice material. */
public final class DatabaseSeeder {
    private DatabaseSeeder() {}
    public static void seedIfEmpty(MongoRepository repository) {
        PracticeCatalog.ensureInstalled(repository);
    }
}
