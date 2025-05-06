package mo_phong_zalo2;

import java.io.FileWriter;
import java.io.IOException;

public class FileUtil {
    public static void appendToFile(String filename, String text) {
        try (FileWriter fw = new FileWriter(filename, true)) {
            fw.write(text + "\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
