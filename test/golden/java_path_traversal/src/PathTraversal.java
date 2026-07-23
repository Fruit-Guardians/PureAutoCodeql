import java.io.File;

public class PathTraversal {
    public static String vulnerable() throws Exception {
        String requested = System.getenv("USER_FILE");
        return new File(requested).getCanonicalPath();
    }
}
