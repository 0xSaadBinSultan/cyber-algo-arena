import re

with open('src/WebServer.java', 'r') as f:
    code = f.read()

# Replace routes
code = code.replace('app.post("/api/admin/challenges/ctf", this::handleAddCtf);', 'app.post("/api/admin/challenges", this::handleAddChallenge);')
code = code.replace('app.post("/api/admin/challenges/cp", this::handleAddCp);', '')

# Replace handlers
handler_code = """    private void handleAddChallenge(Context ctx) {
        User user = requireAdmin(ctx);
        if (user == null) return;

        Map<String, String> body = parseBody(ctx);
        try {
            String type = requireField(body, "type");
            String id = requireField(body, "id");
            String title = requireField(body, "title");
            String description = body.getOrDefault("description", "");
            int basePoints = Integer.parseInt(requireField(body, "basePoints"));
            Challenge.Difficulty difficulty = Challenge.Difficulty.fromToken(requireField(body, "difficulty"));

            Challenge created = null;

            if ("CTF".equalsIgnoreCase(type)) {
                String category = requireField(body, "category");
                String rawFlag = requireField(body, "rawFlag");
                int hintCost = Integer.parseInt(body.getOrDefault("hintCost", "0"));
                String attachmentFileName = body.get("attachmentFileName");
                if (attachmentFileName == null || attachmentFileName.isBlank()) {
                    attachmentFileName = body.get("attachment");
                }
                CTFChallenge ctf = engine.addCtfChallenge(id, title, basePoints, difficulty, category, rawFlag, hintCost, attachmentFileName);
                ctf.setDescription(description);
                engine.getRepository().saveChallenge(ctf);
                created = ctf;
            } else if ("CP".equalsIgnoreCase(type)) {
                long timeLimitMs = Long.parseLong(body.getOrDefault("timeLimitMs", "1000"));
                int memoryLimitMb = Integer.parseInt(body.getOrDefault("memoryLimitMb", "256"));
                
                String sampleIn = body.getOrDefault("sampleInput", "");
                String sampleOut = body.getOrDefault("sampleOutput", "");
                
                java.nio.file.Path testcaseDir = java.nio.file.Path.of("contest_data", "testcases", id);
                if (!sampleIn.isEmpty() || !sampleOut.isEmpty()) {
                    try {
                        java.nio.file.Files.createDirectories(testcaseDir);
                        java.nio.file.Files.writeString(testcaseDir.resolve("input_1.txt"), sampleIn + "\\n");
                        java.nio.file.Files.writeString(testcaseDir.resolve("output_1.txt"), sampleOut + "\\n");
                    } catch (Exception e) {}
                }
                
                CPProblem cp = engine.addCpChallenge(id, title, basePoints, difficulty, timeLimitMs, memoryLimitMb, testcaseDir);
                cp.setDescription(description);
                engine.getRepository().saveChallenge(cp);
                created = cp;
            } else {
                throw new IllegalArgumentException("Invalid challenge type");
            }

            ctx.status(201).json(Map.of("message", "Challenge created", "id", created.getId()));
        } catch (IllegalArgumentException ex) {
            ctx.status(400).json(errorMap(ex.getMessage()));
        }
    }"""

code = re.sub(r'    private void handleAddCtf\(Context ctx\) \{.*?\n    \}\n\n    private void handleAddCp\(Context ctx\) \{.*?\n    \}', handler_code, code, flags=re.DOTALL)

with open('src/WebServer.java', 'w') as f:
    f.write(code)
