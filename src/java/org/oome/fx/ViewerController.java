package org.oome.fx;

import java.io.File;
import java.lang.classfile.MethodModel;
import java.util.Map;

import org.oome.classfile.ClassAnalysisResult;
import org.oome.classfile.ClassFileAnalyzer;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.stage.FileChooser;

public class ViewerController {

    // These fields are automatically injected by the FXML loader
    @FXML private TextArea summaryArea;
    @FXML private TextArea bytecodeArea;
    @FXML private TextArea hexArea;
    @FXML private ComboBox<String> methodSelector;
    @FXML private Label statusBar;

    // initialize our ClassFileAnalyzer
    private final ClassFileAnalyzer analyzer = new ClassFileAnalyzer();
    private Map<String, MethodModel> currentMethodMap;

    @FXML
    public void initialize() {
        // This method is called after the FXML file has been loaded.
        methodSelector.setOnAction(e -> displaySelectedMethodBytecode());
    }

    @FXML
    private void handleOpenFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select a Java .class file");
        FileChooser.ExtensionFilter extFilter = new FileChooser.ExtensionFilter("Java Class Files (*.class)", "*.class");
        fileChooser.getExtensionFilters().add(extFilter);
        
        File file = fileChooser.showOpenDialog(statusBar.getScene().getWindow());
        if (file != null) {
            statusBar.setText("Processing: " + file.getName());
            new Thread(() -> { // Run analysis on a background thread
                try {
                    ClassAnalysisResult result = analyzer.analyze(file.toPath());
                    Platform.runLater(() -> updateUI(result, file.getName()));
                } catch (Exception ex) {
                    Platform.runLater(() -> showError("Failed to process file: " + ex.getMessage()));
                    ex.printStackTrace();
                }
            }).start();
        }
    }

    private void updateUI(ClassAnalysisResult result, String fileName) {
        summaryArea.setText(result.summaryReport());
        hexArea.setText(result.hexDump());
        currentMethodMap = result.methodMap();

        methodSelector.getItems().clear();
        methodSelector.getItems().addAll(currentMethodMap.keySet());
        if (!methodSelector.getItems().isEmpty()) {
            methodSelector.getSelectionModel().selectFirst();
        }
        
        statusBar.setText("Successfully analyzed: " + fileName);
    }
    
    private void displaySelectedMethodBytecode() {
        String signature = methodSelector.getSelectionModel().getSelectedItem();
        if (signature == null || currentMethodMap == null) return;
        bytecodeArea.setText(analyzer.getMethodBytecodeReport(currentMethodMap.get(signature)));
    }

    private void showError(String message) {
        summaryArea.setText(message);
        bytecodeArea.clear();
        hexArea.clear();
        methodSelector.getItems().clear();
        statusBar.setText("Error");
    }
}
