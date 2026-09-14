import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Scanner;

//   import -> clone a chosen git repo into templates/
//   load   -> read a template by name, guarding against "../" in the name
//
// VULNERABILITY: git preserves symlinks, so an imported repo can plant a
// template that is a symlink pointing outside templates/. load() passes its
// "../" check (the name stays inside templates/), then readString FOLLOWS
// the link out -> arbitrary file read.
public class TemplateManagerHardened {
    static final Path TEMPLATES = Path.of("templates");
    static final Scanner in = new Scanner(System.in);

    static void importRepo() throws Exception {
        System.out.print("  template repo (git URL or path): ");
        String repo = in.nextLine().trim();
        int code = new ProcessBuilder("git", "clone", repo, "templates")
            .start().waitFor();
        System.out.println(code == 0
            ? "  imported templates from " + repo
            : "  import failed (git exit " + code + ")");
    }

    static void load() throws Exception {
        System.out.print("  template name: ");
        String name = in.nextLine().trim();
        Path base   = TEMPLATES.toAbsolutePath().normalize();
        Path target = base.resolve(name).normalize();
        if (!target.toRealPath().startsWith(base)) {
            System.out.println("  blocked");
            return;
        }
        if (!Files.exists(target)) { System.out.println("  not found: " + name); return; }
        System.out.print("  --- rendered ---\n" + Files.readString(target));
    }

    public static void main(String[] args) throws Exception {
        while (true) {
            System.out.print("\n[i]mport / [l]oad > ");
            if (!in.hasNextLine()) break;
            String choice = in.nextLine().trim().toLowerCase();
            try {
                if (choice.startsWith("i")) importRepo();
                else if (choice.startsWith("l")) load();
                else System.out.println("  type i or l");
            } catch (Exception e) {
                System.out.println("  error: " + e.getMessage());
            }
        }
    }
}
