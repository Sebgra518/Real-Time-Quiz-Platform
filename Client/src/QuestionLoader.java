package src;

import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import org.json.*;

class QuestionLoader {

    public static List<Question> loadQuestions(String filePath) {
        List<Question> questions = new ArrayList<>();
        try {

            String content = new String(Files.readAllBytes(Paths.get(filePath)));// Get JSON FILE
            JSONArray arr = new JSONArray(content);// Turn JSON Contents into a JSONArray

            for (int i = 0; i < arr.length(); i++) {

                JSONObject obj = arr.getJSONObject(i);
                String question = obj.getString("question");
                JSONArray opts = obj.getJSONArray("options");
                List<String> options = new ArrayList<>(); // Create a seperate arraylist or options

                for (int j = 0; j < opts.length(); j++) {
                    options.add(opts.getString(j));
                }
                String answer = obj.getString("answer");

                questions.add(new Question(question, options, answer));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return questions;
    }
}
