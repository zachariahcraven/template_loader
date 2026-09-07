import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Comparator;

public class TemplateServer {
    static final Path TEMPLATES = Path.of("templates");

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.createContext("/import", ex -> respond(ex, "text/plain", importRepo(param(ex, "repo"))));
        server.createContext("/render", ex -> respond(ex, "text/plain", render(param(ex, "name"))));
        server.createContext("/", ex -> {
            if (ex.getRequestURI().getPath().equals("/")) respond(ex, "text/html", PAGE);
            else respond(ex, "text/plain", "not found\n");
        });
        server.start();
        System.out.println("template GUI on http://localhost:8080");
        System.out.println("Ctrl-C to stop.");
    }

    // INTENDED FEATURE: clone a user-chosen repo into templates/.
    static String importRepo(String repo) {
        if (repo == null || repo.isBlank()) return "missing ?repo=\n";
        try {
            deleteRecursively(TEMPLATES);
            int code = new ProcessBuilder("git", "clone", "-q", repo, TEMPLATES.toString())
                    .start().waitFor();
            return code == 0 ? "imported templates from " + repo + "\n"
                             : "import failed (git exit " + code + ")\n";
        } catch (Exception e) { return "import error: " + e + "\n"; }
    }

    // VULNERABLE READ: guards against "../" but FOLLOWS symlinks.
    static String render(String name) {
        if (name == null || name.isBlank()) return "missing ?name=\n";
        try {
            Path base   = TEMPLATES.toAbsolutePath().normalize();
            Path target = base.resolve(name).normalize();
            if (!target.startsWith(base)) return "blocked (escapes templates/)\n";
            if (!Files.exists(target))    return "not found: " + name + "\n";
            return Files.readString(target);                 // follows symlinks -> leak
        } catch (Exception e) { return "error: " + e + "\n"; }
    }

    // --- the GUI page ---
    static final String PAGE = """
            <!doctype html>
            <html><head><meta charset="utf-8"><title>Template Manager</title>
            <style>
              body{font-family:system-ui,sans-serif;max-width:760px;margin:2rem auto;padding:0 1rem;color:#222}
              h1{margin-bottom:.2rem}
              .card{border:1px solid #ddd;border-radius:10px;padding:1rem 1.2rem;margin:1rem 0;box-shadow:0 1px 4px rgba(0,0,0,.06)}
              .card h3{margin:.2rem 0 .6rem}
              input{width:100%;padding:.55rem;margin:.3rem 0;border:1px solid #bbb;border-radius:6px;font-size:1rem}
              button{padding:.55rem 1.1rem;border:0;border-radius:6px;background:#1565c0;color:#fff;font-size:1rem;cursor:pointer}
              button:hover{background:#0d47a1}
              pre{background:#111;color:#7CFC7C;padding:1rem;border-radius:8px;overflow:auto;white-space:pre-wrap;min-height:2rem}
              .hint{color:#777;font-size:.85rem}
            </style></head>
            <body>
              <h1>Template Manager</h1>
              <p class="hint">Import a template repo, then render a template by name.</p>

              <div class="card">
                <h3>1. Import a template repo</h3>
                <input id="repo" value="../attacker_remote" placeholder="git URL or path">
                <button onclick="doImport()">Import</button>
              </div>

              <div class="card">
                <h3>2. Render a template</h3>
                <input id="name" value="greeting.html" placeholder="template name (e.g. greeting.html, report.html)">
                <button onclick="doRender()">Render</button>
              </div>

              <h3>Output</h3>
              <pre id="out">(nothing yet)</pre>

              <script>
                async function call(url){ const r = await fetch(url); return await r.text(); }
                async function doImport(){
                  const repo = document.getElementById('repo').value;
                  document.getElementById('out').textContent = await call('/import?repo=' + encodeURIComponent(repo));
                }
                async function doRender(){
                  const name = document.getElementById('name').value;
                  document.getElementById('out').textContent = await call('/render?name=' + encodeURIComponent(name));
                }
              </script>
            </body></html>
            """;

    static String param(HttpExchange ex, String key) {
        String q = ex.getRequestURI().getRawQuery();
        if (q == null) return null;
        for (String kv : q.split("&")) {
            int i = kv.indexOf('=');
            if (i > 0 && kv.substring(0, i).equals(key))
                return URLDecoder.decode(kv.substring(i + 1), StandardCharsets.UTF_8);
        }
        return null;
    }

    static void respond(HttpExchange ex, String type, String body) throws IOException {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", type + "; charset=utf-8");
        ex.sendResponseHeaders(200, b.length);
        try (var os = ex.getResponseBody()) { os.write(b); }
    }

    static void deleteRecursively(Path p) throws IOException {
        if (!Files.exists(p, LinkOption.NOFOLLOW_LINKS)) return;
        try (var walk = Files.walk(p)) {
            walk.sorted(Comparator.reverseOrder()).forEach(x -> {
                try { Files.deleteIfExists(x); } catch (IOException ignored) {}
            });
        }
    }
}
