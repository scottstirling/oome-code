package org.oome.swing;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.lang.classfile.MethodModel;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;

import org.oome.classfile.ClassAnalysisResult;
import org.oome.classfile.ClassFileAnalyzer;


/// ClassFileViewer is a Java Swing GUI to inspect a Java .class file.
/// This class handles all UI components and events, and delegates class
/// file analysis to the [ClassFileAnalyzer] class.
///
/// @author oome.org
/// @version 3.1
/// @since 24 (minimum Java 24)
public class ClassFileViewer extends JFrame {

    private final ClassFileAnalyzer analyzer = new ClassFileAnalyzer();
    private final JTextArea summaryArea;
    private final JTextArea bytecodeArea;
    private final JTextArea hexArea;
    private final JComboBox<String> methodSelector;
    private final JLabel statusBar;
    private Map<String, MethodModel> currentMethodMap = new LinkedHashMap<>();

    public ClassFileViewer() {
        setTitle("oome.org - Java Class File Viewer");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(900, 700);
        setLocationRelativeTo(null);

        // --- UI Setup ---
        var mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        var topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        var openButton = new JButton("Open .class File...");
        statusBar = new JLabel("Please select a class file to analyze.");
        topPanel.add(openButton);
        topPanel.add(statusBar);

        var tabbedPane = new JTabbedPane();
        summaryArea = createDisplayArea();
        bytecodeArea = createDisplayArea();
        hexArea = createDisplayArea();
        methodSelector = new JComboBox<>();
        
        var bytecodePanel = new JPanel(new BorderLayout(5, 5));
        bytecodePanel.add(methodSelector, BorderLayout.NORTH);
        bytecodePanel.add(new JScrollPane(bytecodeArea), BorderLayout.CENTER);

        tabbedPane.addTab("Overview", new JScrollPane(summaryArea));
        tabbedPane.addTab("Method Bytecode", bytecodePanel);
        tabbedPane.addTab("Hex Dump", new JScrollPane(hexArea));

        mainPanel.add(topPanel, BorderLayout.NORTH);
        mainPanel.add(tabbedPane, BorderLayout.CENTER);
        setContentPane(mainPanel);

        // --- Event Listeners ---
        openButton.addActionListener(e -> chooseAndProcessFile());
        methodSelector.addActionListener(e -> displaySelectedMethodBytecode());
    }

    private JTextArea createDisplayArea() {
        var textArea = new JTextArea("Select a file to begin.");
        textArea.setEditable(false);
        textArea.setFont(new Font("Monospaced", Font.PLAIN, 14));
        return textArea;
    }

    private void chooseAndProcessFile() {
        var fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Select a Java .class file");
        fileChooser.setFileFilter(new FileNameExtensionFilter("Java Class Files", "class"));

        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            Path classFilePath = fileChooser.getSelectedFile().toPath();
            statusBar.setText("Processing: " + classFilePath.getFileName());
            try {
                // DELEGATION: Call the analyzer to do the heavy lifting
                ClassAnalysisResult result = analyzer.analyze(classFilePath);

                // POPULATE UI: Use the data from the result object
                summaryArea.setText(result.summaryReport());
                hexArea.setText(result.hexDump());
                currentMethodMap = result.methodMap();

                methodSelector.removeAllItems();
                currentMethodMap.keySet().forEach(methodSelector::addItem);
                
                statusBar.setText("Successfully analyzed: " + classFilePath.getFileName());
                summaryArea.setCaretPosition(0);
                hexArea.setCaretPosition(0);

            } catch (Exception ex) {
                summaryArea.setText("Error processing file: " + ex.getMessage());
                bytecodeArea.setText("");
                hexArea.setText("");
                methodSelector.removeAllItems();
                statusBar.setText("Failed to process file.");
                ex.printStackTrace();
            }
        }
    }

    private void displaySelectedMethodBytecode() {
        var selectedSignature = (String) methodSelector.getSelectedItem();
        if (selectedSignature == null) {
            bytecodeArea.setText("No method selected or available.");
            return;
        }

        var selectedMethod = currentMethodMap.get(selectedSignature);
        if (selectedMethod == null) return;

        bytecodeArea.setText(analyzer.getMethodBytecodeReport(selectedMethod));
        bytecodeArea.setCaretPosition(0);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) { System.err.println("Couldn't set system look and feel."); }
            new ClassFileViewer().setVisible(true);
        });
    }
}
