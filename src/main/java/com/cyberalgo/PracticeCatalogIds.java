package com.cyberalgo;

final class PracticeCatalogIds {
    private PracticeCatalogIds() {}
    static boolean isShowable(Challenge c) {
        if (c.getId().startsWith("ARENA-")) return true;
        if (c instanceof CPProblem cp) return cp.isJudgeReady() || c.getId().startsWith("CF-") || c.getId().startsWith("AC-");
        // Old generated security entries lack the evidence needed to solve them.
        return !c.getId().startsWith("SEC-") && c.getDescription() != null
                && !c.getDescription().isBlank();
    }
}
