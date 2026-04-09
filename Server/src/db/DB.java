// src/main/java/db/DB.java
package db;

import java.sql.*;
import java.util.*;
import model.*;

import org.mindrot.jbcrypt.BCrypt; // if you use BCrypt for password hashing

public class DB {

    // --- Configure ---
    private static final String JDBC_URL = "jdbc:mysql://192.168.254.161:3306/quizdata?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
    private static final String JDBC_USER = "root";
    private static final String JDBC_PASS = "password";

    // Load BCrypt Driver
    static {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException ignored) {
        }
    }

    // Get a new DB connection (caller should use try-with-resources).
    public Connection connect() throws SQLException {
        return DriverManager.getConnection(JDBC_URL, JDBC_USER, JDBC_PASS);
    }

    // --- CATEGORIES ---
    public List<String> getCategoriesFromUsername(String username) {
        List<String> categories = new ArrayList<>();
        if (username == null || username.isBlank())
            return categories;

        String sql = """
                                    SELECT c.name
                FROM users u
                JOIN categories c ON c.user_id = u.id
                WHERE u.username = ?
                ORDER BY c.name;

                                """;
        try (Connection conn = connect();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username.trim());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    categories.add(rs.getString("name"));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return categories;
    }

    public String addCategoryToUsername(String username, String category) {
        if (username == null || username.isBlank() ||
                category == null || category.isBlank()) {
            return "invalid_input";
        }

        String getUserSql = "SELECT id FROM users WHERE username = ?";
        String insertCategorySql = "INSERT INTO categories(name, user_id) VALUES (?, ?)";

        try (Connection conn = connect()) {
            int userId;

            // Look up user id
            try (PreparedStatement ps = conn.prepareStatement(getUserSql)) {
                ps.setString(1, username.trim());
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return "user_not_found";
                    }
                    userId = rs.getInt(1);
                }
            }

            // Insert category
            try (PreparedStatement ps = conn.prepareStatement(insertCategorySql)) {
                ps.setString(1, category.trim());
                ps.setInt(2, userId);
                int rows = ps.executeUpdate();
                if (rows > 0) {
                    return "ok";
                } else {
                    return "category_exists";
                }
            }
        } catch (SQLIntegrityConstraintViolationException e) {
            // e.g. UNIQUE(user_id, name) violation
            return "category_exists";
        } catch (SQLException e) {
            e.printStackTrace();
            return "db_error";
        }
    }

    // --- USERS ---
    // Create user; returns userId (>0) if ok, 0 if invalid, -1 on DB error.
    public int createUser(String username, String password) {
        if (username == null || username.isBlank() || password == null)
            return 0;

        String hash = BCrypt.hashpw(password, BCrypt.gensalt(12));
        String sql = "INSERT INTO users (username, password_hash) VALUES (?, ?)";

        try (Connection conn = connect();
                PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, username.trim());
            ps.setString(2, hash);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next())
                    return rs.getInt(1);
            }
            return -1;
        } catch (SQLIntegrityConstraintViolationException dup) {
            return 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return -1;
        }
    }

    // Login; returns userId (>0) if ok, 0 if invalid, -1 on DB error.
    public int login(String username, String password) {
        String sql = "SELECT id, password_hash FROM users WHERE username = ?";
        try (Connection conn = connect();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next())
                    return 0;
                int userId = rs.getInt("id");
                String hash = rs.getString("password_hash");
                return BCrypt.checkpw(password, hash) ? userId : 0;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return -1;
        }
    }

    // --- QUESTIONS ---

    // Returns list of question texts for a given category name.
    public List<String> getAllQuestionFromCategory(String category) {
        List<String> questions = new ArrayList<>();
        String sql = """
                    SELECT q.text
                    FROM questions q
                    JOIN categories c ON q.category_id = c.id
                    WHERE c.name = ?
                    ORDER BY q.id
                """;
        try (Connection conn = connect();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, category);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next())
                    questions.add(rs.getString("text"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return questions;
    }

    public List<QuizQuestion> loadQuestionsForCategory(String category) {
        String sql = """
                SELECT
                    q.id          AS q_id,
                    q.text        AS q_text,
                    a.id          AS a_id,
                    a.answer_text AS a_text,
                    a.is_correct  AS a_correct
                FROM questions q
                JOIN categories c ON q.category_id = c.id
                LEFT JOIN answers a ON a.question_id = q.id
                WHERE c.name = ?
                ORDER BY q.id, a.id
                """;
        List<QuizQuestion> questions = new ArrayList<>();

        try (Connection conn = connect();
                PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, category);

            try (ResultSet rs = ps.executeQuery()) {
                int currentQuestionId = -1;
                QuizQuestion currentQuestion = null;
                List<QuizAnswer> currentAnswers = null;

                while (rs.next()) {
                    int qId = rs.getInt("q_id");

                    // When we see a new question id, finish the previous one and start a new one
                    if (currentQuestion == null || qId != currentQuestionId) {
                        // flush previous
                        if (currentQuestion != null) {
                            // create a new question object with its accumulated answers
                            questions.add(new QuizQuestion(
                                    currentQuestion.id,
                                    currentQuestion.text,
                                    currentAnswers));
                        }

                        currentQuestionId = qId;
                        String qText = rs.getString("q_text");
                        currentAnswers = new ArrayList<>();
                        currentQuestion = new QuizQuestion(qId, qText, currentAnswers);
                    }

                    int aId = rs.getInt("a_id");
                    // Because of LEFT JOIN, answers may be null if no answers yet
                    if (!rs.wasNull()) {
                        String aText = rs.getString("a_text");
                        boolean correct = rs.getBoolean("a_correct");
                        currentAnswers.add(new QuizAnswer(aId, aText, correct));
                    }
                }

                // flush last question
                if (currentQuestion != null) {
                    questions.add(new QuizQuestion(
                            currentQuestion.id,
                            currentQuestion.text,
                            currentAnswers));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return questions;
    }

    // Adds a single question to a category (creates the category if needed).
    public String addQuestionToCategory(String question, String category) {
        if (category == null || category.isBlank() ||
                question == null || question.isBlank()) {
            return "invalid_input";
        }

        String insertCategory = "INSERT IGNORE INTO categories(name) VALUES (?)";
        String getCategoryId = "SELECT id FROM categories WHERE name = ?";
        String insertQuestion = "INSERT INTO questions(text, category_id) VALUES (?, ?)";

        try (Connection conn = connect()) {

            // Ensure category exists
            try (PreparedStatement ps = conn.prepareStatement(insertCategory)) {
                ps.setString(1, category.trim());
                ps.executeUpdate();
            }

            int categoryId;
            try (PreparedStatement ps = conn.prepareStatement(getCategoryId)) {
                ps.setString(1, category.trim());
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return "category_missing";
                    }
                    categoryId = rs.getInt(1);
                }
            }

            // Insert question
            try (PreparedStatement ps = conn.prepareStatement(insertQuestion)) {
                ps.setString(1, question.trim());
                ps.setInt(2, categoryId);
                int rows = ps.executeUpdate();
                if (rows > 0) {
                    return "ok";
                } else {
                    return "question_exists";
                }
            }

        } catch (SQLIntegrityConstraintViolationException e) {
            return "question_exists";
        } catch (SQLException e) {
            e.printStackTrace();
            return "db_error";
        }
    }

    // Adds multiple questions to a category (creates the category if needed).
    public int addQuestionsToCategory(String category, List<String> questions) {
        if (category == null || category.isBlank() || questions == null || questions.isEmpty())
            return 0;

        // Clean and de-dupe within input
        List<String> cleaned = questions.stream()
                .filter(Objects::nonNull).map(String::trim).filter(s -> !s.isEmpty()).distinct().toList();

        String insertCategory = "INSERT IGNORE INTO categories(name) VALUES (?)";
        String getCategoryId = "SELECT id FROM categories WHERE name = ?";
        String insertQuestion = "INSERT IGNORE INTO questions(text, category_id) VALUES (?, ?)";

        int added = 0;

        try (Connection conn = connect()) {
            // ensure category
            try (PreparedStatement ps = conn.prepareStatement(insertCategory)) {
                ps.setString(1, category);
                ps.executeUpdate();
            }
            int categoryId;
            try (PreparedStatement ps = conn.prepareStatement(getCategoryId)) {
                ps.setString(1, category);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    categoryId = rs.getInt(1);
                }
            }

            // insert questions
            try (PreparedStatement ps = conn.prepareStatement(insertQuestion)) {
                for (String q : cleaned) {
                    ps.setString(1, q);
                    ps.setInt(2, categoryId);
                    added += ps.executeUpdate(); // 1 if inserted, 0 if duplicate
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return added;
    }

    // --------------- ANSWERS ----------------

    // Returns answers (text + isCorrect) for a given question text.
    public List<Answer> getAllAnswersFromQuestion(String question) {
        List<Answer> answers = new ArrayList<>();
        String sql = """
                    SELECT a.answer_text, a.is_correct
                    FROM answers a
                    JOIN questions q ON a.question_id = q.id
                    WHERE q.text = ?
                    ORDER BY a.id
                """;
        try (Connection conn = connect();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, question);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    answers.add(new Answer(
                            rs.getString("answer_text"),
                            rs.getBoolean("is_correct")));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return answers;
    }

    public String addAnswerToQuestion(String question, Answer answer) {
        if (question == null || question.isBlank() || answer == null) {
            return "invalid_input";
        }
        String text = answer.text;
        if (text == null || text.trim().isEmpty()) {
            return "invalid_input";
        }

        String getQuestionId = "SELECT id FROM questions WHERE text = ?";
        String insertAnswer = "INSERT INTO answers(question_id, answer_text, is_correct) VALUES (?, ?, ?)";

        try (Connection conn = connect()) {

            int questionId;
            try (PreparedStatement ps = conn.prepareStatement(getQuestionId)) {
                ps.setString(1, question.trim());
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return "question_not_found";
                    }
                    questionId = rs.getInt(1);
                }
            }

            try (PreparedStatement ps = conn.prepareStatement(insertAnswer)) {
                ps.setInt(1, questionId);
                ps.setString(2, text.trim());
                ps.setBoolean(3, answer.isCorrect);
                int rows = ps.executeUpdate();
                if (rows > 0) {
                    return "ok";
                } else {
                    return "answer_exists";
                }
            }

        } catch (SQLIntegrityConstraintViolationException e) {
            return "answer_exists";
        } catch (SQLException e) {
            e.printStackTrace();
            return "db_error";
        }
    }

}
