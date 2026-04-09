package model;

public final class QuizAnswer {
    public final int id;
    public final String text;
    public final boolean correct;

    public QuizAnswer(int id, String text, boolean correct) {
        this.id = id;
        this.text = text;
        this.correct = correct;
    }
}