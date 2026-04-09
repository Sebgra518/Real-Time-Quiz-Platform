package src;

import model.SubmitAnswerResult;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

public class APIClientHandler {

    private static String BASE_URL = "";
    private String lastPlayersResponse;

    public void setURL(String url) {
        BASE_URL = url != null ? url.trim() : "";
    }

    public int createUser(String username, String pass) {
        HttpURLConnection conn = null;
        try {
            // Ensure we hit /users
            String endpoint = BASE_URL.endsWith("/") ? BASE_URL + "users" : BASE_URL + "/users";
            URL url = new URL(endpoint);

            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
            conn.setDoOutput(true);

            // Build body correctly (password must be pass, not "UTF-8")
            String body = "username=" + URLEncoder.encode(username, StandardCharsets.UTF_8.name())
                    + "&password=" + URLEncoder.encode(pass, StandardCharsets.UTF_8.name());

            byte[] out = body.getBytes(StandardCharsets.UTF_8);
            conn.setFixedLengthStreamingMode(out.length);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(out);
            }

            int code = conn.getResponseCode();

            // Optional: print response body to help debug non-2xx codes
            InputStream is = (code >= 200 && code < 400) ? conn.getInputStream() : conn.getErrorStream();
            if (is != null) {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                    String line;
                    System.out.println("Response:");
                    while ((line = br.readLine()) != null)
                        System.out.println(line);
                }
            }

            return code;

        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        } finally {
            if (conn != null)
                conn.disconnect();
        }
    }

    public List<String> getCategoriesFromUser(String username) {
        List<String> categories = new ArrayList<>();
        HttpURLConnection conn = null;
        try {
            String encodedUser = URLEncoder.encode(username, StandardCharsets.UTF_8);
            String endpoint = (BASE_URL.endsWith("/"))
                    ? BASE_URL + "users/" + encodedUser + "/categories"
                    : BASE_URL + "/users/" + encodedUser + "/categories";

            URL url = new URL(endpoint);
            System.out.println("GET " + url);

            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/json");
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(10_000);

            int code = conn.getResponseCode();

            // Choose the right stream (error stream may be null on some JDKs)
            InputStream stream = (code >= 200 && code < 300)
                    ? conn.getInputStream()
                    : (conn.getErrorStream() != null ? conn.getErrorStream() : conn.getInputStream());

            String body;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                for (String line; (line = reader.readLine()) != null;)
                    sb.append(line);
                body = sb.toString();
            }

            if (code != 200) {
                // Try to extract {"error":"..."} for a friendlier message
                try {
                    JSONObject o = new JSONObject(body);
                    String msg = o.optString("error", "HTTP " + code);
                    throw new IOException("Failed to fetch categories: " + msg + " (HTTP " + code + ")");
                } catch (org.json.JSONException ignore) {
                    throw new IOException("Failed to fetch categories (HTTP " + code + "): " + body);
                }
            }

            // Parse body as EITHER an array or an object with "categories"
            Object parsed = new JSONTokener(body).nextValue();
            JSONArray arr;
            if (parsed instanceof JSONArray) {
                arr = (JSONArray) parsed;
            } else if (parsed instanceof JSONObject) {
                JSONObject o = (JSONObject) parsed;
                if (o.has("error")) {
                    throw new IOException("Failed to fetch categories: " + o.optString("error"));
                }
                arr = o.optJSONArray("categories");
                if (arr == null) {
                    throw new org.json.JSONException("Missing 'categories' array in response");
                }
            } else {
                throw new org.json.JSONException("Unexpected JSON type: " + parsed.getClass());
            }

            for (int i = 0; i < arr.length(); i++) {
                categories.add(arr.getString(i));
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (conn != null)
                conn.disconnect();
        }
        return categories;
    }

    public int createSession(String username) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(BASE_URL + "/sessions");
            conn = (HttpURLConnection) url.openConnection();

            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            conn.setDoOutput(true);

            // like: curl -d "username=alice"
            String data = "username=" + URLEncoder.encode(username, StandardCharsets.UTF_8.name());
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = data.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            int http = conn.getResponseCode();
            String body = readBody(conn);

            // Expect 200 OK or 201 Created, but be lenient with any 2xx
            if (http / 100 == 2) {
                int key = extractSixDigitKey(body);
                if (key != -1) {
                    return key; // <-- SUCCESS: return the actual 6-digit key
                }
            }

            System.err.println("createSession unexpected response (" + http + "): " + body);
            return -1;

        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        } finally {
            if (conn != null)
                conn.disconnect();
        }
    }

    public int joinSession(int key, String username) {
        try {
            String keyStr = String.format("%06d", key); // <-- pad to 6 digits
            // Construct the target URL, e.g.
            // http://192.168.254.161:3050/sessions/083201/join
            URL url = new URL(BASE_URL + "/sessions/" + keyStr + "/join");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();

            // Configure request
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            conn.setDoOutput(true);

            // Request body: username=bob
            String data = "username=" + URLEncoder.encode(username, "UTF-8");
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = data.getBytes("UTF-8");
                os.write(input, 0, input.length);
            }

            // Get HTTP response code
            int responseCode = conn.getResponseCode();

            // Optional: print server response
            try (BufferedReader br = new BufferedReader(new InputStreamReader(
                    responseCode >= 200 && responseCode < 300 ? conn.getInputStream() : conn.getErrorStream()))) {
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    response.append(line);
                }
                System.out.println("joinSession response: " + response);
            }

            conn.disconnect();
            return responseCode;

        } catch (Exception e) {
            e.printStackTrace();
            return -1; // indicate failure
        }
    }

    public int leaveSession(int key, String username) {
        try {
            // Build the URL: http://<base>/sessions/{key}/leave
            String keyStr = String.format("%06d", key);
            URL url = new URL(BASE_URL + "/sessions/" + keyStr + "/leave");

            // Create connection
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

            // Build form data
            String formData = "username=" + URLEncoder.encode(username, "UTF-8");

            // Send form data
            try (OutputStream os = conn.getOutputStream()) {
                os.write(formData.getBytes());
            }

            // Get the HTTP response code
            int responseCode = conn.getResponseCode();

            // Expected: 200 on success
            if (responseCode == 200) {
                return 200;
            }

            return responseCode;

        } catch (java.net.ConnectException e) {
            System.err.println("Connection refused: " + e.getMessage());
            return -1;
        } catch (java.net.UnknownHostException e) {
            System.err.println("Unknown host: " + e.getMessage());
            return -1;
        } catch (IOException e) {
            System.err.println("I/O error in leaveSession: " + e.getMessage());
            return -1;
        } catch (Exception e) {
            System.err.println("Unexpected error in leaveSession: " + e.getMessage());
            return -1;
        }
    }

    public List<String> listPlayersInSession(int key) {
        List<String> players = new ArrayList<>();
        HttpURLConnection conn = null;

        try {
            String keyStr = String.format("%06d", key);
            URL url = new URL(BASE_URL + "/sessions/" + keyStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/json");

            int responseCode = conn.getResponseCode();

            StringBuilder response = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(
                    responseCode >= 200 && responseCode < 300 ? conn.getInputStream() : conn.getErrorStream()))) {
                String line;
                while ((line = br.readLine()) != null) {
                    response.append(line);
                }
            }

            lastPlayersResponse = response.toString();
            System.out.println("listPlayersInSession response: " + lastPlayersResponse);

            // Only parse if successful
            if (responseCode >= 200 && responseCode < 300) {
                players = parsePlayerNames(lastPlayersResponse);
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (conn != null)
                conn.disconnect();
        }

        return players;
    }

    public int closeSession(int key) {
        try {
            String keyStr = String.format("%06d", key);
            URL url = new URL(BASE_URL + "/sessions/" + keyStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();

            // DELETE request
            conn.setRequestMethod("DELETE");
            conn.setRequestProperty("Accept", "application/json");

            int responseCode = conn.getResponseCode();

            // Optional: print response content for debug/logging
            try (BufferedReader br = new BufferedReader(new InputStreamReader(
                    responseCode >= 200 && responseCode < 300 ? conn.getInputStream() : conn.getErrorStream()))) {
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    response.append(line);
                }
                System.out.println("closeSession response: " + response);
            }

            conn.disconnect();
            return responseCode;

        } catch (Exception e) {
            e.printStackTrace();
            return -1; // indicate failure
        }
    }

    public int startSession(int key, String username, String category, int timeSeconds) {
        HttpURLConnection conn = null;
        try {
            String keyStr = String.format("%06d", key);
            URL url = new URL(BASE_URL + "/sessions/" + keyStr + "/start");
            conn = (HttpURLConnection) url.openConnection();

            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            conn.setDoOutput(true);

            String body = "username=" + URLEncoder.encode(username, StandardCharsets.UTF_8.name()) +
                    "&category=" + URLEncoder.encode(category, StandardCharsets.UTF_8.name()) +
                    "&time=" + URLEncoder.encode(Integer.toString(timeSeconds), StandardCharsets.UTF_8.name());

            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }

            int code = conn.getResponseCode();
            // (optional) print response...
            return code;
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        } finally {
            if (conn != null)
                conn.disconnect();
        }
    }

    // ---Quiz Creation---
    public int createCategory(String username, String category) {
        HttpURLConnection conn = null;
        try {
            // Build endpoint: /users/<username>/categories
            String endpoint = BASE_URL.endsWith("/")
                    ? BASE_URL + "users/" + URLEncoder.encode(username, StandardCharsets.UTF_8.name()) + "/categories"
                    : BASE_URL + "/users/" + URLEncoder.encode(username, StandardCharsets.UTF_8.name()) + "/categories";

            URL url = new URL(endpoint);

            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
            conn.setDoOutput(true);

            // Body: category=<encoded>
            String body = "category=" + URLEncoder.encode(category, StandardCharsets.UTF_8.name());
            byte[] out = body.getBytes(StandardCharsets.UTF_8);
            conn.setFixedLengthStreamingMode(out.length);

            // Write request body
            try (OutputStream os = conn.getOutputStream()) {
                os.write(out);
            }

            // Response code
            int code = conn.getResponseCode();

            // Optional: print server response (very helpful)
            InputStream is = (code >= 200 && code < 400)
                    ? conn.getInputStream()
                    : conn.getErrorStream();

            if (is != null) {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                    String line;
                    System.out.println("Response:");
                    while ((line = br.readLine()) != null)
                        System.out.println(line);
                }
            }

            return code;

        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        } finally {
            if (conn != null)
                conn.disconnect();
        }
    }

    public int createQuestion(String category, String question) {
        HttpURLConnection conn = null;
        try {
            // Build endpoint: /categories/<category>/question
            String endpoint = BASE_URL.endsWith("/")
                    ? BASE_URL + "categories/" + URLEncoder.encode(category, StandardCharsets.UTF_8.name())
                            + "/question"
                    : BASE_URL + "/categories/" + URLEncoder.encode(category, StandardCharsets.UTF_8.name())
                            + "/question";

            URL url = new URL(endpoint);

            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
            conn.setDoOutput(true);

            // Body: question=<encoded>
            String body = "question=" + URLEncoder.encode(question, StandardCharsets.UTF_8.name());
            byte[] out = body.getBytes(StandardCharsets.UTF_8);
            conn.setFixedLengthStreamingMode(out.length);

            // Write request body
            try (OutputStream os = conn.getOutputStream()) {
                os.write(out);
            }

            // Get response status code
            int code = conn.getResponseCode();

            // Optional: read response body for debugging
            InputStream is = (code >= 200 && code < 400)
                    ? conn.getInputStream()
                    : conn.getErrorStream();

            if (is != null) {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                    String line;
                    System.out.println("Response:");
                    while ((line = br.readLine()) != null)
                        System.out.println(line);
                }
            }

            return code;

        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        } finally {
            if (conn != null)
                conn.disconnect();
        }
    }

    public int createAnswer(String question, String answer, boolean isCorrect) {
        HttpURLConnection conn = null;
        try {
            // Build endpoint: /questions/<question>/answers
            String endpoint = BASE_URL.endsWith("/")
                    ? BASE_URL + "questions/" + URLEncoder.encode(question, StandardCharsets.UTF_8.name()) + "/answers"
                    : BASE_URL + "/questions/" + URLEncoder.encode(question, StandardCharsets.UTF_8.name())
                            + "/answers";

            URL url = new URL(endpoint);

            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
            conn.setDoOutput(true);

            // Body: answerText=<answer>&isCorrect=<true/false>
            String body = "answerText=" + URLEncoder.encode(answer, StandardCharsets.UTF_8.name()) +
                    "&isCorrect=" + URLEncoder.encode(Boolean.toString(isCorrect), StandardCharsets.UTF_8.name());

            byte[] out = body.getBytes(StandardCharsets.UTF_8);
            conn.setFixedLengthStreamingMode(out.length);

            // Write request body
            try (OutputStream os = conn.getOutputStream()) {
                os.write(out);
            }

            // Response code
            int code = conn.getResponseCode();

            // Optional: read response for debugging
            InputStream is = (code >= 200 && code < 400)
                    ? conn.getInputStream()
                    : conn.getErrorStream();

            if (is != null) {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                    String line;
                    System.out.println("Response:");
                    while ((line = br.readLine()) != null)
                        System.out.println(line);
                }
            }

            return code;

        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        } finally {
            if (conn != null)
                conn.disconnect();
        }
    }

    // ---Quiz---
    public int submitAnswer(int key, String username, int answerId) {
        HttpURLConnection conn = null;
        try {
            String keyStr = String.format("%06d", key);
            URL url = new URL(BASE_URL + "/sessions/" + keyStr + "/answer");
            conn = (HttpURLConnection) url.openConnection();

            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            conn.setDoOutput(true);

            String body = "username=" + URLEncoder.encode(username, StandardCharsets.UTF_8.name()) +
                    "&answerId=" + URLEncoder.encode(Integer.toString(answerId), StandardCharsets.UTF_8.name());

            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }

            int code = conn.getResponseCode();

            // Optional: print response
            try (BufferedReader br = new BufferedReader(new InputStreamReader(
                    code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream(),
                    StandardCharsets.UTF_8))) {
                String line;
                StringBuilder resp = new StringBuilder();
                while ((line = br.readLine()) != null) {
                    resp.append(line);
                }
                System.out.println("submitAnswer response: " + resp);
            }

            return code;

        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        } finally {
            if (conn != null)
                conn.disconnect();
        }
    }

    public int nextQuestion(int key, String username) {
        HttpURLConnection conn = null;
        try {
            // always send 6-digit key
            String keyStr = String.format("%06d", key);
            String endpoint = BASE_URL + "/sessions/" + keyStr + "/next";

            URL url = new URL(endpoint);
            conn = (HttpURLConnection) url.openConnection();

            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
            conn.setDoOutput(true);

            // Only the host is allowed to advance
            String body = "username=" + URLEncoder.encode(username, StandardCharsets.UTF_8.name());
            byte[] out = body.getBytes(StandardCharsets.UTF_8);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(out);
            }

            int code = conn.getResponseCode();

            // Optional: print response for debugging
            try (BufferedReader br = new BufferedReader(new InputStreamReader(
                    code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream(),
                    StandardCharsets.UTF_8))) {

                StringBuilder resp = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    resp.append(line);
                }
                System.out.println("nextQuestion response: " + resp);
            }

            return code;

        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        } finally {
            if (conn != null)
                conn.disconnect();
        }
    }

    public int login(String username, String password) {
        HttpURLConnection conn = null;
        try {
            // Construct endpoint URL
            String endpoint = BASE_URL.endsWith("/") ? BASE_URL + "login" : BASE_URL + "/login";
            URL url = new URL(endpoint);
            System.out.println("POST " + url);

            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
            conn.setDoOutput(true);

            // Prepare form body
            String body = "username=" + URLEncoder.encode(username, StandardCharsets.UTF_8.name())
                    + "&password=" + URLEncoder.encode(password, StandardCharsets.UTF_8.name());

            byte[] out = body.getBytes(StandardCharsets.UTF_8);
            conn.setFixedLengthStreamingMode(out.length);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(out);
            }

            int code = conn.getResponseCode();

            // Debug output for testing
            InputStream is = (code >= 200 && code < 400) ? conn.getInputStream() : conn.getErrorStream();
            if (is != null) {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                    String line;
                    System.out.println("Response:");
                    while ((line = br.readLine()) != null)
                        System.out.println(line);
                }
            }

            return code;

        } catch (Exception e) {
            e.printStackTrace();
            return -1; // indicate connection or unexpected error
        } finally {
            if (conn != null)
                conn.disconnect();
        }
    }

    public int logout(String username) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(BASE_URL + "/logout");
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            connection.setDoOutput(true);

            // Only username is needed, and it must be form-encoded
            String body = "username=" + URLEncoder.encode(username, StandardCharsets.UTF_8);

            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = body.getBytes(StandardCharsets.UTF_8);
                os.write(input);
            }

            int responseCode = connection.getResponseCode();

            // Optional: read response body for debugging
            try (InputStream is = (responseCode >= 200 && responseCode < 300)
                    ? connection.getInputStream()
                    : connection.getErrorStream()) {
                if (is != null) {
                    String resp = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                    System.out.println("Logout response (" + responseCode + "): " + resp);
                }
            }

            return responseCode;

        } catch (java.net.ConnectException e) {
            System.err.println("Connection refused: " + e.getMessage());
            return -1;
        } catch (java.net.UnknownHostException e) {
            System.err.println("Invalid URL: " + e.getMessage());
            return -1;
        } catch (IOException e) {
            System.err.println("I/O error during logout: " + e.getMessage());
            return -1;
        } catch (Exception e) {
            System.err.println("Unexpected error during logout: " + e.getMessage());
            return -1;
        } finally {
            if (connection != null)
                connection.disconnect();
        }
    }

    private static String readBody(HttpURLConnection conn) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(
                conn.getResponseCode() >= 200 && conn.getResponseCode() < 300
                        ? conn.getInputStream()
                        : conn.getErrorStream(),
                StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null)
            sb.append(line);
        br.close();
        return sb.toString();
    }

    private static int extractSixDigitKey(String text) {
        if (text == null)
            return -1;

        // Try a few common JSON shapes explicitly
        Matcher mJson = Pattern.compile("\"key\"\\s*:\\s*\"?(\\d{6})\"?").matcher(text);
        if (mJson.find())
            return Integer.parseInt(mJson.group(1));

        // Generic fallback: first 6-digit number anywhere
        Matcher m = Pattern.compile("\\b(\\d{6})\\b").matcher(text);
        if (m.find())
            return Integer.parseInt(m.group(1));

        return -1;
    }

    private List<String> parsePlayerNames(String json) {
        List<String> names = new ArrayList<>();
        if (json == null || json.isEmpty())
            return names;

        // Try to find quoted names
        Matcher m = Pattern.compile("\"([A-Za-z0-9_\\-]+)\"").matcher(json);
        while (m.find()) {
            String name = m.group(1);
            if (!"players".equalsIgnoreCase(name)) {
                names.add(name);
            }
        }

        return names;
    }

    public JSONObject getSessionState(int key) {
        HttpURLConnection conn = null;
        try {
            String keyStr = String.format("%06d", key);
            URL url = new URL(BASE_URL + "/sessions/" + keyStr + "/state");

            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/json");

            int code = conn.getResponseCode();

            // Read body
            StringBuilder response = new StringBuilder();
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(
                            code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream(),
                            StandardCharsets.UTF_8))) {

                String line;
                while ((line = br.readLine()) != null)
                    response.append(line);
            }

            if (code >= 200 && code < 300) {
                return new JSONObject(response.toString());
            } else if (code == 404) {
                // Session not found -> host probably closed it
                System.err.println("Session not found: " + response);
                JSONObject obj = new JSONObject();
                obj.put("sessionClosed", true);
                return obj;
            } else {
                System.err.println("getSessionState error: HTTP " + code + " body=" + response);
                return null;
            }

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        } finally {
            if (conn != null)
                conn.disconnect();
        }
    }

    public SubmitAnswerResult submitAnswerDetailed(int key, String username, int answerId) {
        HttpURLConnection conn = null;
        try {
            String keyStr = String.format("%06d", key);
            URL url = new URL(BASE_URL + "/sessions/" + keyStr + "/answer");

            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            conn.setDoOutput(true);

            String body = "username=" + URLEncoder.encode(username, StandardCharsets.UTF_8)
                    + "&answerId=" + URLEncoder.encode(String.valueOf(answerId), StandardCharsets.UTF_8);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }

            int code = conn.getResponseCode();

            StringBuilder response = new StringBuilder();
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(
                            code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream(),
                            StandardCharsets.UTF_8))) {

                String line;
                while ((line = br.readLine()) != null)
                    response.append(line);
            }

            if (code >= 200 && code < 300) {
                JSONObject obj = new JSONObject(response.toString());
                boolean correct = obj.optBoolean("correct", false);
                int correctAnswerId = obj.optInt("correctAnswerId", -1);
                return new SubmitAnswerResult(code, correct, correctAnswerId);
            } else {
                System.err.println("submitAnswer error: HTTP " + code + " body=" + response);
                return new SubmitAnswerResult(code, false, -1);
            }

        } catch (Exception e) {
            e.printStackTrace();
            return new SubmitAnswerResult(-1, false, -1);
        } finally {
            if (conn != null)
                conn.disconnect();
        }
    }

}
