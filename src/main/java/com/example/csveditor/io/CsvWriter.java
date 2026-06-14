package com.example.csveditor.io;

import com.example.csveditor.domain.CsvDocument;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class CsvWriter {
    public void write(CsvDocument document, Path outputPath) throws CsvIOException {
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setRecordSeparator(document.getLineSeparator())
                .build();
        try (BufferedWriter writer = Files.newBufferedWriter(outputPath, document.getCharset());
             CSVPrinter printer = new CSVPrinter(writer, format)) {
            printer.printRecord(document.getSchema().getHeaderValues());
            for (List<String> row : document.getRows()) {
                printer.printRecord(row);
            }
        } catch (IOException e) {
            throw new CsvIOException("CSVの書き込みに失敗しました: " + outputPath, e);
        }
    }
}
