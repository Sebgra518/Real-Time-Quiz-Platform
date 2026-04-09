package model;

public class QuizQuestion {
    public String question;
    public java.util.List<String> answers;
    public int correctIndex; // 0–3

    public QuizQuestion(String question, java.util.List<String> answers, int correctIndex) {
        this.question = question;
        this.answers = answers;
        this.correctIndex = correctIndex;
    }
}