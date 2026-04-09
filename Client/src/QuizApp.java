//Java 21.0.9
package src;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import org.json.JSONArray;
import org.json.JSONObject;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import model.*;

public class QuizApp extends JFrame {
    private CardLayout cardLayout;
    private JPanel mainPanel;
    private APIClientHandler handler = new APIClientHandler();
    private final JTextField baseUrlField = new JTextField("http://localhost:3050");

    // --- Welcome Page ---
    private String username = "";
    private String password = "";

    private JTextField usernameField;
    private JPasswordField passwordField;

    private JButton registerButton;
    private JButton LoginButton;

    // --- Host Page ---
    private int key = -1;
    private JTextField sessionKeyField;

    private JSpinner timerSpinner;

    private ButtonGroup categoryGroup;
    private JList<String> categoryList;

    private DefaultListModel<String> playersModel;
    private JList<String> playersList;

    private JSpinner joinKeySpinner;

    // ---Quiz Creator Vars ---
    private String draftCategory;
    private final java.util.List<QuizQuestion> draftQuestions = new ArrayList<>();

    private final DefaultListModel<String> questionListModel = new DefaultListModel<>();
    private JList<String> questionList;

    private int editingQuestionIndex = -1;

    private ButtonGroup corrBtnGrp;

    // ---Question Creator Vars ---
    private JTextField qcQuestionField;
    private JTextField[] qcAnswerFields;
    private JRadioButton[] qcRadioButtons;

    // ---Host Quiz Page ---
    private JLabel quizTimerLabel;
    private javax.swing.Timer quizPollTimer;

    private JTextArea quizQuestionArea;

    private DefaultListModel<String> quizAnswersModel;
    private JList<String> quizAnswersList;

    private DefaultListModel<String> quizPlayersModel;
    private JList<String> quizPlayersList;

    // --Client Quiz Page ---
    // Participant quiz UI components
    private JLabel participantTimerLabel;
    private JTextArea participantQuestionArea;
    private JButton[] answerButtons = new JButton[4];
    private java.util.List<ScoreEntry> lastScores = new ArrayList<>();
    private int myFinalScore = 0;
    private JLabel[] topScoreLabels = new JLabel[10];
    private JLabel myScoreLabel;

    // For mapping which button corresponds to which answerId
    private int[] answerIds = new int[4];

    // Polling timer for participant
    private javax.swing.Timer participantPollTimer;

    // Track if this player has already answered the current question
    private boolean answeredThisQuestion = false;
    private boolean hostResultsShown = false;
    private boolean participantResultsShown = false;
    private String lastQuestionText = null;

    private JTable hostScoresTable;
    private DefaultTableModel hostScoresTableModel;

    private int participantStateFailureCount = 0;
    private int hostStateFailureCount = 0;

    private Color[] defaultAnswerBackgrounds = new Color[4];

    public QuizApp() {
        this.setTitle("Trivia Game by Sebastian");
        this.setSize(800, 600);
        this.setLocationRelativeTo(null);

        cardLayout = new CardLayout();
        mainPanel = new JPanel(cardLayout);
        add(mainPanel);

        mainPanel.add(welcomePage(), "Welcome");
        mainPanel.add(modeSelectPage(), "ModeSelect");
        mainPanel.add(participantResultsPage(), "ParticipantResultsPage");
        mainPanel.add(hostResultsPage(), "HostResultsPage");
        mainPanel.add(joinPage(), "Join");
        mainPanel.add(quizCreator(), "QuizCreator");
        mainPanel.add(questionCreator(), "QuestionCreator");
        mainPanel.add(participantQuizPage(), "participantQuizPage");
        mainPanel.add(joinedPanel(), "joinedPanel");
        mainPanel.add(HostQuizPanel(), "HostQuizPanel");

        // We want custom exit handling, so don't let Swing close automatically
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                // Try to logout cleanly before exiting
                handleAppExit();
            }
        });
        cardLayout.show(mainPanel, "Welcome");
    }

    // --- JPANELS ---
    private JPanel welcomePage() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));

        JPanel centerPanel = new JPanel();
        centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
        centerPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JLabel welcomeLabel = new JLabel("Welcome to the Quiz!");
        welcomeLabel.setFont(new Font("Arial", Font.BOLD, 24));
        welcomeLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        centerPanel.add(welcomeLabel);
        centerPanel.add(Box.createVerticalStrut(20));

        // Base URL
        JPanel urlPanel = new JPanel(new BorderLayout(8, 8));
        urlPanel.setBorder(new EmptyBorder(10, 10, 0, 10));
        urlPanel.add(new JLabel("Base URL:"), BorderLayout.WEST);
        urlPanel.add(baseUrlField, BorderLayout.CENTER);
        panel.add(urlPanel, BorderLayout.NORTH);

        // Username
        JLabel nameLabel = new JLabel("Username:");
        nameLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        centerPanel.add(nameLabel);
        usernameField = new JTextField(15);
        usernameField.setMaximumSize(new Dimension(200, 30));
        usernameField.setAlignmentX(Component.CENTER_ALIGNMENT);
        centerPanel.add(usernameField);
        centerPanel.add(Box.createVerticalStrut(12));

        // Password
        JLabel passLabel = new JLabel("Password:");
        passLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        centerPanel.add(passLabel);
        passwordField = new JPasswordField(15);
        passwordField.setMaximumSize(new Dimension(200, 30));
        passwordField.setAlignmentX(Component.CENTER_ALIGNMENT);
        centerPanel.add(passwordField);
        centerPanel.add(Box.createVerticalStrut(12));

        // Buttons panel (Register + Login side-by-side)
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));

        // Register
        registerButton = new JButton("Register User");
        registerButton.addActionListener(e -> handleRegister());
        buttonPanel.add(registerButton);

        // Login Button
        LoginButton = new JButton("Login");
        LoginButton.setFont(new Font("Arial", Font.BOLD, 16));
        LoginButton.addActionListener(e -> handleLogin());
        buttonPanel.add(LoginButton);

        buttonPanel.setAlignmentX(Component.CENTER_ALIGNMENT);
        centerPanel.add(buttonPanel);
        centerPanel.add(Box.createVerticalStrut(20));

        panel.add(centerPanel, BorderLayout.CENTER);
        return panel;
    }

    private JPanel modeSelectPage() {
        JPanel panel = new JPanel(new BorderLayout());

        JLabel title = new JLabel("Select Mode", SwingConstants.CENTER);
        title.setFont(new Font("Arial", Font.BOLD, 22));
        panel.add(title, BorderLayout.NORTH);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 30, 20));

        // --- Host button ---
        JButton hostButton = new JButton("Host");
        hostButton.setFont(new Font("Arial", Font.BOLD, 18));
        hostButton.addActionListener(e -> {
            mainPanel.add(hostPage(), "HostPage"); // Loaded lasily to prevent creation of rooms on client startup.
            cardLayout.show(mainPanel, "HostPage");
        });
        buttonPanel.add(hostButton);

        // --- Join button ---
        JButton joinButton = new JButton("Join");
        joinButton.setFont(new Font("Arial", Font.BOLD, 18));
        joinButton.addActionListener(e -> {
            cardLayout.show(mainPanel, "Join");
        });
        buttonPanel.add(joinButton);

        // --- Create Quiz button ---
        JButton createQuizButton = new JButton("Create Quiz");
        createQuizButton.setFont(new Font("Arial", Font.BOLD, 18));
        createQuizButton.addActionListener(e -> {
            cardLayout.show(mainPanel, "QuizCreator");
        });
        buttonPanel.add(createQuizButton);

        panel.add(buttonPanel, BorderLayout.CENTER);

        // --- Logout button ---
        JButton backButton = new JButton("Logout");
        backButton.setFont(new Font("Arial", Font.PLAIN, 16));
        backButton.addActionListener(e -> {
            handler.logout(username);
            username = "";
            password = "";
            cardLayout.show(mainPanel, "Welcome");
        });

        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 20, 10));
        bottomPanel.add(backButton);
        panel.add(bottomPanel, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel hostPage() {
        hostResultsShown = false;
        participantResultsShown = false;
        lastScores.clear();

        JPanel panel = new JPanel(new BorderLayout(12, 12));
        panel.setBorder(new EmptyBorder(16, 16, 16, 16));

        JLabel title = new JLabel("Host Session", SwingConstants.CENTER);
        title.setFont(new Font("Arial", Font.BOLD, 22));
        panel.add(title, BorderLayout.NORTH);

        JPanel center = new JPanel(new GridBagLayout());

        // Add constraints
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(8, 8, 8, 8); // Specify padding
        gc.fill = GridBagConstraints.BOTH; // Fill area
        gc.anchor = GridBagConstraints.NORTHWEST; // Lock in

        // Left Column
        JPanel leftCol = new JPanel();
        leftCol.setLayout(new BoxLayout(leftCol, BoxLayout.Y_AXIS));

        // Timer dropdown
        leftCol.add(new JLabel("Time per question (seconds):"));
        timerSpinner = new JSpinner(new SpinnerNumberModel(15, 5, 60, 1));
        timerSpinner.setAlignmentX(Component.LEFT_ALIGNMENT);
        leftCol.add(timerSpinner);
        leftCol.add(Box.createVerticalStrut(12));

        // Categories list
        leftCol.add(new JLabel("Categories:"));
        categoryList = new JList<>(new DefaultListModel<>());
        categoryList.setVisibleRowCount(10);
        categoryList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        JScrollPane catScroll = new JScrollPane(categoryList);
        catScroll.setPreferredSize(new Dimension(260, 180));
        leftCol.add(catScroll);
        leftCol.add(Box.createVerticalStrut(8));

        JButton refreshCategoriesBtn = new JButton("Refresh Categories");
        refreshCategoriesBtn.addActionListener(e -> loadCategories());
        leftCol.add(refreshCategoriesBtn);

        gc.gridx = 0;
        gc.gridy = 0;
        gc.weightx = 0.5;
        gc.weighty = 1.0;
        center.add(leftCol, gc);

        // Right column
        JPanel rightCol = new JPanel();
        rightCol.setLayout(new BoxLayout(rightCol, BoxLayout.Y_AXIS));

        // Session Key field (read-only)
        rightCol.add(new JLabel("Session Key:"));
        sessionKeyField = new JTextField(10);
        sessionKeyField.setEditable(false);
        sessionKeyField.setFont(new Font("Monospaced", Font.BOLD, 16));
        sessionKeyField.setBackground(Color.LIGHT_GRAY);
        sessionKeyField.setMaximumSize(new Dimension(200, 28));
        rightCol.add(sessionKeyField);
        rightCol.add(Box.createVerticalStrut(12));

        // Players list (read-only)
        rightCol.add(new JLabel("Players in Session:"));
        playersModel = new DefaultListModel<>();
        playersList = new JList<>(playersModel);
        playersList.setEnabled(false);
        JScrollPane playersScroll = new JScrollPane(playersList);
        playersScroll.setPreferredSize(new Dimension(260, 180));
        rightCol.add(playersScroll);
        rightCol.add(Box.createVerticalStrut(8));

        JButton refreshPlayersBtn = new JButton("Refresh Players");
        refreshPlayersBtn.addActionListener(e -> handleRefreshPlayerButton());
        rightCol.add(refreshPlayersBtn);

        gc.gridx = 1;
        gc.gridy = 0;
        gc.weightx = 0.5;
        gc.weighty = 1.0;
        center.add(rightCol, gc);

        panel.add(center, BorderLayout.CENTER);

        // Botton Area
        JPanel south = new JPanel(new BorderLayout());

        // Buttons (Back + Start Quiz)
        JPanel bottomButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton backBtn = new JButton("Back");
        backBtn.addActionListener(e -> {
            handler.closeSession(key);
            cardLayout.show(mainPanel, "ModeSelect");
        });
        bottomButtons.add(backBtn);

        // Start Quiz Button
        JButton proceedBtn = new JButton("Start Quiz");
        proceedBtn.addActionListener(e -> handleQuizStart());
        bottomButtons.add(proceedBtn);

        south.add(bottomButtons, BorderLayout.EAST);
        panel.add(south, BorderLayout.SOUTH);

        // Generate Session
        try {
            key = handler.createSession(username);
            if (key > 0) {
                sessionKeyField.setText(String.format("%06d", key));
            } else {
                sessionKeyField.setText("Error");
                JOptionPane.showMessageDialog(this, "Failed to create session.");
            }
        } catch (Exception e) {
            e.printStackTrace();
            sessionKeyField.setText("Error");
        }
        loadCategories(); // populate categories

        return panel;
    }

    private JPanel joinPage() {
        JPanel panel = new JPanel(new BorderLayout(12, 12));
        panel.setBorder(new EmptyBorder(16, 16, 16, 16));

        JLabel title = new JLabel("Join Session", SwingConstants.CENTER);
        title.setFont(new Font("Arial", Font.BOLD, 22));
        panel.add(title, BorderLayout.NORTH);

        // Center form
        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));

        // Code Insert
        JLabel codeLabel = new JLabel("Enter 6-digit session code:");
        codeLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(codeLabel);
        form.add(Box.createVerticalStrut(6));

        // Integer box (0..999999) displayed as 6 digits
        joinKeySpinner = new JSpinner(new SpinnerNumberModel(0, 0, 999999, 1));
        joinKeySpinner.setAlignmentX(Component.LEFT_ALIGNMENT);
        JSpinner.NumberEditor editor = new JSpinner.NumberEditor(joinKeySpinner, "000000");
        joinKeySpinner.setEditor(editor);
        joinKeySpinner.setMaximumSize(new Dimension(140, 30));
        form.add(joinKeySpinner);
        form.add(Box.createVerticalStrut(14));

        // Join Button
        JButton joinBtn = new JButton("Join Session");
        joinBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        joinBtn.addActionListener(e -> {
            handleJoinButton();
        });
        form.add(joinBtn);

        // Center the form
        JPanel centerWrap = new JPanel();
        centerWrap.add(form);
        panel.add(centerWrap, BorderLayout.CENTER);

        // Back button
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton backBtn = new JButton("Back");
        backBtn.addActionListener(e -> {
            cardLayout.show(mainPanel, "ModeSelect");
        });
        bottom.add(backBtn);
        panel.add(bottom, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel joinedPanel() {

        JPanel panel = new JPanel(new BorderLayout(12, 12));
        panel.setBorder(new EmptyBorder(16, 16, 16, 16));

        JLabel title = new JLabel("Waiting Room", SwingConstants.CENTER);
        title.setFont(new Font("Arial", Font.BOLD, 24));
        panel.add(title, BorderLayout.NORTH);

        // Center message
        JLabel waitingLabel = new JLabel("You Are In!", SwingConstants.CENTER);
        waitingLabel.setFont(new Font("Arial", Font.BOLD, 20));
        waitingLabel.setForeground(new Color(0, 128, 0)); // green text
        panel.add(waitingLabel, BorderLayout.CENTER);

        // Exit button
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton exitBtn = new JButton("Exit");
        exitBtn.setFont(new Font("Arial", Font.PLAIN, 14));
        exitBtn.addActionListener(e -> {
            cardLayout.show(mainPanel, "ModeSelect");
        });
        bottom.add(exitBtn);
        panel.add(bottom, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel quizCreator() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));

        // Top
        JPanel topPanel = new JPanel(new BorderLayout(5, 5));

        // Category input
        JLabel categoryLabel = new JLabel("Category:");
        JTextField categoryField = new JTextField();

        topPanel.add(categoryLabel, BorderLayout.WEST);
        topPanel.add(categoryField, BorderLayout.CENTER);
        panel.add(topPanel, BorderLayout.NORTH);

        // Center
        JPanel centerPanel = new JPanel(new BorderLayout(10, 10));

        // Buttons + Question List
        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        JButton createQuestionBtn = new JButton("Create Question");
        JButton editQuestionBtn = new JButton("Edit Question");
        JButton deleteQuestionBtn = new JButton("Delete Question");

        buttonRow.add(createQuestionBtn);
        buttonRow.add(editQuestionBtn);
        buttonRow.add(deleteQuestionBtn);

        questionList = new JList<>(questionListModel);
        JScrollPane scrollPane = new JScrollPane(questionList);

        centerPanel.add(buttonRow, BorderLayout.NORTH);
        centerPanel.add(scrollPane, BorderLayout.CENTER);

        panel.add(centerPanel, BorderLayout.CENTER);

        // Bottom
        JPanel bottomPanel = new JPanel(new BorderLayout());

        // Back + Create Quiz
        JButton backButton = new JButton("Back");
        JButton createQuizBtn = new JButton("Create Quiz");

        JPanel bottomLeft = new JPanel(new FlowLayout(FlowLayout.LEFT));
        bottomLeft.add(backButton);

        JPanel bottomRight = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottomRight.add(createQuizBtn);

        bottomPanel.add(bottomLeft, BorderLayout.WEST);
        bottomPanel.add(bottomRight, BorderLayout.EAST);

        panel.add(bottomPanel, BorderLayout.SOUTH);

        // New question
        createQuestionBtn.addActionListener(e -> {
            handleCreateQuesionButton(panel, categoryField);
        });

        // Edit selected question
        editQuestionBtn.addActionListener(e -> {
            handleEditQuestionButton(panel, categoryField);
        });

        // Delete selected question
        deleteQuestionBtn.addActionListener(e -> {
            handleDeleteQuestionButton(panel);
        });

        // Back to mode select
        backButton.addActionListener(e -> {
            cardLayout.show(mainPanel, "ModeSelect");
        });

        // Create quiz
        createQuizBtn.addActionListener(e -> {
            handleCreateQuizButton(panel, categoryField);
        });

        return panel;
    }

    private JPanel questionCreator() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));

        // --- TOP: Question text ---
        JPanel topPanel = new JPanel(new BorderLayout(5, 5));
        JLabel questionLabel = new JLabel("Question:");
        qcQuestionField = new JTextField(); // use class field
        topPanel.add(questionLabel, BorderLayout.WEST);
        topPanel.add(qcQuestionField, BorderLayout.CENTER);
        panel.add(topPanel, BorderLayout.NORTH);

        // --- CENTER: 2x2 answers with radio buttons ---
        JPanel answersGrid = new JPanel(new GridLayout(2, 2, 10, 10));

        qcAnswerFields = new JTextField[4];
        qcRadioButtons = new JRadioButton[4];
        corrBtnGrp = new ButtonGroup();
        for (int i = 0; i < 4; i++) {
            JPanel cell = new JPanel(new BorderLayout(5, 0));
            qcRadioButtons[i] = new JRadioButton();
            corrBtnGrp.add(qcRadioButtons[i]);

            qcAnswerFields[i] = new JTextField();

            cell.add(qcRadioButtons[i], BorderLayout.WEST);
            cell.add(qcAnswerFields[i], BorderLayout.CENTER);
            answersGrid.add(cell);
        }

        panel.add(answersGrid, BorderLayout.CENTER);

        // --- BOTTOM: Back + Save ---
        JButton backBtn = new JButton("Back");
        JButton saveBtn = new JButton("Save Question");

        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottomPanel.add(backBtn);
        bottomPanel.add(saveBtn);

        panel.add(bottomPanel, BorderLayout.SOUTH);

        // --- BUTTON ACTIONS ---

        backBtn.addActionListener(e -> {
            cardLayout.show(mainPanel, "QuizCreator");
        });

        saveBtn.addActionListener(e -> {
            String questionText = qcQuestionField.getText().trim();
            if (questionText.isEmpty()) {
                JOptionPane.showMessageDialog(panel,
                        "Please enter a question.",
                        "Missing Question",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }

            java.util.List<String> answers = new ArrayList<>();
            int correctIndex = -1;

            for (int i = 0; i < 4; i++) {
                String ans = qcAnswerFields[i].getText().trim();
                if (ans.isEmpty()) {
                    JOptionPane.showMessageDialog(panel,
                            "All answer fields must be filled.",
                            "Missing Answer",
                            JOptionPane.WARNING_MESSAGE);
                    return;
                }
                answers.add(ans);
                if (qcRadioButtons[i].isSelected()) {
                    correctIndex = i;
                }
            }

            if (correctIndex == -1) {
                JOptionPane.showMessageDialog(panel,
                        "Please select the correct answer.",
                        "No Correct Answer Selected",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }

            QuizQuestion newDraft = new QuizQuestion(questionText, answers, correctIndex);

            if (editingQuestionIndex == -1) {
                // New question
                draftQuestions.add(newDraft);
                questionListModel.addElement("Q" + draftQuestions.size() + ": " + questionText);
            } else {
                // Editing existing
                draftQuestions.set(editingQuestionIndex, newDraft);
                questionListModel.set(editingQuestionIndex,
                        "Q" + (editingQuestionIndex + 1) + ": " + questionText);
            }

            cardLayout.show(mainPanel, "QuizCreator");
        });

        return panel;
    }

    private JPanel participantQuizPage() {
        JPanel panel = new JPanel(new BorderLayout(12, 12));
        panel.setBorder(new EmptyBorder(16, 16, 16, 16));

        JLabel title = new JLabel("Quiz In Progress", SwingConstants.CENTER);
        title.setFont(new Font("Arial", Font.BOLD, 22));
        panel.add(title, BorderLayout.NORTH);

        // Top
        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));

        // Timer
        participantTimerLabel = new JLabel("Time: --");
        participantTimerLabel.setFont(new Font("Arial", Font.BOLD, 18));
        participantTimerLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        top.add(participantTimerLabel);
        top.add(Box.createVerticalStrut(8));

        // Quesitons
        participantQuestionArea = new JTextArea(3, 40);
        participantQuestionArea.setLineWrap(true);
        participantQuestionArea.setWrapStyleWord(true);
        participantQuestionArea.setEditable(false);
        participantQuestionArea.setFont(new Font("Arial", Font.PLAIN, 16));
        JScrollPane qScroll = new JScrollPane(participantQuestionArea);
        qScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        top.add(qScroll);

        panel.add(top, BorderLayout.NORTH);

        // Center
        JPanel answersGrid = new JPanel(new GridLayout(2, 2, 12, 12));

        // Answer Buttons
        for (int i = 0; i < 4; i++) {
            JButton btn = new JButton("Answer " + (i + 1));
            btn.setFont(new Font("Arial", Font.PLAIN, 16));
            int index = i;
            btn.addActionListener(e -> handleAnswerClick(index));
            answerButtons[i] = btn;
            defaultAnswerBackgrounds[i] = btn.getBackground();
            answerIds[i] = -1;
            answersGrid.add(btn);
        }

        panel.add(answersGrid, BorderLayout.CENTER);

        // Bottom
        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));

        // Leave Button
        JButton leaveBtn = new JButton("Leave Quiz");
        leaveBtn.addActionListener(e -> {
            // stop polling
            if (participantPollTimer != null)
                participantPollTimer.stop();

            // go back to some screen, e.g. ModeSelect or JoinPage
            cardLayout.show(mainPanel, "ModeSelect");
        });
        south.add(leaveBtn);

        panel.add(south, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel participantResultsPage() {
        JPanel panel = new JPanel(new BorderLayout(12, 12));
        panel.setBorder(new EmptyBorder(16, 16, 16, 16));

        JLabel title = new JLabel("Quiz Results", SwingConstants.CENTER);
        title.setFont(new Font("Arial", Font.BOLD, 24));
        panel.add(title, BorderLayout.NORTH);

        // Top 3 scores
        JPanel center = new JPanel();
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        center.setBorder(new EmptyBorder(10, 40, 10, 40));

        JLabel topLabel = new JLabel("Top 10 Players:");
        topLabel.setFont(new Font("Arial", Font.BOLD, 18));
        topLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        center.add(topLabel);
        center.add(Box.createVerticalStrut(10));

        // List top 10 players
        for (int i = 0; i < 10; i++) {
            topScoreLabels[i] = new JLabel("—");
            topScoreLabels[i].setFont(new Font("Arial", Font.PLAIN, 16));
            topScoreLabels[i].setAlignmentX(Component.CENTER_ALIGNMENT);
            center.add(topScoreLabels[i]);
            center.add(Box.createVerticalStrut(3));
        }

        center.add(Box.createVerticalStrut(20));

        // Display current User's Score
        myScoreLabel = new JLabel("Your score: --", SwingConstants.CENTER);
        myScoreLabel.setFont(new Font("Arial", Font.BOLD, 18));
        myScoreLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        center.add(myScoreLabel);

        panel.add(center, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));

        // Leave button
        JButton leaveBtn = new JButton("Leave");
        leaveBtn.setFont(new Font("Arial", Font.PLAIN, 16));
        leaveBtn.addActionListener(e -> {
            handler.leaveSession(key, username);
            cardLayout.show(mainPanel, "ModeSelect");
        });
        bottom.add(leaveBtn);
        panel.add(bottom, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel HostQuizPanel() {
        JPanel panel = new JPanel(new BorderLayout(12, 12));
        panel.setBorder(new EmptyBorder(16, 16, 16, 16));

        JLabel title = new JLabel("Hosting Quiz", SwingConstants.CENTER);
        title.setFont(new Font("Arial", Font.BOLD, 22));
        panel.add(title, BorderLayout.NORTH);

        // Center
        JPanel center = new JPanel(new BorderLayout(8, 8));

        JPanel topCenter = new JPanel();
        topCenter.setLayout(new BoxLayout(topCenter, BoxLayout.Y_AXIS));

        // Quiz Timer
        quizTimerLabel = new JLabel("Time: --");
        quizTimerLabel.setFont(new Font("Arial", Font.BOLD, 18));
        quizTimerLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        topCenter.add(quizTimerLabel);
        topCenter.add(Box.createVerticalStrut(8));

        // Queston Display
        quizQuestionArea = new JTextArea(3, 40);
        quizQuestionArea.setLineWrap(true);
        quizQuestionArea.setWrapStyleWord(true);
        quizQuestionArea.setEditable(false);
        quizQuestionArea.setFont(new Font("Arial", Font.PLAIN, 16));
        JScrollPane questionScroll = new JScrollPane(quizQuestionArea);
        questionScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        topCenter.add(questionScroll);

        center.add(topCenter, BorderLayout.NORTH);

        // Answers list
        quizAnswersModel = new DefaultListModel<>();
        quizAnswersList = new JList<>(quizAnswersModel);
        quizAnswersList.setEnabled(false); // host is not answering here
        JScrollPane answersScroll = new JScrollPane(quizAnswersList);
        answersScroll.setBorder(BorderFactory.createTitledBorder("Answers"));
        center.add(answersScroll, BorderLayout.CENTER);

        panel.add(center, BorderLayout.CENTER);

        // Players in Session
        quizPlayersModel = new DefaultListModel<>();
        quizPlayersList = new JList<>(quizPlayersModel);
        quizPlayersList.setEnabled(false);
        JScrollPane playersScroll = new JScrollPane(quizPlayersList);
        playersScroll.setPreferredSize(new Dimension(200, 0));
        playersScroll.setBorder(BorderFactory.createTitledBorder("Players"));
        panel.add(playersScroll, BorderLayout.EAST);

        // Bottom
        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));

        // Back Button
        JButton backBtn = new JButton("Back to Host Lobby");
        backBtn.addActionListener(e -> {
            if (quizPollTimer != null)
                quizPollTimer.stop();
            cardLayout.show(mainPanel, "HostPage");
        });
        south.add(backBtn);

        // Next Button
        JButton nextBtn = new JButton("Next Question");
        nextBtn.addActionListener(e -> {
            int code = handler.nextQuestion(key, username);
            if (code < 200 || code >= 300) {
                JOptionPane.showMessageDialog(
                        this,
                        "Failed to advance question (HTTP " + code + ")",
                        "Error",
                        JOptionPane.ERROR_MESSAGE);
            }
        });

        // End Quiz Button
        JButton endBtn = new JButton("End Quiz");
        endBtn.addActionListener(e -> {
            handleEndQuiz();
        });

        south.add(nextBtn);
        south.add(endBtn);

        panel.add(south, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel hostResultsPage() {
        JPanel panel = new JPanel(new BorderLayout(12, 12));
        panel.setBorder(new EmptyBorder(16, 16, 16, 16));

        JLabel title = new JLabel("Quiz Results (Host View)", SwingConstants.CENTER);
        title.setFont(new Font("Arial", Font.BOLD, 24));
        panel.add(title, BorderLayout.NORTH);

        // Table Player and Score
        hostScoresTableModel = new DefaultTableModel(new Object[] { "Player", "Score" }, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false; // host can't edit results
            }
        };

        // Host Scores Table
        hostScoresTable = new JTable(hostScoresTableModel);
        hostScoresTable.setFillsViewportHeight(true);
        hostScoresTable.setRowHeight(24);
        hostScoresTable.getTableHeader().setReorderingAllowed(false);

        JScrollPane scroll = new JScrollPane(hostScoresTable);
        scroll.setBorder(BorderFactory.createTitledBorder("Final Scores"));
        panel.add(scroll, BorderLayout.CENTER);

        // Back Button
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton backBtn = new JButton("Back to Mode Select");
        backBtn.addActionListener(e -> {
            handler.closeSession(key);
            cardLayout.show(mainPanel, "ModeSelect");
        });
        bottom.add(backBtn);

        panel.add(bottom, BorderLayout.SOUTH);

        return panel;
    }

    // --- JPANEL SPECIFIC METHODS ---

    // -- Welcome Screen --
    private void handleRegister() {
        handler.setURL(baseUrlField.getText().trim());
        username = usernameField.getText().trim();
        password = passwordField.getText().trim();
        if (username.isEmpty() || password.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Enter a username and password to register.");
            return;
        }

        int errorCode = handler.createUser(username, password);

        if (errorCode == 409) {
            JOptionPane.showMessageDialog(this, username + " is already taken");
            return;
        }

        JOptionPane.showMessageDialog(this, "Registered " + username + " successfully!");
        handleLogin();
        usernameField.setText("");
        passwordField.setText("");
    }

    private void handleLogin() {
        handler.setURL(baseUrlField.getText().trim());

        if (usernameField.getText().trim().isEmpty() || passwordField.getText().trim().isEmpty()) {
            JOptionPane.showMessageDialog(this, "Enter a username and password to login.");
            return;
        }

        int errorCode = handler.login(usernameField.getText().trim(), passwordField.getText().trim());

        if (errorCode == 401) {
            JOptionPane.showMessageDialog(this, "Invalid Username or Password");
            return;
        }
        if (errorCode == 409) {
            JOptionPane.showMessageDialog(this, "Username already logged in");
            return;
        }
        if (errorCode == -1) {
            JOptionPane.showMessageDialog(this, "Invalid URL/Server is down");
            return;
        }

        if (errorCode == 200) {
            username = usernameField.getText().trim();
            password = passwordField.getText().trim();
            cardLayout.show(mainPanel, "ModeSelect");
        }
    }

    // -- Mode Select Screen --
    private void handleJoinButton() {
        key = (Integer) joinKeySpinner.getValue();
        String user = username;

        // Basic validation: ensure 6 digits (000000..999999 are allowed; you might want
        // to disallow 000000)
        if (key < 0 || key > 999999) {
            JOptionPane.showMessageDialog(this, "Please enter a valid 6-digit code.");
            return;
        }

        int http = handler.joinSession(key, user);
        if (http >= 200 && http < 300) {
            startParticipantStatePolling();
            cardLayout.show(mainPanel, "joinedPanel");
        } else {
            JOptionPane.showMessageDialog(this, "Failed to join session (HTTP " + http + ").");
        }
    }

    // -- Question Creator --
    private void handleCreateQuesionButton(JPanel panel, JTextField categoryField) {
        draftCategory = categoryField.getText().trim();
        if (draftCategory == null || draftCategory.isEmpty()) {
            JOptionPane.showMessageDialog(panel,
                    "Please enter a category before creating questions.",
                    "Missing Category",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        openQuestionCreatorForNew();
    }

    private void handleEditQuestionButton(JPanel panel, JTextField categoryField) {
        int idx = questionList.getSelectedIndex();
        if (idx < 0) {
            JOptionPane.showMessageDialog(panel,
                    "Please select a question to edit.",
                    "No Selection",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        draftCategory = categoryField.getText().trim();
        openQuestionCreatorForEdit(idx);
    }

    private void handleDeleteQuestionButton(JPanel panel) {
        int idx = questionList.getSelectedIndex();
        if (idx < 0) {
            JOptionPane.showMessageDialog(panel,
                    "Please select a question to delete.",
                    "No Selection",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(panel,
                "Delete the selected question?",
                "Confirm Delete",
                JOptionPane.YES_NO_OPTION);

        if (confirm == JOptionPane.YES_OPTION) {
            draftQuestions.remove(idx);
            questionListModel.remove(idx);
            // Renumber display text
            for (int i = 0; i < draftQuestions.size(); i++) {
                QuizQuestion q = draftQuestions.get(i);
                questionListModel.set(i, "Q" + (i + 1) + ": " + q.question);
            }
        }
    }

    private void handleCreateQuizButton(JPanel panel, JTextField categoryField) {
        draftCategory = categoryField.getText().trim();
        if (draftCategory == null || draftCategory.isEmpty()) {
            JOptionPane.showMessageDialog(panel,
                    "Please enter a category.",
                    "Missing Category",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (draftQuestions.isEmpty()) {
            JOptionPane.showMessageDialog(panel,
                    "Please create at least one question.",
                    "No Questions",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        handler.createCategory(username, draftCategory);
        for (QuizQuestion q : draftQuestions) {
            handler.createQuestion(draftCategory, q.question);

            for (int i = 0; i < 4; i++) {
                if (i == q.correctIndex) {
                    handler.createAnswer(q.question, q.answers.get(i), true);
                } else {
                    handler.createAnswer(q.question, q.answers.get(i), false);
                }

            }
        }

        JOptionPane.showMessageDialog(mainPanel,
                "Quiz created with " + draftQuestions.size() + " questions.",
                "Quiz Created",
                JOptionPane.INFORMATION_MESSAGE);
        // clear the draft
        draftCategory = null;
        draftQuestions.clear();
        questionListModel.clear();

        cardLayout.show(mainPanel, "ModeSelect");
    }

    // -- Host Quiz Page --
    private void handleEndQuiz() {
        int result = JOptionPane.showConfirmDialog(
                this,
                "Are you sure you want to end the quiz for everyone?",
                "End Quiz",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);

        if (result != JOptionPane.YES_OPTION) {
            return;
        }

        // Stop polling UI updates
        if (quizPollTimer != null) {
            quizPollTimer.stop();
        }

        // Tell server to close the session
        int code = handler.closeSession(key);

        if (code >= 200 && code < 300) {
            JOptionPane.showMessageDialog(
                    this,
                    "Quiz ended and session closed.",
                    "Quiz Finished",
                    JOptionPane.INFORMATION_MESSAGE);
            // Go back to main menu / mode select
            cardLayout.show(mainPanel, "ModeSelect");
        } else {
            JOptionPane.showMessageDialog(
                    this,
                    "Failed to end quiz (HTTP " + code + ").",
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
            // optionally restart polling if you want
        }
    }

    private void handleQuizStart() {
        // Ensure category is selected
        String category = categoryList.getSelectedValue();
        if (category == null) {
            JOptionPane.showMessageDialog(
                    this,
                    "Please select a category before starting the quiz.",
                    "No Category Selected",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Read timer value safely
        int timeSeconds;
        try {
            timeSeconds = (int) timerSpinner.getValue();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(
                    this,
                    "Invalid timer value.",
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Send request to server
        int http = handler.startSession(key, username, category, timeSeconds);

        // Check server response
        if (http >= 200 && http < 300) {
            cardLayout.show(mainPanel, "HostQuizPanel");
            startHostStatePolling();
        } else {
            JOptionPane.showMessageDialog(
                    this,
                    "Failed to start quiz! Server returned HTTP " + http,
                    "Quiz Start Error",
                    JOptionPane.ERROR_MESSAGE);
        }

    }

    private void handleRefreshPlayerButton() {
        String keyText = sessionKeyField.getText().trim();
        if (!keyText.matches("\\d{6}")) {
            JOptionPane.showMessageDialog(this, "Invalid session key.");
            return;
        }
        int key = Integer.parseInt(keyText);
        List<String> names = handler.listPlayersInSession(key);
        playersModel.clear();

        if (!names.isEmpty()) {
            for (String n : names)
                playersModel.addElement(n);
        } else {
            playersModel.addElement("(no players found)");
        }
    }

    // -- Participant Quiz Panel --
    private void handleAnswerClick(int index) {
        // no answer mapped or already answered
        if (index < 0 || index >= answerIds.length)
            return;

        int answerId = answerIds[index];
        if (answerId == -1)
            return;

        if (answeredThisQuestion)
            return;

        answeredThisQuestion = true;

        // Disable buttons to prevent spam
        for (JButton btn : answerButtons) {
            btn.setEnabled(false);
        }

        new Thread(() -> {
            try {
                SubmitAnswerResult result = handler.submitAnswerDetailed(key, username, answerId);

                SwingUtilities.invokeLater(() -> {
                    if (result.httpCode >= 200 && result.httpCode < 300) {
                        // Color buttons based on correctness
                        int correctAnswerId = result.correctAnswerId;

                        // Highlight the correct answer in green
                        if (correctAnswerId != -1) {
                            for (int i = 0; i < answerIds.length; i++) {
                                if (answerIds[i] == correctAnswerId && answerButtons[i] != null) {
                                    answerButtons[i].setBackground(Color.GREEN);
                                    answerButtons[i].setForeground(Color.BLACK);
                                }
                            }
                        }

                        // Highlight the user's chosen answer
                        if (result.correct) {
                            if (answerButtons[index] != null) {
                                answerButtons[index].setBackground(Color.GREEN);
                                answerButtons[index].setForeground(Color.BLACK);
                            }
                        } else {
                            if (answerButtons[index] != null) {
                                answerButtons[index].setBackground(Color.RED);
                                answerButtons[index].setForeground(Color.WHITE);
                            }
                        }

                    } else {
                        JOptionPane.showMessageDialog(
                                this,
                                "Failed to submit answer (HTTP " + result.httpCode + ")",
                                "Error",
                                JOptionPane.ERROR_MESSAGE);
                    }
                });

            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }).start();
    }

    private void openQuestionCreatorForNew() {
        editingQuestionIndex = -1;
        qcQuestionField.setText("");

        for (int i = 0; i < qcAnswerFields.length; i++) {
            qcAnswerFields[i].setText("");
        }

        corrBtnGrp.clearSelection();

        cardLayout.show(mainPanel, "QuestionCreator");
    }

    private void openQuestionCreatorForEdit(int index) {
        editingQuestionIndex = index;
        QuizQuestion q = draftQuestions.get(index);

        qcQuestionField.setText(q.question);
        for (int i = 0; i < qcAnswerFields.length; i++) {
            qcAnswerFields[i].setText(q.answers.get(i));
            qcRadioButtons[i].setSelected(i == q.correctIndex);
        }
        cardLayout.show(mainPanel, "QuestionCreator");
    }

    // Check if the host started the quiz
    private void startHostStatePolling() {
        if (quizPollTimer != null && quizPollTimer.isRunning()) {
            quizPollTimer.stop();
        }

        hostStateFailureCount = 0;

        quizPollTimer = new javax.swing.Timer(1000, e -> {
            new Thread(() -> {
                try {
                    JSONObject state = handler.getSessionState(key);
                    if (state == null) {
                        hostStateFailureCount++;
                        if (hostStateFailureCount >= 3) {
                            SwingUtilities.invokeLater(this::handleServerDisconnected);
                        }
                        return;
                    }

                    hostStateFailureCount = 0;
                    SwingUtilities.invokeLater(() -> updateHostUIFromState(state));

                } catch (Exception ex) {
                    ex.printStackTrace();
                    hostStateFailureCount++;
                    if (hostStateFailureCount >= 3) {
                        SwingUtilities.invokeLater(this::handleServerDisconnected);
                    }
                }
            }).start();
        });

        quizPollTimer.start();
    }

    private void loadCategories() {
        try {
            java.util.List<String> cats = handler.getCategoriesFromUser(username);
            DefaultListModel<String> model = new DefaultListModel<>();
            if (cats != null) {
                for (String c : cats)
                    model.addElement(c);
            }
            categoryList.setModel(model);
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Failed to load categories.");
        }
    }

    public String getSelectedCategory() {
        if (categoryGroup != null && categoryGroup.getSelection() != null) {
            return categoryGroup.getSelection().getActionCommand();
        }
        return null;
    }

    // Update the Host UI
    private void updateHostUIFromState(JSONObject state) {
        boolean finished = state.optBoolean("finished", false);

        if (finished && !hostResultsShown) {
            hostResultsShown = true;

            // Stop polling
            if (quizPollTimer != null)
                quizPollTimer.stop();

            // Parse scores and update table
            fillHostScoresFromState(state);

            cardLayout.show(mainPanel, "HostResultsPage");
            return;
        }
        try {
            boolean started = state.optBoolean("started", false);
            int time = state.optInt("time", -1);

            quizTimerLabel.setText(started && time >= 0
                    ? "Time: " + time + "s"
                    : "Waiting to start...");

            // Players
            quizPlayersModel.clear();
            if (state.has("players")) {
                for (Object o : state.getJSONArray("players")) {
                    quizPlayersModel.addElement(String.valueOf(o));
                }
            }

            // Question & answers
            if (!started || state.isNull("question")) {
                quizQuestionArea.setText("Waiting for quiz to start or next question...");
                quizAnswersModel.clear();
            } else {
                String question = state.optString("question", "");
                quizQuestionArea.setText(question);

                quizAnswersModel.clear();
                if (state.has("answers")) {
                    var answers = state.getJSONArray("answers");
                    for (int i = 0; i < answers.length(); i++) {
                        JSONObject a = answers.getJSONObject(i);
                        String text = a.optString("text", "");
                        int id = a.optInt("id", -1);
                        quizAnswersModel.addElement("(" + id + ") " + text);
                    }
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    // Ping the serever for State info
    private void startParticipantStatePolling() {
        if (participantPollTimer != null && participantPollTimer.isRunning()) {
            participantPollTimer.stop();
        }

        participantResultsShown = false;
        answeredThisQuestion = false;
        lastQuestionText = null;
        myFinalScore = 0;
        lastScores.clear();
        participantStateFailureCount = 0;

        participantPollTimer = new javax.swing.Timer(1000, e -> {
            new Thread(() -> {
                try {
                    JSONObject state = handler.getSessionState(key);

                    if (state == null) {
                        // treat null as failure (e.g. HTTP error, exception, etc.)
                        participantStateFailureCount++;
                        if (participantStateFailureCount >= 3) {
                            // after 3 consecutive failures, give up
                            SwingUtilities.invokeLater(this::handleServerDisconnected);
                        }
                        return;
                    }
                    if (state.optBoolean("sessionClosed", false)) {
                        SwingUtilities.invokeLater(this::handleSessionClosedByHost);
                        return;
                    }

                    // got valid state → reset failure counter
                    participantStateFailureCount = 0;

                    SwingUtilities.invokeLater(() -> updateParticipantUIFromState(state));

                } catch (Exception ex) {
                    ex.printStackTrace();
                    participantStateFailureCount++;
                    if (participantStateFailureCount >= 3) {
                        SwingUtilities.invokeLater(this::handleServerDisconnected);
                    }
                }
            }).start();
        });

        participantPollTimer.start();
    }

    // Update Participant UI with state info
    private void updateParticipantUIFromState(JSONObject state) {
        boolean finished = state.optBoolean("finished", false);

        if (finished && !participantResultsShown) {
            participantResultsShown = true;

            // Parse scores
            lastScores.clear();
            myFinalScore = 0;

            if (state.has("scores")) {
                try {
                    JSONArray scoresArr = state.getJSONArray("scores");
                    for (int i = 0; i < scoresArr.length(); i++) {
                        JSONObject obj = scoresArr.getJSONObject(i);
                        String name = obj.optString("name", "");
                        int score = obj.optInt("score", 0);
                        lastScores.add(new ScoreEntry(name, score));

                        if (name.equals(username)) {
                            myFinalScore = score;
                        }
                    }
                    // Sort scores descending
                    lastScores.sort((a, b) -> Integer.compare(b.score, a.score));
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }

            // --- Update results UI labels ---
            updateParticipantResultsUI();

            if (participantPollTimer != null)
                participantPollTimer.stop();

            cardLayout.show(mainPanel, "ParticipantResultsPage");
            return;
        }

        try {
            boolean started = state.optBoolean("started", false);
            int time = state.optInt("time", -1);

            if (!started && !finished) {
                // quiz hasn't started yet
                participantTimerLabel.setText("Waiting for host to start...");
                participantQuestionArea.setText("Waiting for quiz to begin...");
                for (JButton btn : answerButtons) {
                    btn.setEnabled(false);
                    btn.setText("Answer");
                }
                return;
            }
            cardLayout.show(mainPanel, "participantQuizPage");
            // Show time, but don't use it to enable/disable
            if (time >= 0) {
                participantTimerLabel.setText("Time: " + time + "s");
            } else {
                participantTimerLabel.setText("Time: --");
            }

            // No active question -> clear UI
            if (state.isNull("question")) {
                participantQuestionArea.setText("No active question.");
                for (int i = 0; i < 4; i++) {
                    answerButtons[i].setText("");
                    answerButtons[i].setEnabled(false);
                    answerButtons[i].setVisible(false);
                    answerIds[i] = -1;
                }
                answeredThisQuestion = false;
                lastQuestionText = null;
                return;
            }

            String question = state.optString("question", "");
            participantQuestionArea.setText(question);

            // --- Detect NEW question ---
            boolean isNewQuestion = (lastQuestionText == null) || !lastQuestionText.equals(question);
            if (isNewQuestion) {
                answeredThisQuestion = false;
                lastQuestionText = question;

                // 🔄 Reset button colors for the new question
                for (int i = 0; i < 4; i++) {
                    if (answerButtons[i] != null && defaultAnswerBackgrounds[i] != null) {
                        answerButtons[i].setBackground(defaultAnswerBackgrounds[i]);
                    }
                    answerButtons[i].setForeground(Color.BLACK);
                }
            }

            // Answers
            JSONArray answersArr = state.optJSONArray("answers");

            // Clear previous answers
            for (int i = 0; i < 4; i++) {
                answerIds[i] = -1;
                answerButtons[i].setText("");
                answerButtons[i].setVisible(false);
                answerButtons[i].setEnabled(false);
            }

            if (answersArr != null) {
                int count = Math.min(4, answersArr.length());
                for (int i = 0; i < count; i++) {
                    JSONObject a = answersArr.getJSONObject(i);
                    int id = a.optInt("id", -1);
                    String text = a.optString("text", "");

                    answerIds[i] = id;
                    answerButtons[i].setText(text);
                    answerButtons[i].setVisible(true);

                    // ✅ Enable buttons when a NEW question appears
                    // or keep them disabled if user already answered
                    answerButtons[i].setEnabled(!answeredThisQuestion);
                }
            }

        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void updateParticipantResultsUI() {
        // Top 10
        for (int i = 0; i < 10; i++) {
            if (topScoreLabels[i] == null)
                continue; // in case called too early

            if (i < lastScores.size()) {
                ScoreEntry se = lastScores.get(i);
                topScoreLabels[i].setText(
                        String.format("%d) %s — %d point%s",
                                i + 1,
                                se.name,
                                se.score,
                                (se.score == 1 ? "" : "s")));
            } else {
                topScoreLabels[i].setText("—");
            }
        }

        if (myScoreLabel != null) {
            myScoreLabel.setText(String.format("Your score: %d", myFinalScore));
        }
    }

    private void fillHostScoresFromState(JSONObject state) {
        if (hostScoresTableModel == null)
            return;

        hostScoresTableModel.setRowCount(0); // clear old data
        lastScores.clear();

        if (!state.has("scores")) {
            return;
        }

        try {
            JSONArray arr = state.getJSONArray("scores");
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                String name = obj.optString("name", "");
                int score = obj.optInt("score", 0);
                lastScores.add(new ScoreEntry(name, score));
            }

            // Sort by score descending
            lastScores.sort((a, b) -> Integer.compare(b.score, a.score));

            // Populate table
            for (ScoreEntry se : lastScores) {
                hostScoresTableModel.addRow(new Object[] { se.name, se.score });
            }

        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    // --- Network Handling ---
    private void handleAppExit() {
        // If they are in a session as host or player, you can also call leaveSession:
        if (key > 0) {
            handler.leaveSession(key, username);
        }

        // If user is logged in, try to logout
        if (username != null) {
            try {
                handler.logout(username);
                username = "";
                password = "";
            } catch (Exception ex) {
                // Don't block app exit if logout fails
                ex.printStackTrace();
            }
        }

        // close the app
        dispose();
        System.exit(0);
    }

    private void handleServerDisconnected() {
        // Stop timers
        if (participantPollTimer != null && participantPollTimer.isRunning()) {
            participantPollTimer.stop();
        }
        if (quizPollTimer != null && quizPollTimer.isRunning()) {
            quizPollTimer.stop();
        }

        key = -1;

        // clear other state
        hostResultsShown = false;
        participantResultsShown = false;
        lastScores.clear();
        answeredThisQuestion = false;
        lastQuestionText = null;

        // Inform the user
        JOptionPane.showMessageDialog(
                this,
                "Lost connection to the server.\nYou will be returned to the login screen.",
                "Connection Lost",
                JOptionPane.ERROR_MESSAGE);

        // try to logout
        try {
            if (username != null && !username.isEmpty()) {
                handler.logout(username);
            }
        } catch (Exception ex) {
            // ignore, we're already disconnected
        }

        // Reset login data
        username = "";
        password = "";

        // Show login/welcome screen
        cardLayout.show(mainPanel, "Welcome");
    }

    private void handleSessionClosedByHost() {
        if (participantPollTimer != null && participantPollTimer.isRunning()) {
            participantPollTimer.stop();
        }

        key = -1;

        JOptionPane.showMessageDialog(
                this,
                "The host has ended the quiz or closed the session.",
                "Session Ended",
                JOptionPane.INFORMATION_MESSAGE);

        cardLayout.show(mainPanel, "ModeSelect");
    }
}
