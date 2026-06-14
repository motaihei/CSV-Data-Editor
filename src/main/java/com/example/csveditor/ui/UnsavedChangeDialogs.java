package com.example.csveditor.ui;

import com.example.csveditor.domain.CsvDocument;

import javax.swing.JOptionPane;
import java.awt.Component;

/**
 * Confirmation dialogs for dirty CSV documents.
 */
public final class UnsavedChangeDialogs {
    public enum Choice {
        SAVE,
        DISCARD,
        CANCEL
    }

    private UnsavedChangeDialogs() {
    }

    public static Choice confirmClose(Component parent, CsvDocument document) {
        return confirm(parent, document, "未保存のCSVを閉じますか。", "閉じる前に保存しますか。");
    }

    public static Choice confirmReload(Component parent, CsvDocument document) {
        return confirm(parent, document, "未保存のCSVを再読み込みしますか。", "再読み込み前に保存しますか。");
    }

    public static Choice confirmExit(Component parent, CsvDocument document) {
        return confirm(parent, document, "未保存のCSVがあります。", "終了前に保存しますか。");
    }

    private static Choice confirm(Component parent, CsvDocument document, String title, String message) {
        String path = document == null ? "" : String.valueOf(document.getRelativePath());
        Object[] options = {"保存する", "破棄する", "キャンセル"};
        int result = JOptionPane.showOptionDialog(parent,
                message + "\n" + path,
                title,
                JOptionPane.YES_NO_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE,
                null,
                options,
                options[0]);
        if (result == 0) {
            return Choice.SAVE;
        }
        if (result == 1) {
            return Choice.DISCARD;
        }
        return Choice.CANCEL;
    }
}
