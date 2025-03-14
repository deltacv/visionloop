package io.github.deltacv.visionloop.tj;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

public final class TJLoader {
    private TJLoader() {}

    public static void load() {
        // get os and arch
        String os = System.getProperty("os.name").toLowerCase();
        String arch = System.getProperty("os.arch").toLowerCase();

        String libPath = null;

        if (os.contains("win")) {
            if (arch.contains("64")) {
                libPath = "/META-INF/lib/windows_64/turbojpeg.dll";
            } else {
                libPath = "/META-INF/lib/windows_32/turbojpeg.dll";
            }
        } else if (os.contains("linux")) {
            if (arch.contains("64")) {
                libPath = "/META-INF/lib/linux_64/libturbojpeg.so";
            } else {
                libPath = "/META-INF/lib/linux_32/libturbojpeg.so";
            }
        } else if (os.contains("mac") || os.contains("darwin")) {
            if (arch.contains("64")) {
                libPath = "/META-INF/lib/osx_64/libturbojpeg.dylib";
            } else if (arch.contains("ppc")) {
                libPath = "/META-INF/lib/osx_ppc/libturbojpeg.dylib";
            } else {
                libPath = "/META-INF/lib/osx_32/libturbojpeg.dylib";
            }
        }

        if (libPath == null) {
            throw new RuntimeException("Unsupported OS/Arch: " + os + " " + arch);
        }

        loadFromResource(libPath);
    }

    private static void loadFromResource(String resource) {
        try (InputStream res = TJLoader.class.getResourceAsStream(resource)) {
            if (res == null) {
                throw new RuntimeException("Native lib not found: " + resource);
            }

            // Crear archivo temporal
            File tempFile = File.createTempFile("libturbojpeg", getFileExtension(resource));
            tempFile.deleteOnExit(); // Eliminar después de ejecución

            // Copiar contenido
            Files.copy(res, tempFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);

            // Cargar la biblioteca
            System.load(tempFile.getAbsolutePath());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    private static String getFileExtension(String path) {
        if (path.endsWith(".dll")) return ".dll";
        if (path.endsWith(".so")) return ".so";
        if (path.endsWith(".dylib")) return ".dylib";
        return "";
    }
}
