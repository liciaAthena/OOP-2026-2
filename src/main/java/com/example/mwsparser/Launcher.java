package com.example.mwsparser;

import javafx.application.Application;
import java.io.*;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.file.Files;
import java.nio.file.Paths;

import java.sql.SQLException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import java.sql.*;

class CCContent {
    // Legal characters regex - see https://www.mediawiki.org/wiki/Manual:$wgLegalTitleChars
    private static final Pattern blacklist = Pattern.compile("^.*(\\[|\\]|\\{|\\}|\\||#|<|>).*$/gim");

    public String id, type, name, wikiname, color, gradient, description, flavorText, dateAdded, credits, feature;
    public Boolean published;

    public CCContent(JSONObject data) {
        id = data.getString("ID");
        type = data.getString("Type");
        name = data.getString("Name");

        Matcher m = blacklist.matcher(name);
        wikiname = (m.matches()) ? "PROBLEMNAME" : "";

        color = data.optString("Color");
        gradient = data.optString("Gradient");
        description = data.optString("Description");
        flavorText = data.optString("FlavorText");

        dateAdded = data.optString("DateAdded");
        credits = data.optString("Credits");
        feature = (data.optBoolean("DeveloperExclusive") ? "DoNotPublish" : data.optString("Feature"));

        published = false;
    }

    public void publishDatabase() {
        try {
            String sql = "SELECT * FROM cccontent WHERE id='"+id+"'";
            ResultSet resultSet = DatabaseConnection.statement.executeQuery(sql);
            if (!resultSet.next()) {
                // no row in database has this id
                // TODO - create new row in db
                // addDatabase();
                System.out.println("Does not exist in DB");
            } else {
                // this id exists in the database
                // TODO - find if there is a row in db that matches the data of the json fully
                // if (compareDatabase();) {
                    // TODO - update row in db
                    // updateDatabase();
                // }
            }
        }

        catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}

class CCBlock extends CCContent {
    public String tier, counterpart, tags;
    public Boolean flawless;
    public List<CCGeneration> generation = new ArrayList<>();

    public CCBlock(JSONObject data) {
        super(data);
        tier = data.optString("Tier");
        flawless = !Objects.equals(data.optString("OriginalVariant"), "");
        counterpart = data.optString("Flawless", data.optString("OriginalVariant"));
        JSONArray generation = data.optJSONArray("Generation");
        if (generation != null) {
            for (int i = 0 ; i < generation.length(); i++) {
                JSONObject obj = generation.getJSONObject(i);
                CCGeneration entry = new CCGeneration(obj, this.id, i);
                this.generation.add(entry);
            }
        }
    }
}

class CCGeneration {
    public String id, zone, biome;
    public int genId, chance, flawlessChance, minDepth, maxDepth;

    public CCGeneration(JSONObject data, String id, int i) {
        this.id = id;
        genId = i;
        zone = data.optString("Zone");
        biome = data.optString("Biome");
        chance = data.optInt("Chance");
        flawlessChance = data.optInt("FlawlessChance");
        minDepth = data.optInt("MinDepth");
        maxDepth = data.optInt("MaxDepth");
        System.out.print("eek");
    }
}

class CCEquipment extends CCContent {
    public String tier, component, stats;

    public CCEquipment(JSONObject data) {
        super(data);
        tier = data.optString("Tier");
        component = data.optString("Component");
        // TODO - stats
    }
}

class DatabaseConnection {
    public static Connection connection;
    public static Statement statement;
    private static String username;
    private static String password;

    public static void Connect() {
        // TODO - create database if it doesn't exist
        BufferedReader r = new BufferedReader(new InputStreamReader(System.in));
        try {
            if (connection != null && !connection.isValid(2)) {
                return; //connection already established
            }
            if (username == null) {
                //TODO - save database credentials and path in an env file on first time launching
                System.out.println("Insert database username:");
                username = r.readLine();
                System.out.println("Insert database password:");
                password = r.readLine();
            }
            connection = DriverManager.getConnection(
                    "jdbc:mysql://localhost:3306/cccontent", username, password
            );
            System.out.println("Connected to database");
            statement = connection.createStatement();
            r.close();
        }
        catch(IOException e) {
            e.printStackTrace();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public static void Close() {
        try {
            if (connection.isValid(2)) {
                connection.close();
                statement.close();
            }
        }
        catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}

public class Launcher {
    public static void main(String[] args) {
        //Application.launch(HelloApplication.class, args);

        //TODO - temp console application
        //DatabaseConnection.Connect();
        BufferedReader r = new BufferedReader(new InputStreamReader(System.in));
        System.out.println("MWParser\n" +
                "1-Parse JSON\n" +
                "2-Rename problem entries\n" +
                "3-Publish to Miraheze");
        try {
            String s = r.readLine();
            switch (s) {
                case "1":
                    parseJson();
                    break;
                default:
                    break;
            }
            r.close();
        }

        catch(IOException e) {
            e.printStackTrace();
        }
    }

    public static void parseJson() {
        BufferedReader r = new BufferedReader(new InputStreamReader(System.in));
        System.out.println("Insert file directory of JSON:");
        try {
            //String file = Files.readString(Paths.get(""));
            String s = r.readLine();
            String file = Files.readString(Paths.get(String.valueOf(s)));
            JSONObject json = new JSONObject(file);

            DatabaseConnection.Connect();
            Map<String,Object> map = json.toMap();
            for (Map.Entry<String,Object> mapElement : map.entrySet()) {
                String key = mapElement.getKey();
                JSONObject data = json.getJSONObject(key);
                CCContent obj = entryType(data);
            }
            DatabaseConnection.Close();
        }
        catch(IOException e) {
            e.printStackTrace();
        }
    }

    public static CCContent entryType(JSONObject data) {
        String type = data.optString("Type");
        // TODO - figure out a less ugly way to do this
        if (Objects.equals(type, "Block")) {
            CCBlock entry = new CCBlock(data);
            entry.publishDatabase();
            return entry;
        }
        if (Objects.equals(type, "Component") || Objects.equals(type, "Equipment")) {
            CCEquipment entry = new CCEquipment(data);
            entry.publishDatabase();
            return entry;
        }
        CCContent entry = new CCContent(data);
        entry.publishDatabase();
        return entry;
    }

    public static void renameEntries() {
        //TODO
    }

    public static void publishMira() {
        //TODO
    }
}
