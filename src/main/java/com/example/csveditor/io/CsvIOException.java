package com.example.csveditor.io;

import java.io.IOException;

public class CsvIOException extends IOException {
    public CsvIOException(String message, Throwable cause) {
        super(message, cause);
    }

    public CsvIOException(String message) {
        super(message);
    }
}
