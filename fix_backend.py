import re

with open('src/WebServer.java', 'r') as f:
    code = f.read()

# Fix app.before("/api/admin/*")
before_replacement = """        app.before("/api/admin/*", ctx -> {
            String userId = ctx.sessionAttribute("userId");
            if (userId == null || userId.isBlank()) {
                throw new io.javalin.http.UnauthorizedResponse("Unauthorized: Authentication required for administrative operations");
            }
            try {
                User user = engine.getUser(userId);
                if (user == null || user.getRole() != User.Role.ADMIN) {
                    throw new io.javalin.http.ForbiddenResponse("Forbidden: Administrator privileges required");
                }
            } catch (Exception ex) {
                throw new io.javalin.http.ForbiddenResponse("Forbidden: Administrator privileges required");
            }
        });"""

code = re.sub(r'        app\.before\("/api/admin/\*", ctx -> \{.*?\n        \}\);', before_replacement, code, flags=re.DOTALL)

# Add requireAdmin to handleFreezeScoreboard
freeze_replacement = """    private void handleFreezeScoreboard(Context ctx) {
        User user = requireAdmin(ctx);
        if (user == null) return;
        List<Contest> contests = engine.getContests();"""
code = re.sub(r'    private void handleFreezeScoreboard\(Context ctx\) \{\n        List<Contest> contests = engine\.getContests\(\);', freeze_replacement, code)

with open('src/WebServer.java', 'w') as f:
    f.write(code)
