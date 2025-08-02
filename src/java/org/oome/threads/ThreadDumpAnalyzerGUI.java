package org.oome.threads;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JEditorPane;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;

/// Main Application Class (The UI "View")
public class ThreadDumpAnalyzerGUI extends JFrame {

    private final ThreadDumpAnalyzer analyzer = new ThreadDumpAnalyzer();
    private final JList<JavaThread> threadList;
    private final DefaultListModel<JavaThread> listModel;
    private final JEditorPane stackTraceArea;
    private final JTextField filterField;
    private List<JavaThread> allThreads = new ArrayList<>();

    public ThreadDumpAnalyzerGUI() {
        setTitle("oome.org - Thread Dump Analyzer");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1200, 800);
        setLocationRelativeTo(null);

        // --- UI Setup ---
        var mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        var topPanel = new JPanel(new BorderLayout(10, 0));
        var openButton = new JButton("Open Thread Dump File...");
        filterField = new JTextField();
        topPanel.add(openButton, BorderLayout.WEST);
        topPanel.add(filterField, BorderLayout.CENTER);

        listModel = new DefaultListModel<>();
        threadList = new JList<>(listModel);
        threadList.setCellRenderer(new ThreadListCellRenderer());

        stackTraceArea = new JEditorPane(); // Was new JTextArea(...)
        stackTraceArea.setContentType("text/html");
        stackTraceArea.setEditable(false);
        stackTraceArea.setFont(new Font("Monospaced", Font.PLAIN, 14));

        var splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JScrollPane(threadList),
                new JScrollPane(stackTraceArea));
        splitPane.setDividerLocation(400);

        mainPanel.add(topPanel, BorderLayout.NORTH);
        mainPanel.add(splitPane, BorderLayout.CENTER);
        setContentPane(mainPanel);

        // --- Event Listeners ---
        openButton.addActionListener(e -> openFile());
        filterField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                filterThreadList();
            }

            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                filterThreadList();
            }

            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                filterThreadList();
            }
        });
        threadList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                displaySelectedThread();
            }
        });
    }

    private void openFile() {
        var fc = new JFileChooser(".");
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                Path path = fc.getSelectedFile().toPath();
                String content = Files.readString(path);
                allThreads = analyzer.parse(content);
                filterThreadList();
                setTitle("oome.org - Thread Dump Analyzer - " + path.getFileName());
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "Error reading file: " + ex.getMessage(), "Error",
                        JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void filterThreadList() {
        String filterText = filterField.getText().toLowerCase();
        listModel.clear();
        allThreads.stream().filter(t -> t.name().toLowerCase().contains(filterText)).forEach(listModel::addElement);
    }

    private void displaySelectedThread() {
        JavaThread selectedThread = threadList.getSelectedValue();
        if (selectedThread != null) {
            // Call the new HTML report generator
            String reportHtml = analyzer.getThreadReportHtml(selectedThread);
            stackTraceArea.setText(reportHtml);
            stackTraceArea.setCaretPosition(0);
            stackTraceArea.setCaretPosition(0);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                e.printStackTrace();
            }
            new ThreadDumpAnalyzerGUI().setVisible(true);
        });
    }
}

/// The Data "Model" ---
record StackFrame(String line) {
    @Override
    public String toString() {
        return line;
    }
}

record JavaThread(String name, String rawHeaderText, List<StackFrame> stackFrames, String fullText) {
    @Override
    public String toString() {
        return name;
    }

    public boolean isVirtual() {
        return rawHeaderText.contains("virtual");
    }

    public boolean isCarrier() {
        return name.startsWith("ForkJoinPool");
    }
}

/// The Business Logic "Controller" (Parser for jcmd)
class ThreadDumpAnalyzer {
    // THE FIX: The regex now has a capturing group `([^\"]*)` for the thread name.
    private static final Pattern THREAD_HEADER_PATTERN = Pattern.compile("^#\\d+\\s+\"([^\"]*)\"");

    public List<JavaThread> parse(String dumpContent) {
        var threads = new ArrayList<JavaThread>();
        var lines = dumpContent.lines().toList();
        var currentBlock = new ArrayList<String>();
        boolean foundFirstThread = false; // Add a flag to track state

        for (String line : lines) {
            // Ignore everything until we find the first real thread header
            if (!foundFirstThread) {
                if (THREAD_HEADER_PATTERN.matcher(line).find()) {
                    foundFirstThread = true;
                } else {
                    continue; // Skip PID, timestamp, etc.
                }
            }

            // only runs after the first thread is found
            if (THREAD_HEADER_PATTERN.matcher(line).find() && !currentBlock.isEmpty()) {
                parseBlock(currentBlock, threads);
                currentBlock.clear();
            }
            if (!line.trim().isEmpty()) {
                currentBlock.add(line);
            }
        }

        // Process the final block in the file
        if (!currentBlock.isEmpty()) {
            parseBlock(currentBlock, threads);
        }
        return threads;
    }

    private void parseBlock(List<String> blockLines, List<JavaThread> threads) {
        if (blockLines.isEmpty())
            return;

        String header = blockLines.get(0);
        Matcher matcher = THREAD_HEADER_PATTERN.matcher(header);

        if (matcher.find()) {
            // This will now work correctly because group(1) exists.
            String name = matcher.group(1);
            if (name.isEmpty()) {
                name = "Virtual Thread " + header.split("\\s+")[0];
            }

            var stackFrames = new ArrayList<StackFrame>();
            for (int i = 1; i < blockLines.size(); i++) {
                stackFrames.add(new StackFrame(blockLines.get(i).trim()));
            }

            String fullText = String.join("\n", blockLines);
            threads.add(new JavaThread(name, header, stackFrames, fullText));
        } else {
            System.err.println("--- FAILED TO PARSE BLOCK HEADER ---");
            System.err.println(header);
            System.err.println("----------------------------------\n");
        }
    }

    public String getThreadReportHtml(JavaThread thread) {
        if (thread == null)
            return "<html><body>Select a thread.</body></html>";

        // Use a Text Block for the HTML template
        String template = """
                <html><body style='font-family: sans-serif; font-size: 12pt;'>
                  <h3>%s</h3>
                  <p><i>%s</i></p>
                  <h4>Stack Trace:</h4>
                  <pre style='font-family: Monospaced; font-size: 11pt; background-color: #f5f5f5; padding: 1em; border-radius: 4px;'>%s</pre>
                </body></html>
                """;

        String stack = thread.stackFrames().stream().map(Object::toString).collect(Collectors.joining("\n"));

        // Escape HTML characters for safety
        String headerHtml = thread.rawHeaderText().replace("<", "<").replace(">", ">");
        String stackHtml = stack.replace("<", "<").replace(">", ">");

        return template.formatted(thread.name(), headerHtml, stackHtml);
    }

}

/// Custom UI Component for the Thread List.
class ThreadListCellRenderer extends DefaultListCellRenderer {
    // A list of common internal JVM thread names
    private static final List<String> JVM_INTERNAL_THREADS = List.of("Reference Handler", "Finalizer",
            "Signal Dispatcher", "Service Thread", "Monitor Deflation Thread", "C1 CompilerThread", "C2 CompilerThread",
            "Notification Thread", "Common-Cleaner", "DestroyJavaVM", "Attach Listener", "VM Thread", "GC Thread", "G1",
            "AWT-", "Java2D");

    @Override
    public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected,
            boolean cellHasFocus) {
        super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        if (value instanceof JavaThread thread) {
            setText(thread.name());
            String tooltip = thread.rawHeaderText();

            Color foregroundColor = Color.BLACK;

            if (thread.isVirtual()) {
                foregroundColor = new Color(0, 128, 0); // Dark Green
            } else if (thread.isCarrier()) {
                foregroundColor = new Color(100, 149, 237); // Lighter "Cornflower Blue"
            } else if (isJvmInternal(thread.name())) {
                foregroundColor = Color.GRAY;
            }

            if (isSelected) {
                setForeground(Color.WHITE);
            } else {
                setForeground(foregroundColor);
            }
            setToolTipText(tooltip);
        }
        return this;
    }

    private boolean isJvmInternal(String threadName) {
        return JVM_INTERNAL_THREADS.stream().anyMatch(threadName::startsWith);
    }
}