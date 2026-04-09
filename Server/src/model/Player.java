package model;

public final class Player {
    public final String name;
    public int score;
    public boolean answeredThisQuestion;

    public Player(String name) {
        this.name = name;
        this.score = 0;
        this.answeredThisQuestion = false;
    }
}