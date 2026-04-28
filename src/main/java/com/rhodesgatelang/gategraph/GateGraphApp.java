package com.rhodesgatelang.gategraph;

import com.rhodesgatelang.gateo.Gateo;
import com.rhodesgatelang.gateo.v3.GateObject;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;
import javafx.scene.web.WebView;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public final class GateGraphApp extends Application {

    private Path tmpDir;
    private String mermaidJsContent;   // inlined into the HTML
    private WebView webView;
    private Stage primaryStage;

    // Remember the most recently loaded gate object + filename so we can re-render
    // when the user toggles collapse without re-opening the file.
    private GateObject currentGate;
    private String currentFilename;
    private boolean collapseComponents = false;

    @Override
    public void start(Stage stage) throws Exception {
        this.primaryStage = stage;
        this.webView = new WebView();
        webView.getEngine().setJavaScriptEnabled(true);

        // Print JS console output to Java stdout so we can see errors
        webView.getEngine().setOnAlert(e -> System.out.println("[JS alert] " + e.getData()));
        webView.getEngine().getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            if (newState == javafx.concurrent.Worker.State.SUCCEEDED) {
                try {
                    Object result = webView.getEngine().executeScript(
                        "document.getElementById('wrap') ? 'wrap-found' : 'wrap-MISSING'"
                    );
                    System.out.println("[WebView loaded] wrap div: " + result);

                    Object pre = webView.getEngine().executeScript("typeof window.__preMermaid + ':' + (window.__preMermaid||'')");
                    System.out.println("[WebView] __preMermaid: " + pre);

                    Object post = webView.getEngine().executeScript("typeof window.__postMermaid + ':' + (window.__postMermaid||'')");
                    System.out.println("[WebView] __postMermaid: " + post);

                    Object err = webView.getEngine().executeScript("window.__lastError || 'none'");
                    System.out.println("[WebView] JS error: " + err);

                    Object mermaidDefined = webView.getEngine().executeScript("typeof mermaid");
                    System.out.println("[WebView] typeof mermaid: " + mermaidDefined);

                    Object wrapLen = webView.getEngine().executeScript(
                        "var w = document.getElementById('wrap'); w ? w.innerHTML.length : -1"
                    );
                    System.out.println("[WebView] wrap innerHTML length: " + wrapLen);
                } catch (Exception e) {
                    System.out.println("[WebView error] " + e.getMessage());
                }
            } else if (newState == javafx.concurrent.Worker.State.FAILED) {
                System.out.println("[WebView FAILED] " + webView.getEngine().getLoadWorker().getException());
            }
        });

        // Read mermaid.min.js once into memory — we'll inline it into each generated HTML file
        tmpDir = Files.createTempDirectory("gategraph");
        try (InputStream in = GateGraphApp.class.getResourceAsStream("/mermaid.min.js")) {
            if (in == null) throw new IllegalStateException("mermaid.min.js not found in resources");
            mermaidJsContent = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }

        BorderPane root = new BorderPane();
        root.setTop(buildMenuBar());
        root.setCenter(webView);

        stage.setTitle("Gate Graph Viewer");
        stage.setScene(new Scene(root, 1280, 800));
        stage.show();

        loadPlaceholder();
    }

    private MenuBar buildMenuBar() {
        MenuItem openItem = new MenuItem("Open\u2026");
        openItem.setAccelerator(KeyCombination.keyCombination("Ctrl+O"));
        openItem.setOnAction(e -> openFile());

        MenuItem exitItem = new MenuItem("Exit");
        exitItem.setOnAction(e -> primaryStage.close());

        Menu fileMenu = new Menu("File");
        fileMenu.getItems().addAll(openItem, new SeparatorMenuItem(), exitItem);

        // View > Collapse Components — checkable; toggles between the nested
        // schematic view and the chip-style block view.
        CheckMenuItem collapseItem = new CheckMenuItem("Collapse Components");
        collapseItem.setAccelerator(KeyCombination.keyCombination("Ctrl+K"));
        collapseItem.setSelected(collapseComponents);
        collapseItem.setOnAction(e -> {
            collapseComponents = collapseItem.isSelected();
            rerenderCurrent();
        });

        Menu viewMenu = new Menu("View");
        viewMenu.getItems().add(collapseItem);

        return new MenuBar(fileMenu, viewMenu);
    }

    /** Re-render the currently loaded gate object using the latest toggle state. */
    private void rerenderCurrent() {
        if (currentGate == null) return;   // nothing loaded yet
        try {
            String mermaid = MermaidGenerator.generate(currentGate, collapseComponents);
            renderMermaid(mermaid);
        } catch (Exception ex) {
            renderError(currentFilename == null ? "render" : currentFilename, ex.getMessage());
        }
    }

    private void openFile() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Open Gate Graph");
        fc.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("Gate Graph Files (*.gateo)", "*.gateo"));
        fc.setInitialDirectory(Path.of(System.getProperty("user.home")).toFile());

        File selected = fc.showOpenDialog(primaryStage);
        if (selected == null) return;

        try {
            GateObject go = Gateo.read(selected.toPath());
            currentGate = go;
            currentFilename = selected.getName();
            String mermaid = MermaidGenerator.generate(go, collapseComponents);
            renderMermaid(mermaid);
            primaryStage.setTitle("Gate Graph Viewer — " + selected.getName());
        } catch (Exception ex) {
            renderError(selected.getName(), ex.getMessage());
        }
    }

    private void renderMermaid(String mermaidContent) {
        // Escape the diagram text for embedding inside a JS template literal
        String jsEscaped = mermaidContent
            .replace("\\", "\\\\")
            .replace("`", "\\`")
            .replace("$", "\\$");

        String html = "<!DOCTYPE html>\n"
            + "<html>\n<head>\n"
            + "  <meta charset=\"UTF-8\">\n"
            + "  <style>\n"
            + "    html, body { margin:0; height:100%; background:#1e1e1e; color:#d4d4d4; font-family:sans-serif; overflow:hidden; }\n"
            + "    #wrap { padding:16px; box-sizing:border-box; width:100vw; height:100vh; }\n"
            + "    #wrap svg { display:block; width:100% !important; height:100% !important; max-width:100%; max-height:100%; }\n"
            + "  </style>\n"
            + "</head>\n<body>\n"
            + "  <div id=\"wrap\"></div>\n"
            + "  <script>\n"
            + "    window.onerror = function(m,s,l,c,e){ window.__lastError = m+' @'+s+':'+l+' '+e; return false; };\n"
            + "    window.__preMermaid = 'executed';\n"
            + "  </script>\n"
            + "  <script>\n" + mermaidJsContent + "\n  </script>\n"
            + "  <script>\n"
            + "    window.__postMermaid = 'executed';\n"
            + "    try {\n"
            + "      mermaid.initialize({ startOnLoad: false, securityLevel: 'loose', theme: 'dark',\n"
            + "        flowchart: { useMaxWidth: false, htmlLabels: true } });\n"
            + "      var diagramText = `" + jsEscaped + "`;\n"
            + "      mermaid.mermaidAPI.render('gate-graph', diagramText, function(svgCode) {\n"
            + "        document.getElementById('wrap').innerHTML = svgCode;\n"
            + "      });\n"
            + "    } catch (err) {\n"
            + "      document.getElementById('wrap').innerHTML =\n"
            + "        '<pre style=\"color:#f66;white-space:pre-wrap\">' + err + '</pre>';\n"
            + "    }\n"
            + "  </script>\n"
            + "</body>\n</html>\n";

        try {
            Path htmlFile = tmpDir.resolve("graph.html");
            Files.writeString(htmlFile, html);
            System.out.println("[WebView] loading " + htmlFile.toUri());
            System.out.println("[WebView] html size = " + html.length() + " chars");
            webView.getEngine().load(htmlFile.toUri().toString());
        } catch (Exception ex) {
            renderError("render", ex.getMessage());
        }
    }

    private void renderError(String filename, String message) {
        String html = "<html><body style='background:#1e1e1e;color:#f66;"
            + "font-family:monospace;padding:24px'>"
            + "<h2>Failed to load " + escapeHtml(filename) + "</h2>"
            + "<pre>" + escapeHtml(message) + "</pre>"
            + "</body></html>";
        webView.getEngine().loadContent(html, "text/html");
    }

    private void loadPlaceholder() {
        webView.getEngine().loadContent(
            "<html><body style='background:#1e1e1e;color:#888;font-family:sans-serif;"
            + "display:flex;align-items:center;justify-content:center;height:100vh;margin:0'>"
            + "<p>File \u203a Open\u2026 to load a .gateo file</p></body></html>",
            "text/html");
    }

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    public static void main(String[] args) {
        launch(args);
    }
}
