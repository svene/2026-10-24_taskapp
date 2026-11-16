package org.svenehrke.taskapp.html;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/** Reads a static page shell ({@code /shells/*.html}); its {@code #app} element loads the view and renders it in the browser. */
public final class PageShell {

    private PageShell() {}

    public static String read(String shell) {
        try (var in = PageShell.class.getResourceAsStream(shell)) {
            if (in == null) throw new IllegalStateException("missing shell: " + shell);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
