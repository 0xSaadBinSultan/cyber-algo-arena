package com.cyberalgo;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import com.mongodb.client.model.Filters;

/** Explicit operational check: only temporary challenge records are written and removed. */
public final class MongoPersistenceCheck {
    private MongoPersistenceCheck() { }

    public static void run() {
        if (System.getenv("MONGODB_URI") == null || System.getenv("MONGODB_URI").isBlank()) {
            throw new DatabaseUnavailableException();
        }
        String id = "persistence-check-" + UUID.randomUUID();
        boolean attemptedWrite = false;
        try {
            try (MongoManager manager = new MongoManager()) {
                if (!manager.ping()) throw new DatabaseUnavailableException();
                MongoRepository repo = new MongoRepository(manager, false);
                CPProblem problem = new CPProblem(id, "Temporary persistence check", 100,
                        Challenge.Difficulty.EASY, 1000, 256, Path.of("unused"),
                        List.of(CPTestCase.sample("sample", "sample-output"),
                                CPTestCase.hidden("private-input", "private-output")));
                attemptedWrite = true;
                repo.saveChallenge(problem);
                if (repo.getChallengeById(id).isEmpty()) throw new DatabaseUnavailableException();
                System.out.println("[PersistenceCheck] Mongo ping and write/read passed.");
            }
            try (MongoManager restarted = new MongoManager()) {
                if (!restarted.ping()) throw new DatabaseUnavailableException();
                CPProblem loaded = (CPProblem) new MongoRepository(restarted, false).getChallengeById(id).orElseThrow();
                if (!loaded.getConfiguredTestCases().equals(List.of(CPTestCase.sample("sample", "sample-output"),
                        CPTestCase.hidden("private-input", "private-output")))) throw new DatabaseUnavailableException();
                System.out.println("[PersistenceCheck] Hidden tests survived Mongo client/repository reinitialization.");
            }
        } finally {
            if (attemptedWrite) {
                try (MongoManager cleanup = new MongoManager()) {
                    if (!cleanup.ping()) throw new DatabaseUnavailableException();
                    cleanup.getChallengesCollection().deleteOne(Filters.eq("id", id));
                    if (cleanup.getChallengesCollection().countDocuments(Filters.eq("id", id)) != 0) {
                        throw new DatabaseUnavailableException();
                    }
                    System.out.println("[PersistenceCheck] Temporary record deleted and absence verified.");
                } catch (Exception ignored) {
                    System.err.println("[PersistenceCheck] Cleanup failed; remove the temporary persistence-check record before retrying.");
                    throw new DatabaseUnavailableException();
                }
            }
        }
    }
}
