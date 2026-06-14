package com.example.csveditor.io;

import com.example.csveditor.app.AppConfig;
import com.example.csveditor.domain.CsvDocument;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;

public class CsvReaderWriterTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private final Charset cp932 = Charset.forName(AppConfig.DEFAULT_CHARSET_NAME);

    @Test
    public void readsCp932JapaneseAndHeaderSeparately() throws Exception {
        Path file = temporaryFolder.newFile("japanese.csv").toPath();
        Files.write(file, Arrays.asList("氏名,メモ", "山田太郎,確認済み"), cp932);

        CsvDocument document = new CsvReader().read(file, temporaryFolder.getRoot().toPath(), cp932);

        assertEquals(Arrays.asList("氏名", "メモ"), document.getSchema().getHeaderValues());
        assertEquals(1, document.getRows().size());
        assertEquals("山田太郎", document.getRows().get(0).get(0));
        assertEquals("確認済み", document.getRows().get(0).get(1));
    }

    @Test
    public void readsQuotedCommaQuoteEmptyColumnAndMultilineValues() throws Exception {
        Path file = temporaryFolder.newFile("quoted.csv").toPath();
        String csv = "name,note,empty,tail\r\n"
                + "\"a,b\",\"say \"\"hello\"\"\",,\r\n"
                + "multi,\"line1\r\nline2\",x,y\r\n";
        Files.write(file, csv.getBytes(cp932));

        CsvDocument document = new CsvReader().read(file, temporaryFolder.getRoot().toPath(), cp932);

        assertEquals("a,b", document.getRows().get(0).get(0));
        assertEquals("say \"hello\"", document.getRows().get(0).get(1));
        assertEquals("", document.getRows().get(0).get(2));
        assertEquals("", document.getRows().get(0).get(3));
        assertEquals("line1\r\nline2", document.getRows().get(1).get(1));
    }

    @Test
    public void normalizesRaggedRowsToMaximumColumnCount() throws Exception {
        Path file = temporaryFolder.newFile("ragged.csv").toPath();
        Files.write(file, Arrays.asList("a,b", "1", "2,3,4"), cp932);

        CsvDocument document = new CsvReader().read(file, temporaryFolder.getRoot().toPath(), cp932);

        assertEquals(3, document.getSchema().getColumnCount());
        assertEquals(Arrays.asList("a", "b", ""), document.getSchema().getHeaderValues());
        assertEquals(Arrays.asList("1", "", ""), document.getRows().get(0));
        assertEquals(Arrays.asList("2", "3", "4"), document.getRows().get(1));
    }

    @Test
    public void supplementsDisplayHeadersWithoutChangingOriginalHeaderValues() throws Exception {
        Path file = temporaryFolder.newFile("headers.csv").toPath();
        Files.write(file, Arrays.asList("id,,id", "1,2,3"), cp932);

        CsvDocument document = new CsvReader().read(file, temporaryFolder.getRoot().toPath(), cp932);

        assertEquals(Arrays.asList("id", "", "id"), document.getSchema().getHeaderValues());
        assertEquals(Arrays.asList("id", "Column 2", "id (2)"), document.getSchema().getDisplayColumnNames());
    }

    @Test
    public void writesHeaderFirstAndPreservesCsvValues() throws Exception {
        Path source = temporaryFolder.newFile("source.csv").toPath();
        Files.write(source, Arrays.asList("name,note", "old,value"), cp932);
        CsvDocument document = new CsvReader().read(source, temporaryFolder.getRoot().toPath(), cp932);
        document.getRows().get(0).set(0, "山田,太郎");
        document.getRows().get(0).set(1, "say \"hello\"");

        Path output = temporaryFolder.newFile("output.csv").toPath();
        new CsvWriter().write(document, output);
        CsvDocument reread = new CsvReader().read(output, temporaryFolder.getRoot().toPath(), cp932);

        assertEquals(Arrays.asList("name", "note"), reread.getSchema().getHeaderValues());
        assertEquals("山田,太郎", reread.getRows().get(0).get(0));
        assertEquals("say \"hello\"", reread.getRows().get(0).get(1));
    }
}
