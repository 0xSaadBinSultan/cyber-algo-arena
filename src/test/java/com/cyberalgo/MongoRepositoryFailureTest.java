package com.cyberalgo;

import com.mongodb.MongoException;
import com.mongodb.client.MongoCollection;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MongoRepositoryFailureTest {
    @Test
    @SuppressWarnings("unchecked")
    void failedWritesAreNotAcknowledgedOrCached() {
        MongoManager manager = mock(MongoManager.class);
        when(manager.isConnected()).thenReturn(true);
        MongoCollection<Document> users = mock(MongoCollection.class);
        when(manager.getUsersCollection()).thenReturn(users);
        when(users.replaceOne(any(org.bson.conversions.Bson.class), any(Document.class),
                any(com.mongodb.client.model.ReplaceOptions.class)))
                .thenThrow(new MongoException("credential-sentinel must never escape"));
        MongoRepository repo = new MongoRepository(manager, false);
        User user = new User("user", "tester", null, "hash", User.Role.PLAYER, null);
        DatabaseUnavailableException failure = assertThrows(DatabaseUnavailableException.class, () -> repo.saveUser(user));
        assertFalse(failure.toString().contains("credential-sentinel"));
        assertNull(failure.getCause());
        when(manager.isConnected()).thenReturn(false);
        when(manager.ping()).thenReturn(false);
        assertThrows(DatabaseUnavailableException.class, () -> repo.getUserById("user"));
    }
}
