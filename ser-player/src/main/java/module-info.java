module me.champeau.a4j.serplayer {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.prefs;
    requires me.champeau.a4j.jserfile;
    exports me.champeau.a4j.serplayer;
    opens me.champeau.a4j.serplayer.controls to javafx.controls, javafx.fxml;
}
