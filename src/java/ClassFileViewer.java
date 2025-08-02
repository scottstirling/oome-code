// Java Class-File API imports


import java.lang.classfile.Attributes;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeElement;
import java.lang.classfile.Instruction;
import java.lang.classfile.MethodModel;
import java.lang.classfile.attribute.CodeAttribute;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;

//GUI related AWT and Swing imports
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
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

//misc imports
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

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

/**
 * A record to hold the results from a class file analysis.
 * This acts as a data transfer object (DTO) between the analyzer and the UI.
 * Uses the Java <code>record</code> keyword introduced in Java 15.
 */
record ClassAnalysisResult(
    String summaryReport,
    Map<String, MethodModel> methodMap,
    String hexDump
) {}

/**
 * ClassFileAnalyzer handles the logic for parsing and analyzing a class file using the
 * Java Class-File API. Uses Java 24 finalized version of the new Java Class-File APIs.
 */
class ClassFileAnalyzer {

    /**
     * Analyzes a class file and returns a structured result.
     * @param classFilePath The path to the .class file.
     * @return A {@link ClassAnalysisResult} object containing the parsed data.
     * @throws IOException if there is an error reading the file.
     */
    public ClassAnalysisResult analyze(Path classFilePath) throws IOException {
        byte[] classBytes = Files.readAllBytes(classFilePath);
        ClassModel classModel = ClassFile.of().parse(classBytes);

        var summary = buildSummaryReport(classModel);
        var methods = buildMethodMap(classModel);
        var hex = bytesToHex(classBytes);

        return new ClassAnalysisResult(summary, methods, hex);
    }

    /**
     * Generates a detailed report of a single method's bytecode.
     * @param method The {@link MethodModel} to analyze.
     * @return A formatted string containing the bytecode report.
     */
    public String getMethodBytecodeReport(MethodModel method) {

        // 1. Define the report structure with a simple placeholder Text Block (Java 15).
        String headerTemplate = """
            Bytecode for method: %s


            """;
        
        // 2. Build the signature string first.
        String signature = buildMethodSignature(method);

        // 3. Replace the placeholder with the actual signature.
        var report = new StringBuilder(headerTemplate.replace("%s", signature));
        
        Optional<CodeAttribute> codeAttributeOpt = method.findAttribute(Attributes.code());
        if (codeAttributeOpt.isPresent()) {
            var codeAttribute = codeAttributeOpt.get();
            
            // Using .formatted()
            report.append("Max stack: %d, Max locals: %d, Code length: %d\n".formatted(
                                          codeAttribute.maxStack(), codeAttribute.maxLocals(), codeAttribute.codeLength()));
            report.append("--------------------------------------------------\n");
            
            for (CodeElement element : codeAttribute.elementList()) {
                if (element instanceof Instruction instruction) {
                    report.append(instruction.toString()).append("\n");
                }
            }
        } else {
            report.append("(No bytecode for this method - it may be abstract or native)");
        }
        return report.toString();
    }

    private Map<String, MethodModel> buildMethodMap(ClassModel classModel) {
        Map<String, MethodModel> map = new LinkedHashMap<>();
        for (MethodModel method : classModel.methods()) {
            String signature = buildMethodSignature(method);
            map.put(signature, method);
        }
        return map;
    }

    private String buildSummaryReport(ClassModel classModel) {
        // AMBER FEATURE: Text Blocks for cleaner multi-line string building
        var interfaces = classModel.interfaces().stream()
                                   .map(iface -> iface.asSymbol().displayName())
                                   .collect(Collectors.joining(", "));
        
        return """
               Class:       %s
               Superclass:  %s
               Version:     %d.%d (Java %d)
               Interfaces:  %s

               --- Fields ---
               %s

               --- Methods ---
               %s
               """.formatted(
                    classModel.thisClass().asSymbol().displayName(),
                    classModel.superclass().map(cd -> cd.asSymbol().displayName()).orElse("N/A"),
                    classModel.majorVersion(), classModel.minorVersion(), classModel.majorVersion() - 44,
                    interfaces.isEmpty() ? "(None)" : interfaces,
                    buildFieldsReport(classModel),
                    buildMethodsReport(classModel)
               );
    }
    
    private String buildFieldsReport(ClassModel classModel) {
        if (classModel.fields().isEmpty()) return "(No declared fields)";
        return classModel.fields().stream()
                         .map(field -> " - %s %s".formatted(
                                ClassDesc.ofDescriptor(field.fieldType().stringValue()).displayName(),
                                field.fieldName().stringValue()))
                         .collect(Collectors.joining("\n"));
    }

    private String buildMethodsReport(ClassModel classModel) {
        if (classModel.methods().isEmpty()) return "(No declared methods)";
        return classModel.methods().stream()
                         .map(this::buildMethodSignature)
                         .map(sig -> " - " + sig)
                         .collect(Collectors.joining("\n"));
    }

    private String buildMethodSignature(MethodModel method) {
        var mtd = MethodTypeDesc.ofDescriptor(method.methodType().stringValue());
        var params = mtd.parameterList().stream()
                        .map(ClassDesc::displayName)
                        .collect(Collectors.joining(", "));
        return "%s %s(%s)".formatted(mtd.returnType().displayName(), method.methodName().stringValue(), params);
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        HexFormat hexFormat = HexFormat.of().withUpperCase();
        for (int i = 0; i < bytes.length; i++) {
            if (i > 0) {
                hexString.append((i % 16 == 0) ? "\n" : " ");
            }
            hexString.append(hexFormat.toHexDigits(bytes[i]));
        }
        return hexString.toString();
    }

}