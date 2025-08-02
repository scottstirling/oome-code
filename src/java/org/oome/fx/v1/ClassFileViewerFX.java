package org.oome.fx.v1;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import java.net.URL;

public class ClassFileViewerFX extends Application {

    @Override
    public void start(Stage stage) throws Exception {
        // Load the FXML layout file from the same directory
        URL fxmlUrl = getClass().getResource("viewer.fxml");
        Parent root = FXMLLoader.load(fxmlUrl);
        
        Scene scene = new Scene(root, 900, 700);
        
        // Load the CSS stylesheet
        URL cssUrl = getClass().getResource("styles.css");
        scene.getStylesheets().add(cssUrl.toExternalForm());
        
        stage.setTitle("oome.org - FXML Class File Viewer");
        stage.setScene(scene);
        stage.show();
    }

    /**
     * A main method is not strictly required but can be 
     * used to pass arguments to the application at startup.
     * 
     * @param args
     */
    public static void main(String[] args) {
        launch(args);
    }
}
