import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Scanner;

//   import -> User chooses a git repo (e.g. a GitHub URL)
//             and the app clones its templates into templates/
//
//   load   -> reads/renders a template and guards against loading a
//              template with "../" in the path.
//
/* 
 * The vulnerability is the simlink git preserves symlinks, so an imported 
 * repo can contain a template that is a symlink pointing
 * outside templates/. load() passes its "../" check (the name is inside
 * templates/) and then readString FOLLOWS the link out -> arbitrary file read.
*/
public class TemplateManager {
    static final Path TEMPLATES = Path.of("templates");
    static final Scanner in = new Scanner(System.in);

    static void importRepo() throws Exception {
        System.out.print("  template repo (git URL or path): ");
        String repo = in.nextLine().trim();
        if (repo.isEmpty()) { System.out.println("  repo required"); return; }
        int code = new ProcessBuilder("git", "clone", "-q", repo, TEMPLATES.toString())
                .start().waitFor();
        System.out.println(code == 0
                ? "  imported templates from " + repo
                : "  import failed (git exit " + code + ")");
    }

    static void load() throws Exception {
        System.out.print("  template name: ");
        String name = in.nextLine().trim();
        if (name.isEmpty()) { System.out.println("  name required"); return; }

        Path base = TEMPLATES.toAbsolutePath().normalize();
        Path target = base.resolve(name).normalize();
        if (!target.startsWith(base)) {
            System.out.println("  blocked (escapes templates/)");
            return;
        }
        if (!Files.exists(target)) { System.out.println("  not found: " + name); return; }
        System.out.print("  --- rendered ---\n" + Files.readString(target));
    }

    public static void main(String[] args) throws Exception {
        while (true) {
            System.out.print("\n[i]mport a template repo, or [l]oad a template? (Ctrl-D to quit) > ");
            if (!in.hasNextLine()) break;
            String choice = in.nextLine().trim().toLowerCase();
            try {
                if (choice.startsWith("i")) importRepo();
                else if (choice.startsWith("l")) load();
                else System.out.println("  please type i or l");
            } catch (Exception e) {
                System.out.println("  error: " + e.getMessage());
            }
        }
    }
}
