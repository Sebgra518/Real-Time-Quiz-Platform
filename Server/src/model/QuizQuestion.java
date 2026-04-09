package model;

import java.util.*;

public final class QuizQuestion {
    public final int id;
    public final String text;
    public final List<QuizAnswer> answers;

    public QuizQuestion(int id, String text, List<QuizAnswer> answers) {
        this.id = id;
        this.text = text;
        this.answers = answers;
    }
}