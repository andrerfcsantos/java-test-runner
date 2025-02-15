package com.exercism.runner;

import static java.util.concurrent.TimeUnit.SECONDS;

import com.exercism.data.Report;
import com.exercism.junit.JUnitTestParser;
import com.exercism.report.ReportGenerator;
import com.exercism.xml.JUnitXmlParser;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Files;
import com.google.common.io.MoreFiles;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class TestRunner {
    private static final String MAVEN_TEST_OUTPUT = "maven-test.out";

    public static void main(String[] args) throws InterruptedException, IOException {
        run(args[0]);
    }

    private static void run(String slug) throws InterruptedException, IOException {
        Process mavenTest = new ProcessBuilder(
            "mvn",
            "test",
            "--offline",
            "--legacy-local-repository",
            "--batch-mode",
            "--non-recursive",
            "--quiet")
            .redirectOutput(new File(MAVEN_TEST_OUTPUT))
            .start();
        if (!mavenTest.waitFor(20, SECONDS)) {
            throw new IllegalStateException("test did not complete within 20 seconds");
        }

        if (mavenTest.exitValue() != 0) {
            String mavenOutput = Files.asCharSource(
                Paths.get(MAVEN_TEST_OUTPUT).toFile(), StandardCharsets.UTF_8)
                .read();
            if (mavenOutput.contains("COMPILATION ERROR")) {
                ReportGenerator.report(
                    Report.builder()
                        .setStatus("error")
                        .setMessage(mavenOutput)
                        .build());
                return;
            }
        }

        JUnitTestParser testParser = new JUnitTestParser();
        for (Path filePath : MoreFiles.listFiles(Paths.get("src", "test", "java"))) {
            if (MoreFiles.getFileExtension(filePath).equals("java")) {
                testParser.parse(filePath.toFile());
            }
        }
        ImmutableMap<String, String> testCodeByTestName = testParser.buildTestCodeMap();
        JUnitXmlParser xmlParser = new JUnitXmlParser(mavenTest.exitValue(), testCodeByTestName);
        for (Path filePath : MoreFiles.listFiles(Paths.get("target", "surefire-reports"))) {
            if (MoreFiles.getFileExtension(filePath).equals("xml")) {
                xmlParser.parse(filePath.toFile());
            }
        }
        Report report = xmlParser.buildReport();
        ReportGenerator.report(report);
    }
}
