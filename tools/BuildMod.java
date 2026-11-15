import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Zero-dependency Java 17 direct build & verification pipeline for rpgtemplate (Minecraft 1.20.1 Forge).
 *
 * <p>Run directly with JDK 17: {@code java tools/BuildMod.java} (or {@code ./build.sh} / {@code build.bat}).</p>
 *
 * <ol>
 *   <li>Auto-provisions and caches the official Forge 1.20.1 SRG SDK + Minecraft 1.20.1 libraries.</li>
 *   <li>Compiles all overlay Java sources in {@code build0414-src/} directly against real SRG bytecode.</li>
 *   <li>Strips removed legacy {@code production/} classes/assets/mixins from the base JAR, merges
 *       {@code *_sounds_fragment.json} into {@code assets/rpgtemplate/sounds.json}, updates
 *       {@code META-INF/MANIFEST.MF} and {@code META-INF/mods.toml}, and overlays compiled classes + resources.</li>
 *   <li>Runs a post-build integrity & bytecode class-link verification pass on the output JAR.</li>
 * </ol>
 */
public final class BuildMod {
    private static final String MOJANG_MANIFEST_URL =
        "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
    private static final String FORGE_MAVEN = "https://maven.minecraftforge.net";
    private static final String CLEAN_MIXIN_CONFIGS =
        "rpgtemplate.nothingthere.mixins.json,rpgtemplate.fx.mixins.json";

    private static final Pattern LEGACY_ITEM_MODEL_OR_TEX = Pattern.compile(
        "^(body_adapter|bottle_.*|drug_.*|empty_fullbottle|evol_trigger|fabricator_.*|fullbottle_.*"
            + "|genius_fullbottle|hazard_catalyst|hazard_trigger|infection_cure|machine_.*|nebula_gas"
            + "|pandora_.*|research_sample|research_sampler|sample_fullbottle|smith_.*|surgery_kit"
            + "|workshop_manual)\\.(json|png)$"
    );

    private static final Set<String> LEGACY_EXACT_ENTRIES = Set.of(
        "rpgtemplate.build.client.mixins.json",
        "assets/rpgtemplate/fullbottle-art-sources.json",
        "assets/rpgtemplate/blockstates/fullbottle_fabricator.json",
        "assets/rpgtemplate/blockstates/pandora_box.json",
        "data/rpgtemplate/loot_tables/blocks/fullbottle_fabricator.json",
        "data/rpgtemplate/loot_tables/blocks/pandora_box.json",
        "data/rpgtemplate/recipes/empty_fullbottle.json",
        "data/rpgtemplate/recipes/fullbottle_fabricator.json",
        "data/rpgtemplate/recipes/pandora_box.json",
        "data/rpgtemplate/recipes/pandora_link.json"
    );

    public static void main(String[] args) throws Exception {
        long t0 = System.currentTimeMillis();
        Path repoRoot = findRepoRoot();
        Properties props = loadProperties(repoRoot.resolve("build.properties"));

        String modId = props.getProperty("mod_id", "rpgtemplate");
        String modVersion = envOrProp("MOD_VERSION", props.getProperty("mod_version", "0.4.14"));
        String mcVersion = props.getProperty("minecraft_version", "1.20.1");
        String forgeVersion = props.getProperty("forge_version", "47.3.0");
        String mcpVersion = props.getProperty("mcp_version", "20230612.114412");
        Path baseJar = repoRoot.resolve(props.getProperty("base_jar", "base/rpgtemplate-0.4.14.jar"));
        Path srcDir = repoRoot.resolve(props.getProperty("src_dir", "build0414-src"));
        Path resDir = repoRoot.resolve(props.getProperty("resources_dir", "build0414-resources"));
        Path outDir = repoRoot.resolve(props.getProperty("output_dir", "dist"));
        Path cacheDir = resolvePath(repoRoot, envOrProp("RPGTEMPLATE_BUILD_CACHE",
            props.getProperty("cache_dir", ".build-cache")));
        boolean skipVerify = false;
        boolean cleanBuild = false;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--version" -> modVersion = requireArg(args, ++i, "--version");
                case "--base-jar" -> baseJar = resolvePath(repoRoot, requireArg(args, ++i, "--base-jar"));
                case "--output-dir" -> outDir = resolvePath(repoRoot, requireArg(args, ++i, "--output-dir"));
                case "--cache-dir" -> cacheDir = resolvePath(repoRoot, requireArg(args, ++i, "--cache-dir"));
                case "--skip-verify" -> skipVerify = true;
                case "--clean" -> cleanBuild = true;
                case "-h", "--help" -> {
                    printHelp();
                    return;
                }
                default -> throw new IllegalArgumentException("Unknown argument: " + args[i]);
            }
        }

        checkJavaVersion();
        if (!Files.isRegularFile(baseJar)) {
            throw new IllegalStateException("Base JAR not found: " + baseJar
                + "\nPlace rpgtemplate-0.4.14.jar at base/rpgtemplate-0.4.14.jar or pass --base-jar <path>.");
        }
        if (!Files.isDirectory(srcDir) || !Files.isDirectory(resDir)) {
            throw new IllegalStateException("Source or resource directory missing: " + srcDir + ", " + resDir);
        }

        Path buildWorkDir = repoRoot.resolve("build");
        Path classesDir = buildWorkDir.resolve("classes");
        if (cleanBuild && Files.exists(buildWorkDir)) {
            deleteRecursively(buildWorkDir);
        }
        if (Files.exists(classesDir)) {
            deleteRecursively(classesDir);
        }
        Files.createDirectories(classesDir);
        Files.createDirectories(outDir);

        System.out.println("=== rpgtemplate direct Forge 1.20.1 build ===");
        System.out.println("  Version   : " + modVersion);
        System.out.println("  Forge SDK : " + mcVersion + "-" + forgeVersion);
        System.out.println("  Base JAR  : " + baseJar);
        System.out.println("  Cache dir : " + cacheDir);

        // Step 1: Provision cached Forge SRG SDK + vanilla 1.20.1 libraries
        Path forgeEnv = cacheDir.resolve("forge-" + mcVersion + "-" + forgeVersion);
        provisionForgeSdk(forgeEnv, mcVersion, forgeVersion, mcpVersion);

        // Step 2: Compile all overlay Java sources against real Forge/MC 1.20.1 SRG classpath
        List<Path> classpath = buildCompileClasspath(forgeEnv, mcVersion, forgeVersion, mcpVersion, baseJar);
        compileSources(srcDir, classesDir, buildWorkDir, classpath);

        // Step 3: Assemble clean mod JAR
        Path outJar = outDir.resolve(modId + "-" + modVersion + ".jar");
        assembleJar(baseJar, classesDir, resDir, outJar, modVersion);

        // Step 4: Post-build verification gate
        if (!skipVerify) {
            verifyJar(outJar, modVersion);
        }

        long elapsed = System.currentTimeMillis() - t0;
        System.out.printf(Locale.ROOT,
            "=== BUILD SUCCESSFUL in %.2fs -> %s (%,d bytes) ===%n",
            elapsed / 1000.0, outJar, Files.size(outJar));
    }

    private static void printHelp() {
        System.out.println("""
            Usage: java tools/BuildMod.java [options]
              --version <ver>      Override output mod version (default from build.properties)
              --base-jar <path>    Override base rpgtemplate-0.4.14.jar path
              --output-dir <dir>   Override output directory (default: dist)
              --cache-dir <dir>    Override Forge/MC SDK cache directory (default: .build-cache)
              --clean              Clean build/ directory before compiling
              --skip-verify        Skip post-build JAR verification checks
              -h, --help           Show this help message
            """);
    }

    private static String requireArg(String[] args, int idx, String flag) {
        if (idx >= args.length) {
            throw new IllegalArgumentException("Missing value for " + flag);
        }
        return args[idx];
    }

    private static void checkJavaVersion() {
        int feature = Runtime.version().feature();
        if (feature < 17) {
            throw new IllegalStateException("JDK 17+ is required (current Java: " + Runtime.version() + ").");
        }
    }

    private static Path findRepoRoot() {
        Path cwd = Paths.get("").toAbsolutePath().normalize();
        if (Files.isDirectory(cwd.resolve("build0414-src"))) {
            return cwd;
        }
        if (cwd.getParent() != null && Files.isDirectory(cwd.getParent().resolve("build0414-src"))) {
            return cwd.getParent();
        }
        return cwd;
    }

    private static Path resolvePath(Path root, String raw) {
        Path p = Paths.get(raw);
        return p.isAbsolute() ? p.normalize() : root.resolve(p).normalize();
    }

    private static String envOrProp(String envKey, String fallback) {
        String v = System.getenv(envKey);
        return (v != null && !v.isBlank()) ? v.trim() : fallback;
    }

    private static Properties loadProperties(Path path) throws IOException {
        Properties props = new Properties();
        if (Files.isRegularFile(path)) {
            try (InputStream in = Files.newInputStream(path)) {
                props.load(in);
            }
        }
        return props;
    }

    // =========================================================================
    // Step 1: Provision official Forge 1.20.1 SRG SDK + Minecraft 1.20.1 libs
    // =========================================================================

    private static void provisionForgeSdk(Path forgeEnv, String mcVersion, String forgeVersion, String mcpVersion)
            throws Exception {
        Path forgeClientJar = forgeEnv.resolve(
            "libraries/net/minecraftforge/forge/" + mcVersion + "-" + forgeVersion
                + "/forge-" + mcVersion + "-" + forgeVersion + "-client.jar");
        Path forgeUniversalJar = forgeEnv.resolve(
            "libraries/net/minecraftforge/forge/" + mcVersion + "-" + forgeVersion
                + "/forge-" + mcVersion + "-" + forgeVersion + "-universal.jar");
        Path mcSrgJar = forgeEnv.resolve(
            "libraries/net/minecraft/client/" + mcVersion + "-" + mcpVersion
                + "/client-" + mcVersion + "-" + mcpVersion + "-srg.jar");
        Path mcLibsDir = forgeEnv.resolve("mc_libs");
        Path mcLibsMarker = mcLibsDir.resolve(".complete-" + mcVersion);

        HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(30))
            .build();

        if (!Files.isRegularFile(forgeClientJar) || !Files.isRegularFile(forgeUniversalJar)
                || !Files.isRegularFile(mcSrgJar)) {
            System.out.println("[1/4] Provisioning Forge " + mcVersion + "-" + forgeVersion + " SRG environment...");
            Files.createDirectories(forgeEnv);
            Path profiles = forgeEnv.resolve("launcher_profiles.json");
            if (!Files.exists(profiles)) {
                Files.writeString(profiles, "{\"profiles\":{}}\n", StandardCharsets.UTF_8);
            }
            Path installer = forgeEnv.resolve("forge-" + mcVersion + "-" + forgeVersion + "-installer.jar");
            String installerUrl = FORGE_MAVEN + "/net/minecraftforge/forge/"
                + mcVersion + "-" + forgeVersion + "/forge-" + mcVersion + "-" + forgeVersion + "-installer.jar";
            if (!isValidZip(installer)) {
                System.out.println("  Downloading " + installerUrl);
                downloadWithRetry(http, installerUrl, installer);
            }

            String javaBin = findJavaBinary("java");
            ProcessBuilder pb = new ProcessBuilder(
                javaBin, "-Djava.awt.headless=true", "-jar", installer.toString(),
                "--installClient", forgeEnv.toString());
            pb.directory(forgeEnv.toFile());
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            StringBuilder logTail = new StringBuilder();
            try (InputStream is = proc.getInputStream()) {
                byte[] buf = new byte[4096];
                int n;
                while ((n = is.read(buf)) != -1) {
                    String chunk = new String(buf, 0, n, StandardCharsets.UTF_8);
                    logTail.append(chunk);
                    if (logTail.length() > 8000) {
                        logTail.delete(0, logTail.length() - 8000);
                    }
                }
            }
            int code = proc.waitFor();
            if (code != 0 || !Files.isRegularFile(forgeClientJar) || !Files.isRegularFile(mcSrgJar)) {
                throw new IllegalStateException("Forge installer failed (exit=" + code + "):\n" + logTail);
            }
            System.out.println("  Forge SRG environment ready.");
        } else {
            System.out.println("[1/4] Using cached Forge " + mcVersion + "-" + forgeVersion + " SRG environment.");
        }

        if (!Files.isRegularFile(mcLibsMarker)) {
            System.out.println("  Downloading vanilla Minecraft " + mcVersion + " library JARs...");
            Files.createDirectories(mcLibsDir);
            downloadMinecraftLibraries(http, mcVersion, mcLibsDir);
            Files.writeString(mcLibsMarker, "ok\n", StandardCharsets.UTF_8);
        }
    }

    @SuppressWarnings("unchecked")
    private static void downloadMinecraftLibraries(HttpClient http, String mcVersion, Path mcLibsDir)
            throws Exception {
        String manifestBody = fetchStringWithRetry(http, MOJANG_MANIFEST_URL);
        Map<String, Object> manifest = (Map<String, Object>) JsonCodec.parse(manifestBody);
        List<Object> versions = (List<Object>) manifest.get("versions");
        String versionUrl = null;
        for (Object item : versions) {
            Map<String, Object> v = (Map<String, Object>) item;
            if (mcVersion.equals(v.get("id"))) {
                versionUrl = (String) v.get("url");
                break;
            }
        }
        if (versionUrl == null) {
            throw new IllegalStateException("Minecraft version " + mcVersion + " not found in Mojang manifest.");
        }

        String versionBody = fetchStringWithRetry(http, versionUrl);
        Map<String, Object> vJson = (Map<String, Object>) JsonCodec.parse(versionBody);
        List<Object> libs = (List<Object>) vJson.get("libraries");

        List<String[]> tasks = new ArrayList<>();
        Set<String> seenFiles = new HashSet<>();
        for (Object item : libs) {
            Map<String, Object> lib = (Map<String, Object>) item;
            Map<String, Object> downloads = (Map<String, Object>) lib.get("downloads");
            if (downloads == null) continue;
            Map<String, Object> artifact = (Map<String, Object>) downloads.get("artifact");
            if (artifact == null) continue;
            String url = (String) artifact.get("url");
            String relPath = (String) artifact.get("path");
            if (url == null || relPath == null) continue;
            String fileName = relPath.substring(relPath.lastIndexOf('/') + 1);
            if (!seenFiles.add(fileName)) continue;
            Path dest = mcLibsDir.resolve(fileName);
            if (!isValidZip(dest)) {
                tasks.add(new String[]{url, dest.toString()});
            }
        }

        if (!tasks.isEmpty()) {
            ExecutorService pool = Executors.newFixedThreadPool(8);
            try {
                List<Future<?>> futures = new ArrayList<>();
                for (String[] task : tasks) {
                    futures.add(pool.submit(() -> {
                        downloadWithRetry(http, task[0], Paths.get(task[1]));
                        return null;
                    }));
                }
                for (Future<?> f : futures) {
                    f.get();
                }
            } finally {
                pool.shutdownNow();
            }
        }
        System.out.println("  Vanilla libraries ready (" + seenFiles.size() + " JARs).");
    }

    private static String fetchStringWithRetry(HttpClient http, String url) throws Exception {
        Exception last = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(45))
                    .GET().build();
                HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                    return resp.body();
                }
                throw new IOException("HTTP " + resp.statusCode() + " for " + url);
            } catch (Exception e) {
                last = e;
                Thread.sleep(500L * attempt);
            }
        }
        throw last;
    }

    private static void downloadWithRetry(HttpClient http, String url, Path dest) throws Exception {
        Files.createDirectories(dest.getParent());
        Exception last = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            Path tmp = dest.resolveSibling(dest.getFileName() + ".part");
            try {
                HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(120))
                    .GET().build();
                HttpResponse<Path> resp = http.send(req, HttpResponse.BodyHandlers.ofFile(tmp));
                if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                    throw new IOException("HTTP " + resp.statusCode() + " for " + url);
                }
                Files.move(tmp, dest, StandardCopyOption.REPLACE_EXISTING);
                return;
            } catch (Exception e) {
                Files.deleteIfExists(tmp);
                last = e;
                Thread.sleep(600L * attempt);
            }
        }
        throw last;
    }

    private static boolean isValidZip(Path path) {
        if (!Files.isRegularFile(path)) return false;
        try (ZipFile zf = new ZipFile(path.toFile())) {
            return zf.size() > 0;
        } catch (IOException e) {
            return false;
        }
    }

    // =========================================================================
    // Step 2: Compile build0414-src with JDK 17 javac against real SRG classes
    // =========================================================================

    private static List<Path> buildCompileClasspath(
            Path forgeEnv, String mcVersion, String forgeVersion, String mcpVersion, Path baseJar)
            throws IOException {
        List<Path> cp = new ArrayList<>();
        // Strict precedence: Forge-patched MC SRG > Forge API > Vanilla MC SRG > Base mod JAR > libs
        cp.add(forgeEnv.resolve("libraries/net/minecraftforge/forge/" + mcVersion + "-" + forgeVersion
            + "/forge-" + mcVersion + "-" + forgeVersion + "-client.jar"));
        cp.add(forgeEnv.resolve("libraries/net/minecraftforge/forge/" + mcVersion + "-" + forgeVersion
            + "/forge-" + mcVersion + "-" + forgeVersion + "-universal.jar"));
        cp.add(forgeEnv.resolve("libraries/net/minecraft/client/" + mcVersion + "-" + mcpVersion
            + "/client-" + mcVersion + "-" + mcpVersion + "-srg.jar"));
        cp.add(baseJar);

        Path forgeLibs = forgeEnv.resolve("libraries");
        try (Stream<Path> walk = Files.walk(forgeLibs)) {
            walk.filter(Files::isRegularFile)
                .filter(p -> p.getFileName().toString().endsWith(".jar"))
                .filter(p -> {
                    String n = p.getFileName().toString();
                    return !n.startsWith("client-" + mcVersion + "-")
                        && !n.startsWith("forge-" + mcVersion + "-")
                        && !n.startsWith("ForgeAutoRenamingTool");
                })
                .sorted()
                .forEach(cp::add);
        }

        Path mcLibs = forgeEnv.resolve("mc_libs");
        try (Stream<Path> walk = Files.list(mcLibs)) {
            walk.filter(Files::isRegularFile)
                .filter(p -> p.getFileName().toString().endsWith(".jar"))
                .sorted()
                .forEach(cp::add);
        }
        return cp;
    }

    private static void compileSources(Path srcDir, Path classesDir, Path workDir, List<Path> classpath)
            throws Exception {
        List<Path> sources;
        try (Stream<Path> walk = Files.walk(srcDir)) {
            sources = walk.filter(Files::isRegularFile)
                .filter(p -> p.getFileName().toString().endsWith(".java"))
                .sorted()
                .toList();
        }
        System.out.println("[2/4] Compiling " + sources.size() + " Java source files (--release 17)...");

        StringBuilder cpJoined = new StringBuilder();
        for (int i = 0; i < classpath.size(); i++) {
            if (i > 0) cpJoined.append(File.pathSeparator);
            cpJoined.append(classpath.get(i).toAbsolutePath());
        }

        // Write @argfile to stay immune to OS command-line length limits (Windows 8191-char cap)
        Path argFile = workDir.resolve("javac.args");
        StringBuilder argsBuf = new StringBuilder();
        argsBuf.append("--release\n17\n-encoding\nUTF-8\n-Xlint:none\n-cp\n");
        argsBuf.append(quoteArgFileEntry(cpJoined.toString())).append('\n');
        argsBuf.append("-d\n").append(quoteArgFileEntry(classesDir.toAbsolutePath().toString())).append('\n');
        for (Path src : sources) {
            argsBuf.append(quoteArgFileEntry(src.toAbsolutePath().toString())).append('\n');
        }
        Files.writeString(argFile, argsBuf.toString(), StandardCharsets.UTF_8);

        String javacBin = findJavaBinary("javac");
        ProcessBuilder pb = new ProcessBuilder(javacBin, "@" + argFile.toAbsolutePath());
        pb.inheritIO();
        int code = pb.start().waitFor();
        if (code != 0) {
            throw new IllegalStateException("javac failed with exit code " + code);
        }

        long classCount;
        try (Stream<Path> walk = Files.walk(classesDir)) {
            classCount = walk.filter(Files::isRegularFile)
                .filter(p -> p.getFileName().toString().endsWith(".class"))
                .count();
        }
        System.out.println("  Compiled " + classCount + " class files.");
    }

    private static String quoteArgFileEntry(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static String findJavaBinary(String tool) {
        boolean win = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        Path bin = Paths.get(System.getProperty("java.home"), "bin", win ? tool + ".exe" : tool);
        if (Files.isExecutable(bin)) {
            return bin.toString();
        }
        return tool;
    }

    // =========================================================================
    // Step 3: Assemble clean, runnable Forge 1.20.1 mod JAR
    // =========================================================================

    private static void assembleJar(Path baseJar, Path classesDir, Path resDir, Path outJar, String modVersion)
            throws IOException {
        System.out.println("[3/4] Assembling mod JAR -> " + outJar.getFileName() + "...");

        // Collect newly compiled classes and their outer class names (to drop stale $ inner classes from base JAR)
        Map<String, byte[]> entries = new TreeMap<>();
        Set<String> compiledOuterClasses = new HashSet<>();
        try (Stream<Path> walk = Files.walk(classesDir)) {
            for (Path p : walk.filter(Files::isRegularFile).toList()) {
                String rel = toZipPath(classesDir.relativize(p));
                entries.put(rel, Files.readAllBytes(p));
                if (rel.endsWith(".class")) {
                    compiledOuterClasses.add(outerClassOf(rel));
                }
            }
        }

        // Collect overlay resources (excluding root *_sounds_fragment.json which are merged into sounds.json)
        List<Path> soundFragments = new ArrayList<>();
        Set<String> overlayResourcePaths = new HashSet<>();
        try (Stream<Path> walk = Files.walk(resDir)) {
            for (Path p : walk.filter(Files::isRegularFile).sorted().toList()) {
                String rel = toZipPath(resDir.relativize(p));
                if (!rel.contains("/") && rel.endsWith("_sounds_fragment.json")) {
                    soundFragments.add(p);
                    continue;
                }
                if (isLegacyResource(rel)) {
                    continue;
                }
                overlayResourcePaths.add(rel);
                entries.put(rel, Files.readAllBytes(p));
            }
        }

        String baseSoundsRaw = null;
        String baseModsToml = null;
        int keptBaseEntries = 0;
        int strippedLegacyEntries = 0;

        try (ZipFile zf = new ZipFile(baseJar.toFile(), StandardCharsets.UTF_8)) {
            Enumeration<? extends ZipEntry> en = zf.entries();
            while (en.hasMoreElements()) {
                ZipEntry ze = en.nextElement();
                if (ze.isDirectory()) continue;
                String name = ze.getName();

                if ("assets/rpgtemplate/sounds.json".equals(name)) {
                    baseSoundsRaw = new String(readAll(zf, ze), StandardCharsets.UTF_8);
                    continue;
                }
                if ("META-INF/mods.toml".equals(name)) {
                    baseModsToml = new String(readAll(zf, ze), StandardCharsets.UTF_8);
                    continue;
                }
                if ("META-INF/MANIFEST.MF".equals(name)) {
                    continue;
                }
                if (isLegacyBaseEntry(name)) {
                    strippedLegacyEntries++;
                    continue;
                }
                if (name.endsWith(".class") && compiledOuterClasses.contains(outerClassOf(name))) {
                    continue;
                }
                if (overlayResourcePaths.contains(name) || entries.containsKey(name)) {
                    continue;
                }

                entries.put(name, readAll(zf, ze));
                keptBaseEntries++;
            }
        }

        // Merge sounds.json (base + all *_sounds_fragment.json)
        byte[] mergedSounds = mergeSoundsJson(baseSoundsRaw, soundFragments);
        entries.put("assets/rpgtemplate/sounds.json", mergedSounds);

        // Generate clean META-INF/MANIFEST.MF (without deleted rpgtemplate.build.client.mixins.json)
        String manifest = "Manifest-Version: 1.0\r\n"
            + "Implementation-Version: " + modVersion + "\r\n"
            + "MixinConfigs: " + CLEAN_MIXIN_CONFIGS + "\r\n\r\n";
        entries.put("META-INF/MANIFEST.MF", manifest.getBytes(StandardCharsets.UTF_8));

        // Update META-INF/mods.toml version/displayName cleanly
        if (baseModsToml == null) {
            throw new IllegalStateException("Base JAR is missing META-INF/mods.toml");
        }
        String updatedModsToml = updateModsToml(baseModsToml, modVersion);
        entries.put("META-INF/mods.toml", updatedModsToml.getBytes(StandardCharsets.UTF_8));

        // Write output JAR (META-INF/MANIFEST.MF first per JAR spec)
        Path tmpJar = outJar.resolveSibling(outJar.getFileName() + ".tmp");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(tmpJar), StandardCharsets.UTF_8)) {
            writeZipEntry(zos, "META-INF/MANIFEST.MF", entries.get("META-INF/MANIFEST.MF"));
            for (Map.Entry<String, byte[]> e : entries.entrySet()) {
                if ("META-INF/MANIFEST.MF".equals(e.getKey())) continue;
                writeZipEntry(zos, e.getKey(), e.getValue());
            }
        }
        Files.move(tmpJar, outJar, StandardCopyOption.REPLACE_EXISTING);

        System.out.println("  Kept base entries: " + keptBaseEntries
            + ", stripped legacy entries: " + strippedLegacyEntries
            + ", total output entries: " + entries.size());
    }

    private static void writeZipEntry(ZipOutputStream zos, String name, byte[] bytes) throws IOException {
        ZipEntry ze = new ZipEntry(name);
        ze.setTime(0L); // deterministic builds
        zos.putNextEntry(ze);
        zos.write(bytes);
        zos.closeEntry();
    }

    private static boolean isLegacyBaseEntry(String name) {
        if (name.endsWith(".class") && name.contains("/production/")) {
            return true;
        }
        return isLegacyResource(name);
    }

    private static boolean isLegacyResource(String name) {
        if (LEGACY_EXACT_ENTRIES.contains(name)) return true;
        if (name.startsWith("data/rpgtemplate/production/")) return true;
        if (name.startsWith("assets/rpgtemplate/models/block/fullbottle_fabricator")
                || name.startsWith("assets/rpgtemplate/models/block/pandora_box")
                || name.startsWith("assets/rpgtemplate/textures/block/pandora_box")
                || name.startsWith("assets/rpgtemplate/shaders/core/build_energy.")) {
            return true;
        }
        if (name.startsWith("assets/rpgtemplate/models/item/")) {
            String file = name.substring("assets/rpgtemplate/models/item/".length());
            if (LEGACY_ITEM_MODEL_OR_TEX.matcher(file).matches()) return true;
        }
        if (name.startsWith("assets/rpgtemplate/textures/item/")) {
            String file = name.substring("assets/rpgtemplate/textures/item/".length());
            if (LEGACY_ITEM_MODEL_OR_TEX.matcher(file).matches()) return true;
        }
        return false;
    }

    private static String outerClassOf(String classEntry) {
        String noExt = classEntry.substring(0, classEntry.length() - 6);
        int dollar = noExt.indexOf('$');
        return dollar >= 0 ? noExt.substring(0, dollar) : noExt;
    }

    @SuppressWarnings("unchecked")
    private static byte[] mergeSoundsJson(String baseSoundsRaw, List<Path> fragments) throws IOException {
        Map<String, Object> merged = new LinkedHashMap<>();
        if (baseSoundsRaw != null && !baseSoundsRaw.isBlank()) {
            merged.putAll((Map<String, Object>) JsonCodec.parse(baseSoundsRaw));
        }
        for (Path frag : fragments) {
            String raw = Files.readString(frag, StandardCharsets.UTF_8);
            Map<String, Object> parsed = (Map<String, Object>) JsonCodec.parse(raw);
            merged.putAll(parsed);
        }
        return (JsonCodec.stringify(merged, 0) + "\n").getBytes(StandardCharsets.UTF_8);
    }

    private static String updateModsToml(String raw, String modVersion) {
        String out = raw.replaceFirst("(?m)^version\\s*=\\s*\"[^\"]*\"", "version=\"" + modVersion + "\"");
        out = out.replaceFirst("(?m)^displayName\\s*=\\s*\"[^\"]*\"", "displayName=\"rpgtemplate " + modVersion + "\"");
        return out;
    }

    // =========================================================================
    // Step 4: Post-build JAR integrity & bytecode link verification
    // =========================================================================

    @SuppressWarnings("unchecked")
    private static void verifyJar(Path jarPath, String expectedVersion) throws IOException {
        System.out.println("[4/4] Verifying assembled JAR...");
        List<String> failures = new ArrayList<>();
        Map<String, byte[]> classes = new HashMap<>();
        Set<String> allEntries = new HashSet<>();

        try (ZipFile zf = new ZipFile(jarPath.toFile(), StandardCharsets.UTF_8)) {
            Enumeration<? extends ZipEntry> en = zf.entries();
            while (en.hasMoreElements()) {
                ZipEntry ze = en.nextElement();
                if (ze.isDirectory()) continue;
                String name = ze.getName();
                allEntries.add(name);
                if (name.endsWith(".class")) {
                    classes.put(name.substring(0, name.length() - 6), readAll(zf, ze));
                }
            }

            // 1. Check MANIFEST.MF & declared MixinConfigs exist
            ZipEntry mfEntry = zf.getEntry("META-INF/MANIFEST.MF");
            if (mfEntry == null) {
                failures.add("Missing META-INF/MANIFEST.MF");
            } else {
                String mf = new String(readAll(zf, mfEntry), StandardCharsets.UTF_8);
                if (mf.contains("rpgtemplate.build.client.mixins.json")) {
                    failures.add("MANIFEST.MF still references removed rpgtemplate.build.client.mixins.json");
                }
                for (String line : mf.split("\\r?\\n")) {
                    if (line.startsWith("MixinConfigs:")) {
                        for (String cfg : line.substring("MixinConfigs:".length()).trim().split(",")) {
                            String c = cfg.trim();
                            if (!c.isEmpty() && !allEntries.contains(c)) {
                                failures.add("MANIFEST.MF MixinConfig missing from JAR: " + c);
                            }
                        }
                    }
                }
            }

            // 2. Check META-INF/mods.toml
            ZipEntry tomlEntry = zf.getEntry("META-INF/mods.toml");
            if (tomlEntry == null) {
                failures.add("Missing META-INF/mods.toml");
            } else {
                String toml = new String(readAll(zf, tomlEntry), StandardCharsets.UTF_8);
                if (!toml.contains("version=\"" + expectedVersion + "\"")) {
                    failures.add("META-INF/mods.toml does not contain expected version " + expectedVersion);
                }
            }

            // 3. Check no legacy production classes (except CraftMath), no stray root fragments
            for (String name : allEntries) {
                if (name.endsWith(".class") && name.contains("/production/")
                        && !"com/rpgtemplate/core/production/CraftMath.class".equals(name)) {
                    failures.add("Unexpected legacy production class in JAR: " + name);
                }
                if (!name.contains("/") && name.endsWith("_sounds_fragment.json")) {
                    failures.add("Unmerged sound fragment leaked into JAR root: " + name);
                }
                if ("rpgtemplate.build.client.mixins.json".equals(name)) {
                    failures.add("Removed mixin config still present in JAR: " + name);
                }
            }

            // 4. Verify RpgTemplateMod links NtBootstrap and NOT CraftBootstrap
            byte[] modClass = classes.get("com/rpgtemplate/forge/RpgTemplateMod");
            if (modClass == null) {
                failures.add("Missing com/rpgtemplate/forge/RpgTemplateMod.class");
            } else {
                Set<String> modRefs = readConstantPoolClasses(modClass);
                if (!modRefs.contains("com/rpgtemplate/nothingthere/NtBootstrap")) {
                    failures.add("RpgTemplateMod does not reference NtBootstrap");
                }
                if (modRefs.contains("com/rpgtemplate/forge/production/CraftBootstrap")) {
                    failures.add("RpgTemplateMod still references removed CraftBootstrap");
                }
            }

            // 5. Validate merged sounds.json, lang files, and all template JSONs
            ZipEntry soundsEntry = zf.getEntry("assets/rpgtemplate/sounds.json");
            if (soundsEntry == null) {
                failures.add("Missing assets/rpgtemplate/sounds.json");
            } else {
                Map<String, Object> sounds = (Map<String, Object>) JsonCodec.parse(
                    new String(readAll(zf, soundsEntry), StandardCharsets.UTF_8));
                for (String req : List.of("nt_goodbye_windup", "nt_ego_bgm", "cronus_henshin", "cronus_pause")) {
                    if (!sounds.containsKey(req)) {
                        failures.add("sounds.json missing expected key: " + req);
                    }
                }
            }

            ZipEntry zhEntry = zf.getEntry("assets/rpgtemplate/lang/zh_cn.json");
            ZipEntry enEntry = zf.getEntry("assets/rpgtemplate/lang/en_us.json");
            if (zhEntry == null || enEntry == null) {
                failures.add("Missing lang/zh_cn.json or lang/en_us.json");
            } else {
                Map<String, Object> zh = (Map<String, Object>) JsonCodec.parse(
                    new String(readAll(zf, zhEntry), StandardCharsets.UTF_8));
                Map<String, Object> enLang = (Map<String, Object>) JsonCodec.parse(
                    new String(readAll(zf, enEntry), StandardCharsets.UTF_8));
                if (!zh.keySet().equals(enLang.keySet())) {
                    failures.add("zh_cn.json (" + zh.size() + ") and en_us.json (" + enLang.size() + ") keys differ");
                }
            }

            int templateCount = 0;
            for (String name : allEntries) {
                if (name.startsWith("data/rpgtemplate/rpg_templates/") && name.endsWith(".json")) {
                    JsonCodec.parse(new String(readAll(zf, zf.getEntry(name)), StandardCharsets.UTF_8));
                    templateCount++;
                }
            }
            if (templateCount < 11) {
                failures.add("Expected >= 11 rpg_templates JSONs, found " + templateCount);
            }

            // 6. Bytecode internal class-link check across ALL classes in output JAR
            Set<String> brokenRefs = new TreeSet<>();
            for (Map.Entry<String, byte[]> e : classes.entrySet()) {
                for (String ref : readConstantPoolClasses(e.getValue())) {
                    String norm = normalizeInternalClass(ref);
                    if (norm != null && norm.startsWith("com/rpgtemplate/") && !classes.containsKey(norm)) {
                        brokenRefs.add(e.getKey() + " -> " + norm);
                    }
                }
            }
            for (String br : brokenRefs) {
                failures.add("Broken internal class reference: " + br);
            }

            if (!failures.isEmpty()) {
                StringBuilder sb = new StringBuilder("JAR verification FAILED (" + failures.size() + " issues):\n");
                for (String f : failures) {
                    sb.append("  - ").append(f).append('\n');
                }
                throw new IllegalStateException(sb.toString());
            }

            System.out.println("  Verified OK: " + classes.size() + " classes (0 broken internal links), "
                + templateCount + " templates, clean Manifest/MixinConfigs/sounds.json/lang.");
        }
    }

    private static String normalizeInternalClass(String raw) {
        String s = raw;
        while (s.startsWith("[")) s = s.substring(1);
        if (s.startsWith("L") && s.endsWith(";")) {
            s = s.substring(1, s.length() - 1);
        }
        return s.contains("/") ? s : null;
    }

    private static Set<String> readConstantPoolClasses(byte[] classBytes) throws IOException {
        Set<String> result = new HashSet<>();
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(classBytes))) {
            int magic = in.readInt();
            if (magic != 0xCAFEBABE) return result;
            in.readUnsignedShort(); // minor
            in.readUnsignedShort(); // major
            int cpCount = in.readUnsignedShort();
            Map<Integer, String> utf8 = new HashMap<>();
            List<Integer> classNameIndices = new ArrayList<>();
            for (int i = 1; i < cpCount; i++) {
                int tag = in.readUnsignedByte();
                switch (tag) {
                    case 1 -> utf8.put(i, in.readUTF());
                    case 3, 4 -> in.skipBytes(4);
                    case 5, 6 -> {
                        in.skipBytes(8);
                        i++; // longs and doubles occupy two constant-pool slots
                    }
                    case 7 -> classNameIndices.add(in.readUnsignedShort());
                    case 8, 16, 19, 20 -> in.skipBytes(2);
                    case 9, 10, 11, 12, 17, 18 -> in.skipBytes(4);
                    case 15 -> in.skipBytes(3);
                    default -> {
                        return result;
                    }
                }
            }
            for (int idx : classNameIndices) {
                String name = utf8.get(idx);
                if (name != null) result.add(name);
            }
        }
        return result;
    }

    private static byte[] readAll(ZipFile zf, ZipEntry ze) throws IOException {
        try (InputStream in = new BufferedInputStream(zf.getInputStream(ze))) {
            return in.readAllBytes();
        }
    }

    private static String toZipPath(Path rel) {
        return rel.toString().replace('\\', '/');
    }

    private static void deleteRecursively(Path dir) throws IOException {
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }

    // =========================================================================
    // Minimal RFC-8259 JSON parser & pretty-printer (zero external dependencies)
    // =========================================================================

    private static final class JsonCodec {
        private final String s;
        private int pos;

        private JsonCodec(String s) {
            this.s = s;
        }

        static Object parse(String text) {
            JsonCodec p = new JsonCodec(text);
            p.skipWs();
            Object v = p.readValue();
            p.skipWs();
            if (p.pos != p.s.length()) {
                throw new IllegalArgumentException("Trailing JSON content at offset " + p.pos);
            }
            return v;
        }

        private Object readValue() {
            skipWs();
            if (pos >= s.length()) throw new IllegalArgumentException("Unexpected end of JSON");
            char c = s.charAt(pos);
            return switch (c) {
                case '{' -> readObject();
                case '[' -> readArray();
                case '"' -> readString();
                case 't' -> expectLiteral("true", Boolean.TRUE);
                case 'f' -> expectLiteral("false", Boolean.FALSE);
                case 'n' -> expectLiteral("null", null);
                default -> readNumber();
            };
        }

        private Map<String, Object> readObject() {
            pos++; // '{'
            Map<String, Object> map = new LinkedHashMap<>();
            skipWs();
            if (peek('}')) {
                pos++;
                return map;
            }
            while (true) {
                skipWs();
                String key = readString();
                skipWs();
                expect(':');
                Object val = readValue();
                map.put(key, val);
                skipWs();
                if (peek(',')) {
                    pos++;
                } else if (peek('}')) {
                    pos++;
                    return map;
                } else {
                    throw new IllegalArgumentException("Expected ',' or '}' at offset " + pos);
                }
            }
        }

        private List<Object> readArray() {
            pos++; // '['
            List<Object> list = new ArrayList<>();
            skipWs();
            if (peek(']')) {
                pos++;
                return list;
            }
            while (true) {
                list.add(readValue());
                skipWs();
                if (peek(',')) {
                    pos++;
                } else if (peek(']')) {
                    pos++;
                    return list;
                } else {
                    throw new IllegalArgumentException("Expected ',' or ']' at offset " + pos);
                }
            }
        }

        private String readString() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (pos < s.length()) {
                char c = s.charAt(pos++);
                if (c == '"') return sb.toString();
                if (c == '\\') {
                    if (pos >= s.length()) throw new IllegalArgumentException("Unterminated escape");
                    char e = s.charAt(pos++);
                    switch (e) {
                        case '"', '\\', '/' -> sb.append(e);
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        case 'n' -> sb.append('\n');
                        case 'r' -> sb.append('\r');
                        case 't' -> sb.append('\t');
                        case 'u' -> {
                            String hex = s.substring(pos, pos + 4);
                            sb.append((char) Integer.parseInt(hex, 16));
                            pos += 4;
                        }
                        default -> throw new IllegalArgumentException("Invalid escape \\" + e);
                    }
                } else {
                    sb.append(c);
                }
            }
            throw new IllegalArgumentException("Unterminated JSON string");
        }

        private Object expectLiteral(String lit, Object val) {
            if (!s.startsWith(lit, pos)) {
                throw new IllegalArgumentException("Expected " + lit + " at offset " + pos);
            }
            pos += lit.length();
            return val;
        }

        private Number readNumber() {
            int start = pos;
            if (peek('-')) pos++;
            while (pos < s.length() && Character.isDigit(s.charAt(pos))) pos++;
            boolean isFloat = false;
            if (peek('.')) {
                isFloat = true;
                pos++;
                while (pos < s.length() && Character.isDigit(s.charAt(pos))) pos++;
            }
            if (peek('e') || peek('E')) {
                isFloat = true;
                pos++;
                if (peek('+') || peek('-')) pos++;
                while (pos < s.length() && Character.isDigit(s.charAt(pos))) pos++;
            }
            String token = s.substring(start, pos);
            if (token.isEmpty()) throw new IllegalArgumentException("Invalid JSON token at offset " + start);
            return isFloat ? Double.valueOf(token) : Long.valueOf(token);
        }

        private void skipWs() {
            while (pos < s.length()) {
                char c = s.charAt(pos);
                if (c == ' ' || c == '\t' || c == '\r' || c == '\n' || c == '\uFEFF') {
                    pos++;
                } else {
                    break;
                }
            }
        }

        private boolean peek(char c) {
            return pos < s.length() && s.charAt(pos) == c;
        }

        private void expect(char c) {
            if (!peek(c)) {
                throw new IllegalArgumentException("Expected '" + c + "' at offset " + pos);
            }
            pos++;
        }

        @SuppressWarnings("unchecked")
        static String stringify(Object obj, int indent) {
            if (obj == null) return "null";
            if (obj instanceof Boolean || obj instanceof Number) return obj.toString();
            if (obj instanceof String str) return quote(str);
            String pad = "  ".repeat(indent);
            String childPad = "  ".repeat(indent + 1);
            if (obj instanceof Map<?, ?> map) {
                if (map.isEmpty()) return "{}";
                StringBuilder sb = new StringBuilder("{\n");
                int idx = 0;
                for (Map.Entry<?, ?> e : map.entrySet()) {
                    if (idx++ > 0) sb.append(",\n");
                    sb.append(childPad).append(quote(String.valueOf(e.getKey()))).append(": ")
                        .append(stringify(e.getValue(), indent + 1));
                }
                sb.append('\n').append(pad).append('}');
                return sb.toString();
            }
            if (obj instanceof List<?> list) {
                if (list.isEmpty()) return "[]";
                StringBuilder sb = new StringBuilder("[\n");
                for (int i = 0; i < list.size(); i++) {
                    if (i > 0) sb.append(",\n");
                    sb.append(childPad).append(stringify(list.get(i), indent + 1));
                }
                sb.append('\n').append(pad).append(']');
                return sb.toString();
            }
            throw new IllegalArgumentException("Unsupported JSON value: " + obj.getClass());
        }

        private static String quote(String s) {
            StringBuilder sb = new StringBuilder("\"");
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                switch (c) {
                    case '"' -> sb.append("\\\"");
                    case '\\' -> sb.append("\\\\");
                    case '\b' -> sb.append("\\b");
                    case '\f' -> sb.append("\\f");
                    case '\n' -> sb.append("\\n");
                    case '\r' -> sb.append("\\r");
                    case '\t' -> sb.append("\\t");
                    default -> {
                        if (c < 0x20) {
                            sb.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
                        } else {
                            sb.append(c);
                        }
                    }
                }
            }
            sb.append('"');
            return sb.toString();
        }
    }
}
