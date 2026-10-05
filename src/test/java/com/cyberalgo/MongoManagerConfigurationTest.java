package com.cyberalgo;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MongoManagerConfigurationTest {

    @SuppressWarnings("unchecked")
    @Test
    void localDiscoveryIsBoundedWhenNoCloudUriExists() throws Exception {
        Method method = MongoManager.class.getDeclaredMethod("buildCandidateUris", String.class);
        method.setAccessible(true);

        List<String> candidates = (List<String>) method.invoke(null, MongoManager.DEFAULT_URI);

        assertTrue(candidates.size() <= 2);
        assertTrue(candidates.contains(MongoManager.DEFAULT_URI));
    }
}
