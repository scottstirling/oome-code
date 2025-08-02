package org.oome.classfile;

// other imports
import java.io.IOException;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;


/// ClassFileAnalyzer handles the logic for parsing and analyzing a class file using the
/// Java Class-File API. Uses Java 24 finalized version of the new Java Class-File APIs.
/// @since Java 24
public class ClassFileAnalyzer {

    
    /// Analyzes a class file and returns a structured result.
    /// @param classFilePath The path to the .class file.
    /// @return A [ClassAnalysisResult] record object containing the parsed data.
    /// @throws java.io.IOException if there is an error reading the file.
    public ClassAnalysisResult analyze(Path classFilePath) throws IOException {
        byte[] classBytes = Files.readAllBytes(classFilePath);
        ClassModel classModel = ClassFile.of().parse(classBytes);

        var summary = buildSummaryReport(classModel);
        var methods = buildMethodMap(classModel);
        var hex = bytesToHex(classBytes);

        return new ClassAnalysisResult(summary, methods, hex);
    }

     /// Generates a detailed report of a single method's bytecode.
     /// @param method The [java.lang.classfile.MethodModel] to analyze.
     /// @return A formatted string containing the bytecode report.
    public String getMethodBytecodeReport(MethodModel method) {

        /// Define the report structure with a simple placeholder `Text Block` (Java 15).
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
