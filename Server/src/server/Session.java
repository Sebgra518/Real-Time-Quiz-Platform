package server;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import model.*;

public final class Session {
    final int key;
    boolean started;
    boolean finished = false; // NEW

    // How much time each question gets by default (host can change this)
    int timePerQuestionSeconds = 15;

    // Current countdown (changes as time passes)
    volatile int currentTime = timePerQuestionSeconds;

    final List<Player> players = new CopyOnWriteArrayList<>();
    final String host;

    // quiz-related fields
    String category;
    List<QuizQuestion> questions = new ArrayList<>();
    int currentQuestionIndex = -1;
    boolean acceptingAnswers = false;

    Session(int key, String host) {
        this.key = key;
        this.host = host; // can be null if no host
        this.started = false;
        this.finished = false;
    }

    public void start() {
        this.started = true;
    }

    public void addPlayer(String name) {
        players.add(new Player(name));
    }

    public boolean hasPlayer(String name) {
        for (Player p : players) {
            if (p.name.equals(name))
                return true;
        }
        return false;
    }

    public boolean removePlayer(String name) {
        for (int i = 0; i < players.size(); i++) {
            Player p = players.get(i);
            if (p.name.equals(name)) {
                players.remove(i);
                return true;
            }
        }
        return false;
    }

    public List<String> getPlayerNames() {
        List<String> names = new ArrayList<>(players.size());
        for (Player p : players) {
            names.add(p.name);
        }
        return names;
    }

    public void addScore(String name, int delta) {
        for (Player p : players) {
            if (p.name.equals(name)) {
                p.score += delta;
                break;
            }
        }
    }

    public void setTimePerQuestionSeconds(int seconds) {
        if (seconds > 0) {
            this.timePerQuestionSeconds = seconds;
        }
    }

    public void startCategory(String category, List<QuizQuestion> questions) {
        startCategory(category, questions, this.timePerQuestionSeconds);
    }

    public void startCategory(String category, List<QuizQuestion> questions, int timePerQuestionSeconds) {
        if (timePerQuestionSeconds > 0) {
            this.timePerQuestionSeconds = timePerQuestionSeconds;
        }

        this.category = category;
        this.questions.clear();
        this.questions.addAll(questions);
        this.currentQuestionIndex = 0;
        this.started = true;
        this.acceptingAnswers = true;
        this.finished = false;
        this.currentTime = this.timePerQuestionSeconds; // first question countdown
        // reset answer state
        for (Player p : players) {
            p.answeredThisQuestion = false;
        }
    }

    public QuizQuestion getCurrentQuestion() {
        if (currentQuestionIndex < 0 || currentQuestionIndex >= questions.size())
            return null;
        return questions.get(currentQuestionIndex);
    }

    public void nextQuestion() {
        currentQuestionIndex++;
        if (currentQuestionIndex >= questions.size()) {
            acceptingAnswers = false;
            started = false;
            finished = true;
        } else {
            acceptingAnswers = true;
            currentTime = timePerQuestionSeconds;
            for (Player p : players) {
                p.answeredThisQuestion = false;
            }
        }
    }

    public void tick() {
        if (!started || finished)
            return;

        if (currentTime > 0) {
            currentTime--;
            if (currentTime == 0) {
                acceptingAnswers = false; // lock answers
                // Automatically move on when time is up
                if (currentQuestionIndex < questions.size() - 1) {
                    System.out.println("next question!");
                    nextQuestion(); // will reset currentTime, started, acceptingAnswers, etc.
                } else {
                    // last question -> quiz over
                    System.out.println("Finished!");
                    finished = true;
                    started = false;
                }
            }
        }
    }

}
