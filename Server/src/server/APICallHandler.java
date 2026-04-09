package server;

import com.sun.net.httpserver.*;
import db.DB;
import model.*;
import java.io.*;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

public class APICallHandler {
    private static final DB db = new DB();
    private static final Set<String> LOGGED_IN_USERS = ConcurrentHashMap.newKeySet();
    private static final Map<Integer, Session> SESSIONS = new ConcurrentHashMap<>();

    public static void registerRoutes(HttpServer server) {
        // Example endpoints
        server.createContext("/users", wrap(APICallHandler::handleUsers));
        server.createContext("/login", wrap(APICallHandler::handleLogin));
        server.createContext("/categories", wrap(APICallHandler::handleCategory));
        server.createContext("/questions", wrap(APICallHandler::handleQuestion));
        server.createContext("/health", wrap((ex) -> respondJson(ex, 200, "{\"ok\":true}")));
        server.createContext("/sessions", wrap(APICallHandler::handleSessions));
        server.createContext("/logout", wrap(APICallHandler::handleLogout));

    }

    private static void handleCreateUser(HttpExchange ex) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) {
            methodNotAllowed(ex, "POST");
            return;
        }
        Map<String, String> form = readForm(ex);
        String username = form.get("username");
        String password = form.get("password");

        if (isBlank(username) || isBlank(password)) {
            badRequest(ex, "username and password required");
            return;
        }

        int id = db.createUser(username, password);
        if (id > 0)
            respondJson(ex, 201, "{\"userId\":" + id + "}");
        else if (id == 0)
            respondJson(ex, 409, "{\"error\":\"user exists\"}");
        else
            respondJson(ex, 500, "{\"error\":\"db error\"}");
    }

    private static void handleLogin(HttpExchange ex) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) {
            methodNotAllowed(ex, "POST");
            return;
        }
        Map<String, String> form = readForm(ex);
        String username = form.get("username");
        String password = form.get("password");

        if (isBlank(username) || isBlank(password)) {
            badRequest(ex, "username and password required");
            return;
        }

        int userId = db.login(username, password);
        if (userId > 0) {
            boolean added = LOGGED_IN_USERS.add(username);
            if (!added) {
                respondJson(ex, 409, "{\"error\":\"user already logged in\"}");
                return;
            }
            respondJson(ex, 200, "{\"userId\":" + userId + "}");
        } else {
            respondJson(ex, 401, "{\"error\":\"invalid credentials\"}");
        }
    }

    private static void handleLogout(HttpExchange ex) throws IOException {

        if (!"POST".equals(ex.getRequestMethod())) {
            methodNotAllowed(ex, "POST");
            return;
        }
        Map<String, String> form = readForm(ex);
        String username = form.get("username");
        if (isBlank(username)) {
            badRequest(ex, "username required");
            return;
        }
        boolean removed = LOGGED_IN_USERS.remove(username);

        respondJson(ex, 200, "{\"ok\":" + removed + "}");
    }

    private static void handleCategory(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        String method = ex.getRequestMethod();

        // GET/POST /categories/{category}/questions (existing bulk API)
        var matcher = Pattern.compile("^/categories/([^/]+)/questions$").matcher(path);
        if ("GET".equals(method) && matcher.matches()) {
            String category = urlDecode(matcher.group(1));
            var questions = db.getAllQuestionFromCategory(category);
            respondJson(ex, 200, toJsonList("questions", questions));
            return;
        }

        if ("POST".equals(method) && matcher.matches()) {
            String category = urlDecode(matcher.group(1));
            Map<String, String> form = readForm(ex);

            // If a single 'question' is provided, use addQuestionToCategory
            String singleQuestion = form.get("question");
            if (!isBlank(singleQuestion)) {
                String result = db.addQuestionToCategory(singleQuestion, category);

                switch (result) {
                    case "ok" -> respondJson(ex, 201, "{\"status\":\"ok\"}");
                    case "invalid_input" -> badRequest(ex, "invalid input");
                    case "category_missing" -> respondJson(ex, 404, "{\"error\":\"category missing\"}");
                    case "question_exists" -> respondJson(ex, 409, "{\"error\":\"question exists\"}");
                    default -> respondJson(ex, 500, "{\"error\":\"db error\"}");
                }
                return;
            }

            // Fallback to multiline "questions" bulk insert
            String raw = form.getOrDefault("questions", "");
            var questions = Arrays.asList(raw.split("\\r?\\n"));
            int added = db.addQuestionsToCategory(category, questions);
            respondJson(ex, 201, "{\"added\":" + added + "}");
            return;
        }

        // NEW: optional dedicated endpoint POST /categories/{category}/question
        // (singular)
        var singleMatcher = Pattern.compile("^/categories/([^/]+)/question$").matcher(path);
        if ("POST".equals(method) && singleMatcher.matches()) {
            String category = urlDecode(singleMatcher.group(1));
            Map<String, String> form = readForm(ex);
            String question = form.get("question");

            if (isBlank(question)) {
                badRequest(ex, "question required");
                return;
            }

            String result = db.addQuestionToCategory(question, category);

            switch (result) {
                case "ok" -> respondJson(ex, 201, "{\"status\":\"ok\"}");
                case "invalid_input" -> badRequest(ex, "invalid input");
                case "category_missing" -> respondJson(ex, 404, "{\"error\":\"category missing\"}");
                case "question_exists" -> respondJson(ex, 409, "{\"error\":\"question exists\"}");
                default -> respondJson(ex, 500, "{\"error\":\"db error\"}");
            }
            return;
        }

        notFound(ex);
    }

    private static void handleQuestion(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        String method = ex.getRequestMethod();

        var matcher = Pattern.compile("^/questions/([^/]+)/answers$").matcher(path);
        if (!matcher.matches()) {
            notFound(ex);
            return;
        }
        String question = urlDecode(matcher.group(1));

        if ("GET".equals(method)) {
            var answers = db.getAllAnswersFromQuestion(question);
            respondJson(ex, 200, toJsonAnswers(answers));
            return;
        }

        if ("POST".equals(method)) {
            Map<String, String> form = readForm(ex);
            String answerText = form.get("answerText");
            String isCorrectStr = form.get("isCorrect");

            if (isBlank(answerText) || isBlank(isCorrectStr)) {
                badRequest(ex, "answerText and isCorrect required");
                return;
            }

            boolean isCorrect = Boolean.parseBoolean(isCorrectStr);
            Answer answer = new Answer(answerText, isCorrect);

            String result = db.addAnswerToQuestion(question, answer);

            switch (result) {
                case "ok" -> respondJson(ex, 201, "{\"status\":\"ok\"}");
                case "invalid_input" -> badRequest(ex, "invalid input");
                case "question_not_found" -> respondJson(ex, 404, "{\"error\":\"question not found\"}");
                case "answer_exists" -> respondJson(ex, 409, "{\"error\":\"answer exists\"}");
                default -> respondJson(ex, 500, "{\"error\":\"db error\"}");
            }
            return;
        }

        methodNotAllowed(ex, "GET,POST");
    }

    private static void handleSessions(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath(); // e.g. "/sessions" or "/sessions/123456/join"
        String method = ex.getRequestMethod();

        // POST /sessions -> create session (returns {"key":"123456"})
        if ("POST".equals(method) && "/sessions".equals(path)) {
            Map<String, String> form = readForm(ex);
            String username = form.get("username"); // optional "owner" to auto-join
            int key = generateUniqueKey();

            // if username is provided, it must be logged in
            if (!isBlank(username) && !LOGGED_IN_USERS.contains(username)) {
                respondJson(ex, 401, "{\"error\":\"not logged in\"}");
                return;
            }

            // host is final, so pass it into the constructor (can be null)
            Session session = new Session(key, isBlank(username) ? null : username);

            SESSIONS.put(key, session);

            // Important: an int doesn't keep leading zeros; return/display as 6 chars
            respondJson(ex, 201, "{\"key\":\"" + formatKey(key) + "\"}");
            return;
        }

        // POST /sessions/{key}/join -> body: username=foo
        var joinMatcher = Pattern.compile("^/sessions/(\\d{6})/join$").matcher(path);
        if ("POST".equals(method) && joinMatcher.matches()) {
            int key = Integer.parseInt(joinMatcher.group(1));
            Map<String, String> form = readForm(ex);
            String username = form.get("username");

            if (isBlank(username)) {
                badRequest(ex, "username required");
                return;
            }
            if (!LOGGED_IN_USERS.contains(username)) {
                respondJson(ex, 401, "{\"error\":\"not logged in\"}");
                return;
            }

            Session s = SESSIONS.get(key);
            if (s == null) {
                respondJson(ex, 404, "{\"error\":\"session not found\"}");
                return;
            }

            // avoid duplicates using Session helpers
            if (!s.hasPlayer(username)) {
                s.addPlayer(username);
            }

            respondJson(ex, 200, "{\"players\":" + toJsonArray(s.getPlayerNames()) + "}");
            return;
        }

        // POST /sessions/{key}/leave -> body: username=foo
        var leaveMatcher = Pattern.compile("^/sessions/(\\d{6})/leave$").matcher(path);
        if ("POST".equals(method) && leaveMatcher.matches()) {
            int key = Integer.parseInt(leaveMatcher.group(1));
            Map<String, String> form = readForm(ex);
            String username = form.get("username");

            if (isBlank(username)) {
                badRequest(ex, "username required");
                return;
            }
            if (!LOGGED_IN_USERS.contains(username)) {
                respondJson(ex, 401, "{\"error\":\"not logged in\"}");
                return;
            }

            Session s = SESSIONS.get(key);
            if (s == null) {
                respondJson(ex, 404, "{\"error\":\"session not found\"}");
                return;
            }

            boolean removed = s.removePlayer(username);
            respondJson(ex, 200, "{\"ok\":" + removed + "}");
            return;
        }

        // GET /sessions/{key} -> {"players":[...]}
        var getMatcher = Pattern.compile("^/sessions/(\\d{6})$").matcher(path);
        if ("GET".equals(method) && getMatcher.matches()) {
            int key = Integer.parseInt(getMatcher.group(1));
            Session s = SESSIONS.get(key);
            if (s == null) {
                respondJson(ex, 404, "{\"error\":\"session not found\"}");
                return;
            }
            // send player names, not Player objects
            respondJson(ex, 200, "{\"players\":" + toJsonArray(s.getPlayerNames()) + "}");
            return;
        }

        // DELETE /sessions/{key} -> close session
        var delMatcher = Pattern.compile("^/sessions/(\\d{6})$").matcher(path);
        if ("DELETE".equals(method) && delMatcher.matches()) {
            int key = Integer.parseInt(delMatcher.group(1));
            boolean removed = (SESSIONS.remove(key) != null);
            respondJson(ex, 200, "{\"ok\":" + removed + "}");
            return;
        }

        // GET /sessions -> {"keys":["123456","004201",...]}
        if ("GET".equals(method) && "/sessions".equals(path)) {
            List<String> keys = new ArrayList<>();
            for (Integer k : SESSIONS.keySet())
                keys.add(formatKey(k));
            // Optional: sort for nicer output
            Collections.sort(keys);
            respondJson(ex, 200, "{\"keys\":" + toJsonArray(keys) + "}");
            return;
        }

        // POST /sessions/{key}/start -> body: category=Foo[&time=20]
        var startMatcher = Pattern.compile("^/sessions/(\\d{6})/start$").matcher(path);
        if ("POST".equals(method) && startMatcher.matches()) {
            int key = Integer.parseInt(startMatcher.group(1));
            Map<String, String> form = readForm(ex);
            String category = form.get("category");
            String timeStr = form.get("time"); // optional

            if (isBlank(category)) {
                badRequest(ex, "category required");
                return;
            }

            Session s = SESSIONS.get(key);
            if (s == null) {
                respondJson(ex, 404, "{\"error\":\"session not found\"}");
                return;
            }

            // OPTIONAL: only host can start the quiz
            String username = form.get("username");
            if (s.host != null && (isBlank(username) || !s.host.equals(username))) {
                respondJson(ex, 403, "{\"error\":\"only host can start\"}");
                return;
            }

            // Parse time if provided, otherwise keep default
            if (!isBlank(timeStr)) {
                try {
                    int seconds = Integer.parseInt(timeStr);
                    // simple sanity bounds (e.g., 5–300 seconds)
                    if (seconds < 5 || seconds > 300) {
                        badRequest(ex, "time must be between 5 and 300 seconds");
                        return;
                    }
                    s.setTimePerQuestionSeconds(seconds);
                } catch (NumberFormatException e) {
                    badRequest(ex, "invalid time");
                    return;
                }
            }

            List<QuizQuestion> qs = db.loadQuestionsForCategory(category);
            if (qs.isEmpty()) {
                respondJson(ex, 400, "{\"error\":\"no questions for category\"}");
                return;
            }

            s.startCategory(category, qs);

            respondJson(ex, 200, "{\"ok\":true}");
            return;
        }

        var stateMatcher = Pattern.compile("^/sessions/(\\d{6})/state$").matcher(path);
        if ("GET".equals(method) && stateMatcher.matches()) {
            int key = Integer.parseInt(stateMatcher.group(1));
            Session s = SESSIONS.get(key);
            if (s == null) {
                respondJson(ex, 404, "{\"error\":\"session not found\"}");
                return;
            }

            QuizQuestion q = s.getCurrentQuestion();

            StringBuilder json = new StringBuilder();
            json.append("{");
            json.append("\"started\":").append(s.started).append(",");
            json.append("\"finished\":").append(s.finished).append(","); // NEW
            json.append("\"time\":").append(s.currentTime).append(",");
            json.append("\"players\":").append(toJsonArray(s.getPlayerNames())).append(",");

            if (q != null) {
                json.append("\"question\":").append(escapeJson(q.text)).append(",");
                json.append("\"answers\":[");
                for (int i = 0; i < q.answers.size(); i++) {
                    QuizAnswer a = q.answers.get(i);
                    if (i > 0)
                        json.append(",");
                    // don’t include correct flag to clients
                    json.append("{\"id\":").append(a.id)
                            .append(",\"text\":").append(escapeJson(a.text))
                            .append("}");
                }
                json.append("]");
            } else {
                json.append("\"question\":null,\"answers\":[]");
            }
            if (s.finished) {
                // Example: scores as [{ "name":"alice", "score":3 }, ...]
                json.append(",\"scores\":[");
                List<Player> players = s.players; // assuming accessible
                for (int i = 0; i < players.size(); i++) {
                    Player p = players.get(i);
                    if (i > 0)
                        json.append(",");
                    json.append("{\"name\":").append(escapeJson(p.name))
                            .append(",\"score\":").append(p.score)
                            .append("}");
                }
                json.append("]");
            }

            json.append("}");

            respondJson(ex, 200, json.toString());
            return;
        }

        var answerMatcher = Pattern.compile("^/sessions/(\\d{6})/answer$").matcher(path);
        if ("POST".equals(method) && answerMatcher.matches()) {
            int key = Integer.parseInt(answerMatcher.group(1));
            Map<String, String> form = readForm(ex);
            String username = form.get("username");
            String answerIdStr = form.get("answerId");

            if (isBlank(username) || isBlank(answerIdStr)) {
                badRequest(ex, "username and answerId required");
                return;
            }

            int answerId;
            try {
                answerId = Integer.parseInt(answerIdStr);
            } catch (NumberFormatException e) {
                badRequest(ex, "invalid answerId");
                return;
            }

            Session s = SESSIONS.get(key);
            if (s == null) {
                respondJson(ex, 404, "{\"error\":\"session not found\"}");
                return;
            }

            if (!s.started || !s.acceptingAnswers) {
                respondJson(ex, 400, "{\"error\":\"not accepting answers\"}");
                return;
            }

            QuizQuestion q = s.getCurrentQuestion();
            if (q == null) {
                respondJson(ex, 400, "{\"error\":\"no active question\"}");
                return;
            }

            // --- FIND PLAYER ----
            Player player = null;
            for (Player p : s.players) {
                if (p.name.equals(username)) {
                    player = p;
                    break;
                }
            }
            if (player == null) {
                respondJson(ex, 403, "{\"error\":\"not in this session\"}");
                return;
            }

            // --- PREVENT DOUBLE ANSWERING ---
            if (player.answeredThisQuestion) {
                respondJson(ex, 400, "{\"error\":\"already answered\"}");
                return;
            }

            // --- MARK ANSWERED ---
            player.answeredThisQuestion = true;

            // --- CHECK CORRECTNESS ---
            boolean correct = false;
            int correctAnswerId = -1;

            for (QuizAnswer a : q.answers) {
                if (a.correct) {
                    correctAnswerId = a.id; // remember which answer is correct
                }
                if (a.id == answerId) {
                    correct = a.correct;
                }
            }

            if (correct) {
                s.addScore(username, 1);
            }

            // CHECK IF ALL PLAYERS HAVE ANSWERED
            boolean allAnswered = true;
            for (Player p : s.players) {
                if (!p.answeredThisQuestion) {
                    allAnswered = false;
                    break;
                }
            }

            if (allAnswered) {

                // Is this the last question?
                boolean lastQuestion = (s.currentQuestionIndex >= s.questions.size() - 1);

                if (lastQuestion) {
                    // === QUIZ IS FINISHED ===

                    s.acceptingAnswers = false;
                    s.started = false;
                    s.finished = true;
                } else {
                    // === Move to the next question ===

                    s.nextQuestion();
                }
            }

            // =====================================================

            respondJson(ex, 200,
                    "{\"correct\":" + correct + ",\"correctAnswerId\":" + correctAnswerId + "}");
            return;
        }

        var nextMatcher = Pattern.compile("^/sessions/(\\d{6})/next$").matcher(path);
        if ("POST".equals(method) && nextMatcher.matches()) {
            int key = Integer.parseInt(nextMatcher.group(1));
            Map<String, String> form = readForm(ex);
            String username = form.get("username"); // host

            Session s = SESSIONS.get(key);
            if (s == null) {
                respondJson(ex, 404, "{\"error\":\"session not found\"}");
                return;
            }

            // optional: only host can advance
            if (s.host != null && !s.host.equals(username)) {
                respondJson(ex, 403, "{\"error\":\"only host can advance\"}");
                return;
            }

            s.nextQuestion();
            respondJson(ex, 200, "{\"ok\":true}");
            return;
        }

        methodNotAllowed(ex, "GET,POST,DELETE");
    }

    private static void handleUsers(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        String method = ex.getRequestMethod();

        // POST /users -> create user
        if ("POST".equals(method) && "/users".equals(path)) {
            handleCreateUser(ex);
            return;
        }

        // /users/{username}/categories
        var m = Pattern.compile("^/users/([^/]+)/categories$").matcher(path);
        if (m.matches()) {
            String username = urlDecode(m.group(1));
            if (isBlank(username)) {
                badRequest(ex, "username required");
                return;
            }

            if ("GET".equals(method)) {
                // Optional: require that the user is logged in
                if (!LOGGED_IN_USERS.contains(username)) {
                    respondJson(ex, 401, "{\"error\":\"not logged in\"}");
                    return;
                }

                List<String> categories = db.getCategoriesFromUsername(username);
                respondJson(ex, 200, toJsonList("categories", categories));
                return;
            }

            if ("POST".equals(method)) {
                // Add category to this username
                Map<String, String> form = readForm(ex);
                String category = form.get("category");

                if (isBlank(category)) {
                    badRequest(ex, "category required");
                    return;
                }

                String result = db.addCategoryToUsername(username, category);

                switch (result) {
                    case "ok" -> respondJson(ex, 201, "{\"status\":\"ok\"}");
                    case "invalid_input" -> badRequest(ex, "invalid input");
                    case "user_not_found" -> respondJson(ex, 404, "{\"error\":\"user not found\"}");
                    case "category_exists" -> respondJson(ex, 409, "{\"error\":\"category exists\"}");
                    default -> respondJson(ex, 500, "{\"error\":\"db error\"}");
                }
                return;
            }

            methodNotAllowed(ex, "GET,POST");
            return;
        }

        // Fallbacks
        if ("POST".equals(method)) {
            methodNotAllowed(ex, "POST");
        } else {
            notFound(ex);
        }
    }

    // Helpers
    private static HttpHandler wrap(Handler h) {
        return ex -> {
            try {
                h.handle(ex);
            } catch (Exception e) {
                e.printStackTrace();
                respondJson(ex, 500, "{\"error\":\"internal error\"}");
            } finally {
                ex.close();
            }
        };
    }

    @FunctionalInterface
    interface Handler {
        void handle(HttpExchange ex) throws Exception;
    }

    private static Map<String, String> readForm(HttpExchange ex) throws IOException {
        try (InputStream is = ex.getRequestBody()) {
            String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            return parseQuery(body);
        }
    }

    private static Map<String, String> parseQuery(String query) throws UnsupportedEncodingException {
        Map<String, String> map = new HashMap<>();
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2)
                map.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8),
                        URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
        }
        return map;
    }

    private static void respondJson(HttpExchange ex, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static void badRequest(HttpExchange ex, String msg) throws IOException {
        respondJson(ex, 400, "{\"error\":\"" + msg + "\"}");
    }

    private static void methodNotAllowed(HttpExchange ex, String allow) throws IOException {
        ex.getResponseHeaders().add("Allow", allow);
        respondJson(ex, 405, "{\"error\":\"method not allowed\"}");
    }

    private static void notFound(HttpExchange ex) throws IOException {
        respondJson(ex, 404, "{\"error\":\"not found\"}");
    }

    private static String toJsonList(String key, List<String> list) {
        StringBuilder sb = new StringBuilder("{\"" + key + "\":[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0)
                sb.append(',');
            sb.append('"').append(list.get(i).replace("\"", "\\\"")).append('"');
        }
        sb.append("]}");
        return sb.toString();
    }

    private static String toJsonAnswers(List<Answer> list) {
        StringBuilder sb = new StringBuilder("{\"answers\":[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0)
                sb.append(',');
            Answer a = list.get(i);
            sb.append("{\"text\":\"").append(a.text).append("\",\"isCorrect\":").append(a.isCorrect).append('}');
        }
        sb.append("]}");
        return sb.toString();
    }

    private static String urlDecode(String s) throws UnsupportedEncodingException {
        return URLDecoder.decode(s, StandardCharsets.UTF_8);
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static int generateUniqueKey() {
        // Try a few times to avoid collisions
        for (int i = 0; i < 20; i++) {
            int candidate = ThreadLocalRandom.current().nextInt(0, 1_000_000);
            if (!SESSIONS.containsKey(candidate))
                return candidate;
        }
        // Fallback: rare; iterate until a gap is found
        for (int candidate = 0; candidate < 1_000_000; candidate++) {
            if (!SESSIONS.containsKey(candidate))
                return candidate;
        }
        throw new IllegalStateException("No session keys available");
    }

    private static String formatKey(int key) {
        return String.format("%06d", key);
    }

    private static String toJsonArray(List<String> items) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < items.size(); i++) {
            if (i > 0)
                sb.append(',');
            sb.append('"').append(items.get(i).replace("\"", "\\\"")).append('"');
        }
        sb.append(']');
        return sb.toString();
    }

    private static String escapeJson(String s) {
        if (s == null)
            return "null";
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\b':
                    sb.append("\\b");
                    break;
                case '\f':
                    sb.append("\\f");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    if (c < 0x20 || c > 0x7E) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append("\"");
        return sb.toString();
    }

    // --- QuizHandler ---
    private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor();

    static {
        // Run every 1 second, after an initial 1 second delay
        SCHEDULER.scheduleAtFixedRate(() -> {
            try {
                for (Session s : SESSIONS.values()) {
                    if (s.started) {
                        s.tick();
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, 1, 1, TimeUnit.SECONDS);
    }

}
