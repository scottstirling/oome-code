package org.oome.classfile;

import java.lang.classfile.MethodModel;
import java.util.Map;


/// A `record` to hold data resulting from a class file analysis.
/// This acts as a data transfer object (DTO) between the analyzer and the UI.
/// Uses the Java `record` keyword introduced in Java 15.
public record ClassAnalysisResult(
    String summaryReport,
    Map<String, MethodModel> methodMap,
    String hexDump
) {}
