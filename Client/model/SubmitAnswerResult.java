package model;

public class SubmitAnswerResult {
    public final int httpCode;
    public final boolean correct;
    public final int correctAnswerId;

    public SubmitAnswerResult(int httpCode, boolean correct, int correctAnswerId) {
        this.httpCode = httpCode;
        this.correct = correct;
        this.correctAnswerId = correctAnswerId;
    }
}
