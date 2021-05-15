import com.google.gson.Gson;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;

import javax.security.auth.login.LoginException;
import java.awt.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main {
    public static JDA jda;

    public static void main(String[] args) {
        String token = System.getenv("DISCORD_TOKEN");

        //Start bot
        try {
            jda = JDABuilder.createDefault(token).build();
        } catch (LoginException e) {
            System.out.println("Unable to login to Discord :/");
            e.printStackTrace();
            return;
        }

        //Start HTTP server
        try {
            startHttpServer();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    static void startHttpServer() throws IOException {
        //Start HTTP Server
        HttpServer server = HttpServer.create(new InetSocketAddress(6969), 0);
        server.createContext("/upload", new LogHandler());
        server.setExecutor(null); // creates a default executor
        server.start();

    }

    static class LogHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) {
            Headers headers = exchange.getRequestHeaders();

            //Authorize. If fails, return
            try {
                if (!authorize(exchange, headers))
                    return;

                //Read data from request
                Map<String, Object> rootJson = parseData(exchange);
                //Send log
                sendLog(rootJson);

                respond(exchange, 200, "ty for log <3");
            } catch (IOException e) {
                respond(exchange, 500, "Something went wrong, sorry :/");
                e.printStackTrace();
            }

            exchange.close();
        }

        boolean authorize(HttpExchange exchange, Headers headers) throws IOException {
            // Make sure it's coming from FR ;)
            String correctAuthString = "CoocooFroggy rocks";
            List<String> authHeaders = headers.get("authorization");
            if (authHeaders == null) {
                respond(exchange, 403, "Provide an auth header :/");
                return false;
            }
            String providedAuthString = authHeaders.get(0);
            if (!providedAuthString.equals(correctAuthString)) {
                respond(exchange, 403, "Wrong auth header :/");
                return false;
            }
            //Otherwise it's correct
            return true;
        }

        Map<String, Object> parseData(HttpExchange exchange) throws IOException {
            BufferedReader reader = new BufferedReader(new InputStreamReader(exchange.getRequestBody()));

            StringBuilder bodyBuilder = new StringBuilder(); // bodyBuilder [strong.jpg]
            int charInteger;
            while ((charInteger = reader.read()) != -1) {
                char character = (char) charInteger;
                bodyBuilder.append(character);
            }

            Gson gson = new Gson();
            return gson.fromJson(bodyBuilder.toString(), Map.class);
        }

        Pattern pattern = Pattern.compile("what=(.*)|Done: restoring succeeded!");

        void sendLog(Map<String, Object> rootJson) throws IOException {
            String discord = (String) rootJson.get("discord");
            String logName = (String) rootJson.get("logName");
            String fullLog = (String) rootJson.get("log");
            String command = (String) rootJson.get("command");
            String guiVersion = (String) rootJson.get("guiVersion");
            String status = "None";
            Color embedColor = null;

            File logDirectory = new File("logs/");
            if (!logDirectory.exists())
                logDirectory.mkdir();

            if (!logName.endsWith(".txt"))
                logName += ".txt";
            String logPath = "logs/" + logName;

            // Parse error/success message
            Matcher matcher = pattern.matcher(fullLog);
            // "while" will get the last match. "if" will get the first match.
            while (matcher.find()) {
                //If what=message is not null
                if (matcher.group(1) != null) {
                    //Probably an error
                    status = matcher.group(1);
                    embedColor = new Color(233, 56, 56);
                } else {
                    //Otherwise status is just the match then
                    //Probably success
                    status = matcher.group(0);
                    embedColor = new Color(98, 201, 73);
                }
            }

            FileWriter writer = new FileWriter(logPath);
            writer.write(fullLog);
            writer.close();

            File fileToSend = new File(logPath);

            if (discord.equals("")) {
                discord = "None";
            }
            if (command == null) {
                command = "None (or GUI version too old)";
            }
            if (guiVersion == null) {
                guiVersion = "None (or GUI version too old)";
            }

            //Build a nice looking embed to appear Cryptic's old eyes
            EmbedBuilder embedBuilder = new EmbedBuilder();
            embedBuilder.setAuthor("User: " + discord);
            embedBuilder.addField("Message", "```\n" + status + "\n```", false);
            embedBuilder.addField("Command", "```\n" + command + "\n```", false);
            embedBuilder.setFooter("FR-GUI version: " + guiVersion);
            if (embedColor != null)
                embedBuilder.setColor(embedColor);

            // #logs in personal server
            jda.getTextChannelById("818879231772983357").sendMessage(embedBuilder.build())
                    .addFile(fileToSend).complete();
            // #logs in Cryptic FDR Bureau server
            jda.getTextChannelById("842197751316086804").sendMessage(embedBuilder.build())
                    .addFile(fileToSend).complete();

            //Delete the file
            fileToSend.delete();
        }

        void respond(HttpExchange exchange, int responseCode, String response) {
            try {
                exchange.sendResponseHeaders(responseCode, response.length());
                OutputStream os = exchange.getResponseBody();
                os.write(response.getBytes());
                os.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
