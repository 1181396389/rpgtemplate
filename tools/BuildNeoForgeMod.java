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
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Zero-dependency Java 21 direct build & verification pipeline for rpgtemplate (Minecraft 1.21.1 NeoForge).
 *
 * <p>Run directly with JDK 21: {@code java tools/BuildNeoForgeMod.java} (or {@code ./build.sh} / {@code build.bat}).</p>
 */
public final class BuildNeoForgeMod {
    private static final String NEOFORGE_MAVEN = "https://maven.neoforged.net/releases";

    public static void main(String[] args) throws Exception {
        long t0 = System.currentTimeMillis();
        Path repoRoot = findRepoRoot();
        Properties props = loadProperties(repoRoot.resolve("build.properties"));

        String modId = props.getProperty("mod_id", "rpgtemplate");
        String modVersion = envOrProp("MOD_VERSION", props.getProperty("mod_version", "0.4.14"));
        String mcVersion = props.getProperty("minecraft_version", "1.21.1");
        String nfVersion = props.getProperty("neoforge_version", "21.1.266");
        Path srcDir = repoRoot.resolve(props.getProperty("src_dir", "src/main/java"));
        Path resDir = repoRoot.resolve(props.getProperty("resources_dir", "src/main/resources"));
        Path outDir = repoRoot.resolve(props.getProperty("output_dir", "dist"));
        Path cacheDir = resolvePath(repoRoot, envOrProp("RPGTEMPLATE_NF21_CACHE",
            props.getProperty("cache_dir", ".build-cache")));
        boolean skipVerify = false;
        boolean cleanBuild = false;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--version" -> modVersion = requireArg(args, ++i, "--version");
                case "--output-dir" -> outDir = resolvePath(repoRoot, requireArg(args, ++i, "--output-dir"));
                case "--cache-dir" -> cacheDir = resolvePath(repoRoot, requireArg(args, ++i, "--cache-dir"));
                case "--skip-verify" -> skipVerify = true;
                case "--clean" -> cleanBuild = true;
                case "-h", "--help" -> {
                    System.out.println("Usage: ./build.sh [--version X.Y.Z] [--cache-dir DIR] [--output-dir DIR] [--clean] [--skip-verify]");
                    return;
                }
                default -> throw new IllegalArgumentException("Unknown argument: " + args[i]);
            }
        }

        checkJavaVersion();
        System.out.println("=== rpgtemplate direct NeoForge " + mcVersion + " build ===");
        System.out.println("  Version      : " + modVersion);
        System.out.println("  NeoForge SDK : " + mcVersion + "-" + nfVersion);
        System.out.println("  Sources      : " + srcDir);
        System.out.println("  Cache dir    : " + cacheDir);

        Path sdkDir = cacheDir.resolve("sdk-" + nfVersion);
        provisionNeoForgeSdk(sdkDir, mcVersion, nfVersion);

        Path classesDir = cacheDir.resolve("build-classes-" + modVersion);
        if (cleanBuild && Files.exists(classesDir)) {
            deleteRecursively(classesDir);
        }
        Files.createDirectories(classesDir);
        compileSources(sdkDir, srcDir, classesDir, nfVersion);

        Files.createDirectories(outDir);
        Path outJar = outDir.resolve(modId + "-" + modVersion + "-neoforge-" + mcVersion + ".jar");
        assembleJar(classesDir, resDir, outJar, modId, modVersion);

        if (!skipVerify) {
            verifyJar(outJar, modVersion);
        }

        long elapsedMs = System.currentTimeMillis() - t0;
        System.out.printf("=== BUILD SUCCESSFUL in %.2fs -> %s (%,d bytes) ===%n",
            elapsedMs / 1000.0, outJar, Files.size(outJar));
    }

    private static void checkJavaVersion() {
        int major = Runtime.version().feature();
        if (major < 21) {
            throw new IllegalStateException("JDK 21+ is required to build NeoForge 1.21.1 (current: Java " + major + ")");
        }
    }

    private static Path findRepoRoot() {
        Path cwd = Paths.get("").toAbsolutePath();
        if (Files.isRegularFile(cwd.resolve("build.properties"))) return cwd;
        throw new IllegalStateException("Run BuildNeoForgeMod from repository root containing build.properties");
    }

    private static Properties loadProperties(Path p) throws IOException {
        Properties props = new Properties();
        if (Files.isRegularFile(p)) {
            try (InputStream in = Files.newInputStream(p)) {
                props.load(in);
            }
        }
        return props;
    }

    private static String envOrProp(String env, String fallback) {
        String v = System.getenv(env);
        return (v != null && !v.isBlank()) ? v.trim() : fallback;
    }

    private static Path resolvePath(Path root, String raw) {
        Path p = Paths.get(raw);
        return p.isAbsolute() ? p : root.resolve(p);
    }

    private static String requireArg(String[] args, int idx, String flag) {
        if (idx >= args.length) throw new IllegalArgumentException("Missing value for " + flag);
        return args[idx];
    }

    // =========================================================================
    // Step 1: Provision NeoForge 1.21.1 + Minecraft 1.21.1 Mojmap SDK
    // =========================================================================

    @SuppressWarnings("unchecked")
    private static void provisionNeoForgeSdk(Path sdkDir, String mcVersion, String nfVersion) throws Exception {
        Path nfClientJar = sdkDir.resolve("libraries/net/neoforged/neoforge/" + nfVersion + "/neoforge-" + nfVersion + "-client.jar");
        Path nfUnivJar = sdkDir.resolve("libraries/net/neoforged/neoforge/" + nfVersion + "/neoforge-" + nfVersion + "-universal.jar");
        Path mcLibsMarker = sdkDir.resolve(".vanilla-libs-ready");

        if (Files.isRegularFile(nfClientJar) && Files.isRegularFile(nfUnivJar) && Files.isRegularFile(mcLibsMarker)) {
            System.out.println("[1/4] Using cached NeoForge " + mcVersion + "-" + nfVersion + " Mojmap SDK.");
            return;
        }

        System.out.println("[1/4] Provisioning NeoForge " + mcVersion + "-" + nfVersion + " Mojmap SDK...");
        Files.createDirectories(sdkDir);
        HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(30))
            .build();

        if (!Files.isRegularFile(nfClientJar) || !Files.isRegularFile(nfUnivJar)) {
            Path launcherProfiles = sdkDir.resolve("launcher_profiles.json");
            if (!Files.exists(launcherProfiles)) {
                Files.writeString(launcherProfiles, "{\"profiles\":{}}\n", StandardCharsets.UTF_8);
            }
            Path installerJar = sdkDir.getParent().resolve("neoforge-" + nfVersion + "-installer.jar");
            if (!Files.isRegularFile(installerJar)) {
                String url = NEOFORGE_MAVEN + "/net/neoforged/neoforge/" + nfVersion + "/neoforge-" + nfVersion + "-installer.jar";
                System.out.println("  Downloading " + url);
                downloadFile(http, url, installerJar);
            }
            String javaExe = Paths.get(System.getProperty("java.home"), "bin", "java").toString();
            ProcessBuilder pb = new ProcessBuilder(
                javaExe, "-Djava.awt.headless=true", "-jar", installerJar.toString(),
                "--installClient", sdkDir.toString()
            );
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            String output = new String(proc.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int code = proc.waitFor();
            if (code != 0 || !Files.isRegularFile(nfClientJar)) {
                System.err.println(output);
                throw new IllegalStateException("NeoForge installer failed (exit " + code + ")");
            }
            System.out.println("  NeoForge patched Mojmap client ready.");
        }

        if (!Files.isRegularFile(mcLibsMarker)) {
            Path vJson = sdkDir.resolve("versions/" + mcVersion + "/" + mcVersion + ".json");
            if (!Files.isRegularFile(vJson)) {
                throw new IllegalStateException("Missing Minecraft version metadata: " + vJson);
            }
            Map<String, Object> meta = (Map<String, Object>) JsonCodec.parse(Files.readString(vJson, StandardCharsets.UTF_8));
            List<Object> libs = (List<Object>) meta.getOrDefault("libraries", List.of());
            List<String[]> downloads = new ArrayList<>();
            Path libsDir = sdkDir.resolve("libraries");

            for (Object item : libs) {
                if (!(item instanceof Map<?, ?> lib)) continue;
                if (!rulesAllowLinux((List<Object>) lib.get("rules"))) continue;
                Object dlObj = lib.get("downloads");
                if (!(dlObj instanceof Map<?, ?> dl)) continue;
                Object artObj = dl.get("artifact");
                if (!(artObj instanceof Map<?, ?> art)) continue;
                String relPath = (String) art.get("path");
                String url = (String) art.get("url");
                if (relPath != null && url != null && relPath.endsWith(".jar")) {
                    Path dst = libsDir.resolve(relPath);
                    if (!Files.isRegularFile(dst)) {
                        downloads.add(new String[]{url, dst.toString()});
                    }
                }
            }

            if (!downloads.isEmpty()) {
                System.out.println("  Downloading " + downloads.size() + " vanilla Minecraft " + mcVersion + " libraries...");
                ExecutorService pool = Executors.newFixedThreadPool(8);
                List<Future<?>> futures = new ArrayList<>();
                for (String[] pair : downloads) {
                    futures.add(pool.submit(() -> {
                        try {
                            downloadFile(http, pair[0], Paths.get(pair[1]));
                        } catch (Exception e) {
                            throw new RuntimeException("Failed downloading " + pair[0], e);
                        }
                    }));
                }
                for (Future<?> f : futures) f.get();
                pool.shutdown();
            }
            Files.writeString(mcLibsMarker, "ok\n", StandardCharsets.UTF_8);
            System.out.println("  Minecraft " + mcVersion + " libraries ready.");
        }
    }

    @SuppressWarnings("unchecked")
    private static boolean rulesAllowLinux(List<Object> rules) {
        if (rules == null || rules.isEmpty()) return true;
        boolean allowed = false;
        for (Object rObj : rules) {
            if (!(rObj instanceof Map<?, ?> r)) continue;
            String action = String.valueOf(r.get("action"));
            Object osObj = r.get("os");
            boolean match = true;
            if (osObj instanceof Map<?, ?> osMap) {
                Object name = osMap.get("name");
                if (name != null && !"linux".equals(String.valueOf(name))) {
                    match = false;
                }
            }
            if (match) allowed = "allow".equals(action);
        }
        return allowed;
    }

    private static void downloadFile(HttpClient http, String url, Path dst) throws Exception {
        Files.createDirectories(dst.getParent());
        Path tmp = dst.resolveSibling(dst.getFileName() + ".part");
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
            .header("User-Agent", "rpgtemplate-build/1.0")
            .timeout(Duration.ofSeconds(90))
            .GET().build();
        HttpResponse<InputStream> resp = http.send(req, HttpResponse.BodyHandlers.ofInputStream());
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new IOException("HTTP " + resp.statusCode() + " for " + url);
        }
        try (InputStream in = resp.body()) {
            Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
        }
        Files.move(tmp, dst, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    // =========================================================================
    // Step 2: Compile all src/main/java sources against NeoForge 1.21.1 Mojmap
    // =========================================================================

    private static void compileSources(Path sdkDir, Path srcDir, Path classesDir, String nfVersion) throws Exception {
        List<Path> javaFiles;
        try (Stream<Path> walk = Files.walk(srcDir)) {
            javaFiles = walk.filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".java"))
                .sorted().toList();
        }
        System.out.println("[2/4] Compiling " + javaFiles.size() + " Java source files (--release 21)...");

        List<Path> cp = new ArrayList<>();
        // Highest priority: NeoForge patched MC 1.21.1 client JAR & NeoForge universal JAR
        Path nfClient = sdkDir.resolve("libraries/net/neoforged/neoforge/" + nfVersion + "/neoforge-" + nfVersion + "-client.jar");
        Path nfUniv = sdkDir.resolve("libraries/net/neoforged/neoforge/" + nfVersion + "/neoforge-" + nfVersion + "-universal.jar");
        cp.add(nfClient);
        cp.add(nfUniv);

        Set<Path> seen = new LinkedHashSet<>(cp);
        Path libsDir = sdkDir.resolve("libraries");
        try (Stream<Path> walk = Files.walk(libsDir)) {
            List<Path> allJars = walk.filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".jar"))
                .sorted().toList();
            // Add client-1.21.1-*-srg.jar next (Mojmap unpatched fallback), skip installer fatjars
            for (Path j : allJars) {
                String n = j.getFileName().toString();
                if (n.contains("client-1.21.1") && n.endsWith("-srg.jar") && seen.add(j)) {
                    cp.add(j);
                }
            }
            for (Path j : allJars) {
                String s = j.toString().replace('\\', '/');
                if (s.contains("/installertools/") || s.contains("/AutoRenamingTool/")
                    || s.contains("-slim.jar") || s.contains("-extra.jar")
                    || s.contains("natives-")) {
                    continue;
                }
                if (seen.add(j)) {
                    cp.add(j);
                }
            }
        }

        Path argFile = classesDir.getParent().resolve("javac-args.txt");
        StringBuilder sb = new StringBuilder();
        sb.append("-encoding\nUTF-8\n--release\n21\n-proc:none\n-Xmaxerrs\n200\n-d\n")
            .append(quoteArg(classesDir.toString())).append('\n')
            .append("-cp\n").append(quoteArg(joinCp(cp))).append('\n');
        for (Path jf : javaFiles) {
            sb.append(quoteArg(jf.toString())).append('\n');
        }
        Files.writeString(argFile, sb.toString(), StandardCharsets.UTF_8);

        String javacExe = Paths.get(System.getProperty("java.home"), "bin", "javac").toString();
        ProcessBuilder pb = new ProcessBuilder(javacExe, "@" + argFile);
        pb.inheritIO();
        int code = pb.start().waitFor();
        if (code != 0) {
            throw new IllegalStateException("javac failed with exit code " + code);
        }
        long compiledCount;
        try (Stream<Path> walk = Files.walk(classesDir)) {
            compiledCount = walk.filter(p -> p.toString().endsWith(".class")).count();
        }
        System.out.println("  Compiled " + compiledCount + " class files.");
    }

    private static String joinCp(List<Path> jars) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < jars.size(); i++) {
            if (i > 0) sb.append(File.pathSeparatorChar);
            sb.append(jars.get(i).toString());
        }
        return sb.toString();
    }

    private static String quoteArg(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    // =========================================================================
    // Step 3: Assemble pure-source NeoForge 1.21.1 mod JAR
    // =========================================================================

    private static void assembleJar(Path classesDir, Path resDir, Path outJar,
                                    String modId, String modVersion) throws IOException {
        System.out.println("[3/4] Assembling NeoForge 1.21.1 mod JAR -> " + outJar.getFileName() + "...");
        Path tmpJar = outJar.resolveSibling(outJar.getFileName() + ".tmp");
        Set<String> written = new HashSet<>();

        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(tmpJar))) {
            zos.setLevel(9);

            // 1. Clean MANIFEST.MF
            String manifest = "Manifest-Version: 1.0\n"
                + "Specification-Title: " + modId + "\n"
                + "Specification-Version: 1\n"
                + "Implementation-Title: " + modId + "\n"
                + "Implementation-Version: " + modVersion + "\n"
                + "FMLModType: MOD\n";
            putBytes(zos, written, "META-INF/MANIFEST.MF", manifest.getBytes(StandardCharsets.UTF_8));

            // 2. Resources (update version in neoforge.mods.toml)
            List<Path> resFiles;
            try (Stream<Path> walk = Files.walk(resDir)) {
                resFiles = walk.filter(Files::isRegularFile).sorted().toList();
            }
            for (Path rf : resFiles) {
                String entry = toZipPath(resDir.relativize(rf));
                byte[] bytes = Files.readAllBytes(rf);
                if ("META-INF/neoforge.mods.toml".equals(entry)) {
                    String toml = new String(bytes, StandardCharsets.UTF_8)
                        .replaceFirst("(?m)^version\\s*=\\s*\"[^\"]*\"", "version=\"" + modVersion + "\"");
                    bytes = toml.getBytes(StandardCharsets.UTF_8);
                }
                putBytes(zos, written, entry, bytes);
            }

            // 3. Compiled classes
            List<Path> classFiles;
            try (Stream<Path> walk = Files.walk(classesDir)) {
                classFiles = walk.filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".class"))
                    .sorted().toList();
            }
            for (Path cf : classFiles) {
                String entry = toZipPath(classesDir.relativize(cf));
                putBytes(zos, written, entry, Files.readAllBytes(cf));
            }
        }

        Files.move(tmpJar, outJar, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        System.out.println("  Packed " + written.size() + " entries.");
    }

    private static void putBytes(ZipOutputStream zos, Set<String> written, String entry, byte[] bytes) throws IOException {
        if (!written.add(entry)) return;
        ZipEntry ze = new ZipEntry(entry);
        ze.setTime(0L);
        zos.putNextEntry(ze);
        zos.write(bytes);
        zos.closeEntry();
    }

    // =========================================================================
    // Step 4: Full L1 Bytecode, Mixin, Sound, Model, Lang & Template Verification
    // =========================================================================

    @SuppressWarnings("unchecked")
    private static void verifyJar(Path jarPath, String expectedVersion) throws IOException {
        System.out.println("[4/4] Verifying assembled NeoForge 1.21.1 JAR...");
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

            // 1. Check META-INF/neoforge.mods.toml & declared [[mixins]]
            ZipEntry tomlEntry = zf.getEntry("META-INF/neoforge.mods.toml");
            if (tomlEntry == null) {
                failures.add("Missing META-INF/neoforge.mods.toml");
            } else {
                String toml = new String(readAll(zf, tomlEntry), StandardCharsets.UTF_8);
                if (!toml.contains("version=\"" + expectedVersion + "\"")) {
                    failures.add("META-INF/neoforge.mods.toml missing expected version " + expectedVersion);
                }
                for (String line : toml.split("\\r?\\n")) {
                    String t = line.trim();
                    if (t.startsWith("config=")) {
                        String cfg = t.substring("config=".length()).replace("\"", "").trim();
                        if (!allEntries.contains(cfg)) {
                            failures.add("neoforge.mods.toml mixin config missing: " + cfg);
                        } else {
                            Map<String, Object> mj = (Map<String, Object>) JsonCodec.parse(
                                new String(readAll(zf, zf.getEntry(cfg)), StandardCharsets.UTF_8));
                            String pkg = String.valueOf(mj.getOrDefault("package", "")).replace('.', '/');
                            for (String key : List.of("mixins", "client", "server")) {
                                if (mj.get(key) instanceof List<?> list) {
                                    for (Object cls : list) {
                                        String rel = pkg + "/" + String.valueOf(cls).replace('.', '/');
                                        if (!classes.containsKey(rel)) {
                                            failures.add("Mixin class missing from JAR: " + rel);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 2. Verify sounds.json + OGG closure
            ZipEntry soundsEntry = zf.getEntry("assets/rpgtemplate/sounds.json");
            int checkedOggs = 0;
            if (soundsEntry == null) {
                failures.add("Missing assets/rpgtemplate/sounds.json");
            } else {
                Map<String, Object> sounds = (Map<String, Object>) JsonCodec.parse(
                    new String(readAll(zf, soundsEntry), StandardCharsets.UTF_8));
                for (Map.Entry<String, Object> se : sounds.entrySet()) {
                    if (se.getValue() instanceof Map<?, ?> def && def.get("sounds") instanceof List<?> slist) {
                        for (Object item : slist) {
                            String sname = (item instanceof String s) ? s
                                : (item instanceof Map<?, ?> sm && sm.get("name") != null ? String.valueOf(sm.get("name")) : "");
                            if (sname.startsWith("rpgtemplate:")) {
                                String oggPath = "assets/rpgtemplate/sounds/" + sname.substring("rpgtemplate:".length()) + ".ogg";
                                checkedOggs++;
                                if (!allEntries.contains(oggPath)) {
                                    failures.add("sounds.json missing OGG: " + oggPath);
                                }
                            }
                        }
                    }
                }
            }

            // 3. Verify model -> texture closure
            int checkedModelTex = 0;
            for (String name : allEntries) {
                if (name.startsWith("assets/rpgtemplate/models/") && name.endsWith(".json")) {
                    Object parsed = JsonCodec.parse(new String(readAll(zf, zf.getEntry(name)), StandardCharsets.UTF_8));
                    if (parsed instanceof Map<?, ?> mj && mj.get("textures") instanceof Map<?, ?> tmap) {
                        for (Object tv : tmap.values()) {
                            if (tv instanceof String ts && ts.startsWith("rpgtemplate:")) {
                                String pngPath = "assets/rpgtemplate/textures/" + ts.substring("rpgtemplate:".length()) + ".png";
                                checkedModelTex++;
                                if (!allEntries.contains(pngPath)) {
                                    failures.add("Model " + name + " missing texture: " + pngPath);
                                }
                            }
                        }
                    }
                }
            }

            // 4. Lang parity
            ZipEntry zhEntry = zf.getEntry("assets/rpgtemplate/lang/zh_cn.json");
            ZipEntry enEntry = zf.getEntry("assets/rpgtemplate/lang/en_us.json");
            if (zhEntry == null || enEntry == null) {
                failures.add("Missing lang files");
            } else {
                Map<String, Object> zh = (Map<String, Object>) JsonCodec.parse(new String(readAll(zf, zhEntry), StandardCharsets.UTF_8));
                Map<String, Object> enLang = (Map<String, Object>) JsonCodec.parse(new String(readAll(zf, enEntry), StandardCharsets.UTF_8));
                if (!zh.keySet().equals(enLang.keySet())) {
                    failures.add("zh_cn (" + zh.size() + ") and en_us (" + enLang.size() + ") lang keys differ");
                }
            }

            // 5. Templates JSON parse
            int templateCount = 0;
            for (String name : allEntries) {
                if (name.startsWith("data/rpgtemplate/rpg_templates/") && name.endsWith(".json")) {
                    JsonCodec.parse(new String(readAll(zf, zf.getEntry(name)), StandardCharsets.UTF_8));
                    templateCount++;
                }
            }
            if (templateCount < 11) {
                failures.add("Expected >= 11 rpg_templates, found " + templateCount);
            }

            // 6. Bytecode internal link check + zero net/minecraftforge/ leak check
            Set<String> brokenRefs = new TreeSet<>();
            Set<String> forgeLeaks = new TreeSet<>();
            for (Map.Entry<String, byte[]> e : classes.entrySet()) {
                for (String ref : readConstantPoolClasses(e.getValue())) {
                    String norm = normalizeInternalClass(ref);
                    if (norm == null) continue;
                    if (norm.startsWith("com/rpgtemplate/") && !classes.containsKey(norm)) {
                        brokenRefs.add(e.getKey() + " -> " + norm);
                    }
                    if (norm.startsWith("net/minecraftforge/")) {
                        forgeLeaks.add(e.getKey() + " -> " + norm);
                    }
                }
            }
            for (String br : brokenRefs) failures.add("Broken internal class reference: " + br);
            for (String fl : forgeLeaks) failures.add("Leaked Forge 1.20.1 reference in NeoForge 1.21.1 JAR: " + fl);

            if (!failures.isEmpty()) {
                StringBuilder sb = new StringBuilder("NeoForge JAR verification FAILED (" + failures.size() + " issues):\n");
                for (String f : failures) sb.append("  - ").append(f).append('\n');
                throw new IllegalStateException(sb.toString());
            }

            System.out.println("  Verified OK: " + classes.size() + " classes (0 broken links, 0 Forge leaks), "
                + templateCount + " templates, " + checkedOggs + " sound OGG refs, "
                + checkedModelTex + " model texture refs, clean neoforge.mods.toml/Mixins/lang.");
        }
    }

    private static String normalizeInternalClass(String raw) {
        String s = raw;
        while (s.startsWith("[")) s = s.substring(1);
        if (s.startsWith("L") && s.endsWith(";")) s = s.substring(1, s.length() - 1);
        return s.contains("/") ? s : null;
    }

    private static Set<String> readConstantPoolClasses(byte[] classBytes) throws IOException {
        Set<String> result = new HashSet<>();
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(classBytes))) {
            if (in.readInt() != 0xCAFEBABE) return result;
            in.readUnsignedShort();
            in.readUnsignedShort();
            int cpCount = in.readUnsignedShort();
            Map<Integer, String> utf8 = new HashMap<>();
            List<Integer> classNameIndices = new ArrayList<>();
            for (int i = 1; i < cpCount; i++) {
                int tag = in.readUnsignedByte();
                switch (tag) {
                    case 1 -> utf8.put(i, in.readUTF());
                    case 3, 4 -> in.skipBytes(4);
                    case 5, 6 -> { in.skipBytes(8); i++; }
                    case 7 -> classNameIndices.add(in.readUnsignedShort());
                    case 8, 16, 19, 20 -> in.skipBytes(2);
                    case 9, 10, 11, 12, 17, 18 -> in.skipBytes(4);
                    case 15 -> in.skipBytes(3);
                    default -> { return result; }
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
                try { Files.deleteIfExists(p); } catch (IOException e) { throw new RuntimeException(e); }
            });
        }
    }

    private static final class JsonCodec {
        private final String s;
        private int pos;
        private JsonCodec(String s) { this.s = s; }

        static Object parse(String text) {
            JsonCodec p = new JsonCodec(text);
            p.skipWs();
            Object v = p.readValue();
            p.skipWs();
            if (p.pos != p.s.length()) throw new IllegalArgumentException("Trailing JSON at " + p.pos);
            return v;
        }
        private Object readValue() {
            skipWs();
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
            pos++;
            Map<String, Object> map = new LinkedHashMap<>();
            skipWs();
            if (peek('}')) { pos++; return map; }
            while (true) {
                skipWs();
                String key = readString();
                skipWs();
                if (!peek(':')) throw new IllegalArgumentException("Expected ':' at " + pos);
                pos++;
                map.put(key, readValue());
                skipWs();
                if (peek(',')) pos++;
                else if (peek('}')) { pos++; return map; }
                else throw new IllegalArgumentException("Expected ',' or '}' at " + pos);
            }
        }
        private List<Object> readArray() {
            pos++;
            List<Object> list = new ArrayList<>();
            skipWs();
            if (peek(']')) { pos++; return list; }
            while (true) {
                list.add(readValue());
                skipWs();
                if (peek(',')) pos++;
                else if (peek(']')) { pos++; return list; }
                else throw new IllegalArgumentException("Expected ',' or ']' at " + pos);
            }
        }
        private String readString() {
            pos++;
            StringBuilder sb = new StringBuilder();
            while (pos < s.length()) {
                char c = s.charAt(pos++);
                if (c == '"') return sb.toString();
                if (c == '\\') {
                    char e = s.charAt(pos++);
                    switch (e) {
                        case '"', '\\', '/' -> sb.append(e);
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        case 'n' -> sb.append('\n');
                        case 'r' -> sb.append('\r');
                        case 't' -> sb.append('\t');
                        case 'u' -> { sb.append((char) Integer.parseInt(s.substring(pos, pos + 4), 16)); pos += 4; }
                        default -> throw new IllegalArgumentException("Bad escape \\" + e);
                    }
                } else sb.append(c);
            }
            throw new IllegalArgumentException("Unterminated string");
        }
        private Object expectLiteral(String lit, Object val) {
            if (!s.startsWith(lit, pos)) throw new IllegalArgumentException("Expected " + lit);
            pos += lit.length();
            return val;
        }
        private Number readNumber() {
            int start = pos;
            if (peek('-')) pos++;
            while (pos < s.length() && Character.isDigit(s.charAt(pos))) pos++;
            boolean fl = false;
            if (peek('.')) { fl = true; pos++; while (pos < s.length() && Character.isDigit(s.charAt(pos))) pos++; }
            if (peek('e') || peek('E')) { fl = true; pos++; if (peek('+') || peek('-')) pos++; while (pos < s.length() && Character.isDigit(s.charAt(pos))) pos++; }
            String tok = s.substring(start, pos);
            return fl ? Double.valueOf(tok) : Long.valueOf(tok);
        }
        private void skipWs() {
            while (pos < s.length() && " \t\r\n\uFEFF".indexOf(s.charAt(pos)) >= 0) pos++;
        }
        private boolean peek(char c) { return pos < s.length() && s.charAt(pos) == c; }
    }
}
