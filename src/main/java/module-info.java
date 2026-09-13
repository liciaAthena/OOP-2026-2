module com.example.mwsparser {
    requires javafx.controls;
    requires javafx.fxml;
    requires transitive org.json;
    requires java.sql;


    opens com.example.mwsparser to javafx.fxml;
    exports com.example.mwsparser;
}