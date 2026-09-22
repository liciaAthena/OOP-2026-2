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

    public String id, type, name, wikiname, color, gradient, description, flavorText, dateAdded, credits, feature, oldwikiname;
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
        oldwikiname = "";
    }

    public void checkDatabase() {
        System.out.println(name + "---------");
        //if (!(DatabaseConnection.inDatabase(this))) {
        if (!contentInDatabase()) {
            // no row in database has this id
            System.out.println("Base data does not exist in DB");
            addDatabase();
        } else if (!databaseEqual()) {
            // this id exists in the database and database entry differs from it
            System.out.println("Base data is different");
            updateDatabase();
        } else {
            // data exists in db with no differences
            System.out.println("Base data exists in DB");
        }
    }

    public boolean contentInDatabase() {
        try {
            String sql = "SELECT id FROM ccContent WHERE id = ?";
            PreparedStatement ps = DatabaseConnection.connection.prepareStatement(sql);
            ps.setString(1, id);
            ResultSet resultSet = ps.executeQuery();
            return resultSet.next();
        }
        catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean databaseEqual() {
        try {
            String sql = "SELECT * FROM ccContent WHERE id = ? AND type = ? AND name = ? AND color = ? AND gradient = ? AND description = ? AND flavorText = ? AND feature = ?";
            PreparedStatement ps = DatabaseConnection.connection.prepareStatement(sql);
            ps.setString(1, id);
            ps.setString(2, type);
            ps.setString(3, name);
            ps.setString(4, color);
            ps.setString(5, gradient);
            ps.setString(6, description);
            ps.setString(7, flavorText);
            ps.setString(8, feature);
            ResultSet resultSet = ps.executeQuery();
            return resultSet.next();
        }
        catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void addDatabase() {
        try {
            String sql = "INSERT INTO ccContent (id, type, name, wikiname, color, gradient, description, flavorText, dateAdded, credits, feature, published) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            PreparedStatement ps = DatabaseConnection.connection.prepareStatement(sql);
            ps.setString(1, id);
            ps.setString(2, type);
            ps.setString(3, name);
            ps.setString(4, wikiname);
            ps.setString(5, color);
            ps.setString(6, gradient);
            ps.setString(7, description);
            ps.setString(8, flavorText);
            ps.setString(9, dateAdded);
            ps.setString(10, credits);
            ps.setString(11, feature);
            ps.setBoolean(12, false);
            ps.executeUpdate();
        }

        catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void updateDatabase() {
        try {
            String sqlq = "SELECT name, wikiname, published, oldwikiname FROM ccContent WHERE id = ?";
            PreparedStatement psq = DatabaseConnection.connection.prepareStatement(sqlq);
            psq.setString(1, id);
            ResultSet resultSet = psq.executeQuery();
            resultSet.next();
            String dbname = resultSet.getString("name"); //Spinner
            String dbwikiname = resultSet.getString("wikiname"); //blank
            boolean dbpublished = resultSet.getBoolean("published"); //false
            String dboldwikiname = resultSet.getString("oldwikiname"); //blank
            if (!Objects.equals(dbname, name)) {
                // content has been renamed since last added to the database
                System.out.println("renamed content " + dbwikiname);
                if (dbpublished) {
                    // need to move the page from oldwikiname to new wikiname/name next publish
                    oldwikiname = !Objects.equals(dbwikiname, "") ? dbwikiname : dbname;
                } else {
                    // page still hasn't been moved since last rename, preserve old wiki name
                    oldwikiname = dboldwikiname;
                }
                Matcher m = blacklist.matcher(name);
                wikiname = (m.matches()) ? "PROBLEMNAME" : "";
                // TODO - handle moving pages for renamed content
            }

            String sql = "UPDATE ccContent SET type = ?, name = ?, wikiname = ?, color = ?, gradient = ?, description = ?, flavorText = ?, dateAdded = ?, credits = ?, feature = ?, published = ?, oldwikiname = ? WHERE id = ?";
            PreparedStatement ps = DatabaseConnection.connection.prepareStatement(sql);
            ps.setString(1, type);
            ps.setString(2, name);
            ps.setString(3, wikiname);
            ps.setString(4, color);
            ps.setString(5, gradient);
            ps.setString(6, description);
            ps.setString(7, flavorText);
            ps.setString(8, dateAdded);
            ps.setString(9, credits);
            ps.setString(10, feature);
            ps.setBoolean(11, false);
            ps.setString(12, oldwikiname);
            ps.setString(13, id);
            ps.executeUpdate();
            oldwikiname = "";
        }

        catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public static void setPublished(String id, boolean bool) {
        try {
            String sql = "UPDATE ccContent SET published = ? WHERE id = ?";
            PreparedStatement ps = DatabaseConnection.connection.prepareStatement(sql);
            ps.setBoolean(1, bool);
            ps.setString(2, id);
            ps.executeUpdate();
        }

        catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}

class CCBlock extends CCContent {
    public String tier, counterpart;
    public JSONObject tags;
    public Boolean flawless;
    public List<CCGeneration> generation = new ArrayList<>();

    public CCBlock(JSONObject data) {
        super(data);
        tier = data.optString("Tier");
        flawless = !Objects.equals(data.optString("OriginalVariant"), "");
        counterpart = data.optString("Flawless", data.optString("OriginalVariant"));

        tags = new JSONObject();
        JSONArray tagsArray = data.optJSONArray("Tags");
        JSONObject tagDescriptions = data.optJSONObject("TagDescriptions");
        if (tagsArray != null) {
            tagsArray.forEach(item -> {
                String key = item.toString();
                String description = tagDescriptions != null ? tagDescriptions.optString(key, null) : null;
                tags.put(key, description == null ? JSONObject.NULL : description);
            });
        }

        JSONArray generationArray = data.optJSONArray("Generation");
        if (generationArray != null) {
            for (int i = 1 ; i <= generationArray.length(); i++) {
                JSONObject obj = generationArray.getJSONObject(i-1);
                CCGeneration entry = new CCGeneration(obj, this.id, i);
                this.generation.add(entry);
            }
        }
    }

    public void checkDatabase() {
        // check base data
        super.checkDatabase();

        try {
            // comparing amount of generation objects to amount of generation data in database
            String sql = "SELECT MAX(genId) AS MAXID FROM generationData WHERE id = ?";
            PreparedStatement ps = DatabaseConnection.connection.prepareStatement(sql);
            ps.setString(1, id);
            ResultSet resultSet = ps.executeQuery();
            if (resultSet.next()) {
                System.out.println(resultSet);
                int genMax = generation.size();
                int genMaxDb = resultSet.getInt("MAXID");
                if (genMax < genMaxDb) {
                    // generation entries have been removed and need to be deleted off the database
                    for (int i = genMax+1 ; i <= genMaxDb; i++) {
                        // remove from db where id = id genId = i
                        System.out.println("Removed id " + id + "gentry id " + i);
                        CCGeneration.removeDatabase(id, i);
                    }
                }
            }
        }
        catch (SQLException e) {
            throw new RuntimeException(e);
        }
        if (generation != null) {
            generation.forEach(CCGeneration::checkDatabase);
        }
    }

    public boolean databaseEqual() {
        try {
            //base data equivalence
            boolean base = super.databaseEqual();
            //block data equivalence
            String sql = "SELECT * FROM blockData WHERE id = ? AND tier = ? AND flawless = ? AND counterpart = ?";
            PreparedStatement ps = DatabaseConnection.connection.prepareStatement(sql);
            ps.setString(1, this.id);
            ps.setString(2, this.tier);
            ps.setBoolean(3, this.flawless);
            ps.setString(4, this.counterpart);
            ResultSet resultSet = ps.executeQuery();
            //tags need to be checked separately since the order of the keys might not be equivalent
            boolean tagsequal = true;
            sql = "SELECT tags FROM blockData WHERE id = ? AND tags IS NOT NULL";
            PreparedStatement ps2 = DatabaseConnection.connection.prepareStatement(sql);
            ps2.setString(1, id);
            ResultSet resultSet2 = ps2.executeQuery();
            if (resultSet2.next()) {
                String dbtags = resultSet2.getString("tags");
                JSONObject tagsDB = new JSONObject(dbtags);
                tagsequal = tagsDB.similar(tags);
            }

            return (resultSet.next() && base && tagsequal);
        }
        catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void addDatabase() {
        System.out.println("contentInDatabase " + contentInDatabase());
        if (!contentInDatabase()) {
            super.addDatabase();
        }
        try {
            String sql = "INSERT INTO blockData (id, tier, flawless, counterpart, tags) VALUES (?, ?, ?, ?, ?)";
            PreparedStatement ps = DatabaseConnection.connection.prepareStatement(sql);
            ps.setString(1, id);
            ps.setString(2, tier);
            ps.setBoolean(3, flawless);
            ps.setString(4, counterpart);
            ps.setString(5, tags.toString());
            ps.executeUpdate();
        }
        catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void updateDatabase() {
        super.updateDatabase();
        try {
            String sql = "UPDATE blockData SET tier = ?, flawless = ?, counterpart = ?, tags = ? WHERE id = ?";
            PreparedStatement ps = DatabaseConnection.connection.prepareStatement(sql);
            ps.setString(1, tier);
            ps.setBoolean(2, flawless);
            ps.setString(3, counterpart);
            ps.setString(4, tags.toString());
            ps.setString(5, id);
            ps.executeUpdate();
        }

        catch (SQLException e) {
            throw new RuntimeException(e);
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
    }

    public void checkDatabase() {
        if (!inDatabase()) {
            // no row in database has this id
            System.out.println("Generation data does not exist in DB");
            addDatabase();
            CCContent.setPublished(id, false);
        } else if (!databaseEqual()) {
            // this gen entry exists in the database and database entry differs from it
            System.out.println("Generation data is different");
            updateDatabase();
            CCContent.setPublished(id, false);
        } else {
            // data exists in db with no differences
            System.out.println("Generation data exists in DB");
        }
    }

    public boolean inDatabase() {
        try {
            System.out.println("inDatabase");
            String sql = "SELECT id FROM generationData WHERE id = ? AND genId = ?";
            PreparedStatement ps = DatabaseConnection.connection.prepareStatement(sql);
            ps.setString(1, id);
            ps.setInt(2, genId);
            ResultSet resultSet = ps.executeQuery();
            return resultSet.next();
        }
        catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean databaseEqual() {
        try {
            System.out.println("databaseEqual");
            String sql = "SELECT * FROM generationData WHERE id = ? AND genId = ? AND zone = ? AND biome = ? AND chance = ? AND flawlessChance = ? and minDepth = ? and maxDepth = ?";
            PreparedStatement ps = DatabaseConnection.connection.prepareStatement(sql);
            ps.setString(1, id);
            ps.setInt(2, genId);
            ps.setString(3, zone);
            ps.setString(4, biome);
            ps.setInt(5, chance);
            ps.setInt(6, flawlessChance);
            ps.setInt(7, minDepth);
            ps.setInt(8, maxDepth);
            ResultSet resultSet = ps.executeQuery();

            return (resultSet.next());
        }
        catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void addDatabase() {
        try {
            String sql = "INSERT INTO generationData (id, genId, zone, biome, chance, flawlessChance, minDepth, maxDepth) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
            PreparedStatement ps = DatabaseConnection.connection.prepareStatement(sql);
            ps.setString(1, id);
            ps.setInt(2, genId);
            ps.setString(3, zone);
            ps.setString(4, biome);
            ps.setInt(5, chance);
            ps.setInt(6, flawlessChance);
            ps.setInt(7, minDepth);
            ps.setInt(8, maxDepth);
            ps.executeUpdate();
        }

        catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void updateDatabase() {
        //setPublished(false);
        try {
            String sql = "UPDATE generationData SET zone = ?, biome = ?, chance = ?, flawlessChance = ?, minDepth = ?, maxDepth = ? WHERE id = ? AND genId = ?";
            PreparedStatement ps = DatabaseConnection.connection.prepareStatement(sql);
            ps.setString(1, zone);
            ps.setString(2, biome);
            ps.setInt(3, chance);
            ps.setInt(4, flawlessChance);
            ps.setInt(5, minDepth);
            ps.setInt(6, maxDepth);
            ps.setString(7, id);
            ps.setInt(8, genId);
            ps.executeUpdate();
        }

        catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public static void removeDatabase(String id, int genId) {
        try {
            String sql = "DELETE FROM generationData WHERE id = ? AND genId = ?";
            PreparedStatement ps = DatabaseConnection.connection.prepareStatement(sql);
            ps.setString(1, id);
            ps.setInt(2, genId);
            ps.executeUpdate();
        }
        catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}

class CCEquipment extends CCContent {
    public String tier, component;
    public JSONObject stats;

    public CCEquipment(JSONObject data) {
        super(data);
        tier = data.optString("Tier");
        component = data.optString("Component");
        stats = data.optJSONObject("Stats", new JSONObject("{}"));
    }

    public boolean databaseEqual() {
        try {
            //base data equivalence
            boolean base = super.databaseEqual();
            //equipment data equivalence
            String sql = "SELECT * FROM equipmentData WHERE id = ? AND tier = ? AND component = ?";
            PreparedStatement ps = DatabaseConnection.connection.prepareStatement(sql);
            ps.setString(1, this.id);
            ps.setString(2, this.tier);
            ps.setString(3, this.component);
            ResultSet resultSet = ps.executeQuery();
            //stats need to be checked separately since the order of the keys might not be equivalent
            boolean statsequal = true;
            sql = "SELECT stats FROM equipmentData WHERE id = ? AND stats IS NOT NULL";
            PreparedStatement ps2 = DatabaseConnection.connection.prepareStatement(sql);
            ps2.setString(1, id);
            ResultSet resultSet2 = ps2.executeQuery();
            if (resultSet2.next()) {
                String dbstats = resultSet2.getString("stats");
                JSONObject tagsDB = new JSONObject(dbstats);
                statsequal = tagsDB.similar(stats);
            }
            System.out.println("equip dbequal - base " + base + "statsequal " + statsequal);
            return (resultSet.next() && base && statsequal);
        }
        catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void addDatabase() {
        if (!contentInDatabase()) {
            super.addDatabase();
        }
        try {
            String sql = "INSERT INTO equipmentData (id, tier, component, stats) VALUES (?, ?, ?, ?)";
            PreparedStatement ps = DatabaseConnection.connection.prepareStatement(sql);
            ps.setString(1, id);
            ps.setString(2, tier);
            ps.setString(3, component);
            ps.setString(4, stats.toString());
            ps.executeUpdate();
        }
        catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void updateDatabase() {
        super.updateDatabase();
        try {
            String sql = "UPDATE equipmentData SET tier = ?, component = ?, stats = ? WHERE id = ?";
            PreparedStatement ps = DatabaseConnection.connection.prepareStatement(sql);
            ps.setString(1, tier);
            ps.setString(2, component);
            ps.setString(3, stats.toString());
            ps.setString(4, id);
            ps.executeUpdate();
        }

        catch (SQLException e) {
            throw new RuntimeException(e);
        }
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
        //TODO - GUI will be done in later half of the project. temp console application below
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
                case "3":
                    publishMira();
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
                obj.checkDatabase();
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
            return entry;
        }
        if (Objects.equals(type, "Component") || Objects.equals(type, "Equipment")) {
            CCEquipment entry = new CCEquipment(data);
            return entry;
        }
        CCContent entry = new CCContent(data);
        return entry;
    }

    public static void renameEntries() {
        //TODO
    }

    public static void publishMira() {
        //TODO
    }
}
