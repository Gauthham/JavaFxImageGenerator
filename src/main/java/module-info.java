module org.example.javafxtryoutapp {
    requires javafx.controls;
    requires javafx.fxml;
    opens org.example.javafxtryoutapp to javafx.fxml;
    exports org.example.javafxtryoutapp;
}