package server;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;

public class AppServer {
    public static void main(String[] args) throws Exception {
        int port = 3050;
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        APICallHandler.registerRoutes(server);

        server.setExecutor(null);
        System.out.println("Server running at http://192.168.254.161:" + port);
        server.start();

    }
}

/*
 * ro
 * CREATE USER:
 * curl -X POST http://192.168.254.161:3050/users -H
 * "Content-Type: application/x-www-form-urlencoded" -d
 * "username=username&password=password"
 * 
 * 
 * LOGIN USER: curl -X POST http://192.168.254.161:3050/login \ -H
 * "Content-Type: application/x-www-form-urlencoded" \ -d
 * "username=username&password=password"
 * 
 * Session Calls:
 * 
 * Create a new session: curl -X POST http://192.168.254.161:3050/sessions \
 * -H "Content-Type: application/x-www-form-urlencoded" \
 * -d "username=alice"
 * 
 * Join a session:
 * curl -X POST http://192.168.254.161:3050/sessions/083201/join \
 * -H "Content-Type: application/x-www-form-urlencoded" \
 * -d "username=bob"
 * 
 * List Players in a session:
 * curl http://192.168.254.161:3050/sessions/083201
 * 
 * List active sessions:
 * curl http://192.168.254.161:3050/sessions
 * 
 * Delete or close a session:
 * curl -X DELETE http://192.168.254.161:3050/sessions/083201
 * 
 * 
 * Create Quiz:
 * 
 * Create Category: curl -X POST
 * http://192.168.254.161:3050/users/sebastian/categories \
 * -H "Content-Type: application/x-www-form-urlencoded" \
 * -d "category=Science"
 * 
 * Create Question: curl -X POST
 * http://192.168.254.161:3050/categories/Math/question \
 * -H "Content-Type: application/x-www-form-urlencoded" \
 * -d "question=What%20is%202+2%3F"
 * 
 * Create Answer: curl -X POST
 * "http://192.168.254.161:3050/questions/What%20is%202%2B2%3F/answers" \
 * -H "Content-Type: application/x-www-form-urlencoded" \
 * -d "answerText=4&isCorrect=true"
 * 
 * 
 * 
 */