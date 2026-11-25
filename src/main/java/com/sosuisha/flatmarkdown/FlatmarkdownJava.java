package com.sosuisha.flatmarkdown;

import java.io.IOException;
import java.io.InputStream;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

/**
 * Java bindings for the
 * <a href="https://github.com/sosuisen/flatmarkdown">Flatmarkdown</a>
 * native library via the Foreign Function &amp; Memory API.
 *
 * <p>
 * Flat Markdown is an outline format defined by the
 * <a href="https://github.com/sosuisen/flatmarkdown/blob/main/SPEC.md">Flat Markdown
 * specification</a>. A page is split into items by Markdown blocks. Each item
 * may carry a metadata line such as {@code <!-- @@ ID key="value" -->}.
 * </p>
 *
 * @see <a href="https://github.com/sosuisen/flatmarkdown">Flatmarkdown on
 *      GitHub</a>
 */
public class FlatmarkdownJava {

    private static final MethodHandle BODY_TO_HTML;
    private static final MethodHandle BODY_TO_AST;
    private static final MethodHandle PAGE_TO_AST;
    private static final MethodHandle FREE_STRING;

    static {
        loadNativeLibrary();
        SymbolLookup lookup = SymbolLookup.loaderLookup();
        Linker linker = Linker.nativeLinker();

        // const char* -> char* (returns heap-allocated string)
        FunctionDescriptor strToStr = FunctionDescriptor.of(
                ValueLayout.ADDRESS, ValueLayout.ADDRESS);

        // char* -> void (frees heap-allocated string)
        FunctionDescriptor freeStr = FunctionDescriptor.ofVoid(ValueLayout.ADDRESS);

        BODY_TO_HTML = linker.downcallHandle(
                lookup.find("body_to_html").orElseThrow(), strToStr);
        BODY_TO_AST = linker.downcallHandle(
                lookup.find("body_to_ast").orElseThrow(), strToStr);
        PAGE_TO_AST = linker.downcallHandle(
                lookup.find("page_to_ast").orElseThrow(), strToStr);
        FREE_STRING = linker.downcallHandle(
                lookup.find("free_string").orElseThrow(), freeStr);
    }

    /**
     * Parses a whole Flat Markdown page into a JSON string.
     *
     * <p>
     * The result has the form
     * {@code {"type":"page","front_matter":string|null,"items":[...]}}. Each item
     * has {@code level}, {@code id}, {@code properties}, {@code body}, and
     * {@code ast}. See the Flatmarkdown AST specification for details.
     * </p>
     *
     * @param input the Flat Markdown page; must not be {@code null}
     * @return the page as a JSON string
     * @throws RuntimeException     if the native call fails
     * @throws NullPointerException if {@code input} is {@code null}
     */
    public static String pageToAst(String input) {
        Objects.requireNonNull(input, "input must not be null");
        return callNative(PAGE_TO_AST, input);
    }

    /**
     * Converts the body of one Flat Markdown item to HTML.
     *
     * @param input the item body; must not be {@code null}
     * @return the resulting HTML string
     * @throws RuntimeException     if the native call fails
     * @throws NullPointerException if {@code input} is {@code null}
     */
    public static String bodyToHtml(String input) {
        Objects.requireNonNull(input, "input must not be null");
        return callNative(BODY_TO_HTML, input);
    }

    /**
     * Parses the body of one Flat Markdown item into an AST in JSON format.
     *
     * <p>
     * For details on the AST specification, see the
     * <a href="https://github.com/sosuisen/flatmarkdown">Flatmarkdown</a>
     * documentation.
     * </p>
     *
     * @param input the item body; must not be {@code null}
     * @return the AST as a JSON string
     * @throws RuntimeException     if the native call fails
     * @throws NullPointerException if {@code input} is {@code null}
     */
    public static String bodyToAst(String input) {
        Objects.requireNonNull(input, "input must not be null");
        return callNative(BODY_TO_AST, input);
    }

    /**
     * Converts a Markdown string to HTML.
     *
     * @param input the Markdown text to convert; must not be {@code null}
     * @return the resulting HTML string
     * @throws RuntimeException     if the native call fails
     * @throws NullPointerException if {@code input} is {@code null}
     * @deprecated Use {@link #bodyToHtml(String)}.
     */
    @Deprecated
    public static String markdownToHtml(String input) {
        return bodyToHtml(input);
    }

    /**
     * Converts a Markdown string to an AST in JSON format.
     *
     * @param input the Markdown text to parse; must not be {@code null}
     * @return the AST as a JSON string
     * @throws RuntimeException     if the native call fails
     * @throws NullPointerException if {@code input} is {@code null}
     * @deprecated Use {@link #bodyToAst(String)}.
     */
    @Deprecated
    public static String markdownToAst(String input) {
        return bodyToAst(input);
    }

    private static String callNative(MethodHandle handle, String input) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment inputSeg = arena.allocateFrom(input);
            MemorySegment resultPtr = (MemorySegment) handle.invokeExact(inputSeg);
            String result = resultPtr.reinterpret(Long.MAX_VALUE).getString(0);
            FREE_STRING.invokeExact(resultPtr);
            return result;
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    private static void loadNativeLibrary() {
        String os = System.getProperty("os.name", "").toLowerCase();
        String arch = System.getProperty("os.arch", "").toLowerCase();

        String resourceDir;
        String libName;
        if (os.contains("win") && arch.contains("amd64")) {
            resourceDir = "win32-x86-64";
            libName = "flatmarkdown_java.dll";
        } else if (os.contains("linux") && arch.contains("amd64")) {
            resourceDir = "linux-x86-64";
            libName = "libflatmarkdown_java.so";
        } else if (os.contains("mac") && arch.contains("aarch64")) {
            resourceDir = "darwin-aarch64";
            libName = "libflatmarkdown_java.dylib";
        } else {
            throw new UnsupportedOperationException(
                    "Unsupported platform: " + os + "/" + arch);
        }

        String resourcePath = "/" + resourceDir + "/" + libName;
        try (InputStream in = FlatmarkdownJava.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new RuntimeException("Native library not found in JAR: " + resourcePath);
            }
            Path tempFile = Files.createTempFile("flatmarkdown_java", libName);
            tempFile.toFile().deleteOnExit();
            Files.copy(in, tempFile, StandardCopyOption.REPLACE_EXISTING);
            System.load(tempFile.toAbsolutePath().toString());
        } catch (IOException e) {
            throw new RuntimeException("Failed to load native library", e);
        }
    }
}
