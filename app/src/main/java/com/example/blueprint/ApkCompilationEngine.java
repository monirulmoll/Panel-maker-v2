package com.example.blueprint;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.apksig.ApkSigner;
import com.example.data.CanvasComponentEntity;
import com.example.data.StudioProjectEntity;
import com.example.ui.KotlinProjectCodeEngine;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Complete Dynamic Android Project Blueprint Generator, APK Compiler/Packager & V1/V2/V3 Signer Engine.
 *
 * 1. Dynamically translates the user's Studio Error canvas widgets, Byte Offsets, ON/OFF hex payloads,
 *    and Target File Paths into complete, non-stub Java source files (MainActivity.java,
 *    ProjectLauncherActivity.java, EmptyCanvasWorkspaceView.java, BottomPropertyInspectorDock.java,
 *    FloatingModMenuService.java, BinaryOffsetPatcher.java) and valid XML layouts
 *    (activity_main.xml, inspector_dock.xml, floating_view.xml) + AndroidManifest.xml + build.gradle.
 * 2. Merges the generated project blueprint and assets/overlay_config.json into the compiled
 *    Android runtime package (preserving compiled classes.dex, binary AndroidManifest.xml, and
 *    uncompressed 4-byte-aligned resources.arsc required by Android 11-15 PackageParser).
 * 3. Cryptographically signs the output APK using Google's official ApkSigner (V1 + V2 + V3 schemes)
 *    with the debug keystore keypair so the resulting APK installs cleanly without parsing errors.
 */
public final class ApkCompilationEngine {

    private static final String STUDIO_BASE_PACKAGE = "com.aistudio.floatconfig.vqxkpl";
    private static final String STUDIO_BASE_LABEL = "Studio Error";

    public static final class CompilationResult {
        public final File signedApkFile;
        public final File blueprintSourceZipFile;
        public final String rawJavaPreview;
        public final Map<String, String> generatedTextFiles;
        public final long apkSizeBytes;
        public final String compiledPackageName;
        public final String compiledAppName;

        public CompilationResult(
                @NonNull File signedApkFile,
                @NonNull File blueprintSourceZipFile,
                @NonNull String rawJavaPreview,
                @NonNull Map<String, String> generatedTextFiles,
                long apkSizeBytes
        ) {
            this(signedApkFile, blueprintSourceZipFile, rawJavaPreview, generatedTextFiles, apkSizeBytes,
                    "com.aistudio.floatconfig.app001", "Compiled App");
        }

        public CompilationResult(
                @NonNull File signedApkFile,
                @NonNull File blueprintSourceZipFile,
                @NonNull String rawJavaPreview,
                @NonNull Map<String, String> generatedTextFiles,
                long apkSizeBytes,
                @NonNull String compiledPackageName,
                @NonNull String compiledAppName
        ) {
            this.signedApkFile = signedApkFile;
            this.blueprintSourceZipFile = blueprintSourceZipFile;
            this.rawJavaPreview = rawJavaPreview;
            this.generatedTextFiles = generatedTextFiles;
            this.apkSizeBytes = apkSizeBytes;
            this.compiledPackageName = compiledPackageName;
            this.compiledAppName = compiledAppName;
        }
    }

    private ApkCompilationEngine() {
    }

    /**
     * Resolves the valid Android applicationId / Package ID for the user's created project inside Studio Error.
     * Prioritizes the user's configured packageName (e.g., com.mycompany.myapp) so that the compiled APK
     * has the exact Package ID configured in Project Settings / Pencil Icon dialog.
     */
    @NonNull
    public static String getCompiledAppPackageName(@NonNull StudioProjectEntity project) {
        String customPkg = project.getPackageName() != null ? project.getPackageName().trim().toLowerCase(Locale.US) : "";
        if (!customPkg.isEmpty()) {
            String[] rawParts = customPkg.split("\\.+");
            StringBuilder cleanPkg = new StringBuilder();
            int validSegments = 0;
            for (String part : rawParts) {
                String cleaned = part.replaceAll("[^a-z0-9_]", "");
                if (cleaned.isEmpty()) continue;
                if (!Character.isLetter(cleaned.charAt(0))) {
                    cleaned = "app_" + cleaned;
                }
                if (validSegments > 0) {
                    cleanPkg.append('.');
                }
                cleanPkg.append(cleaned);
                validSegments++;
            }
            if (validSegments == 1) {
                cleanPkg.insert(0, "com.app.");
                validSegments = 3;
            }
            String candidate = cleanPkg.toString();
            if (validSegments >= 2 && !STUDIO_BASE_PACKAGE.equals(candidate)) {
                return candidate;
            }
        }

        String rawName = project.getName() != null ? project.getName().trim().toLowerCase(Locale.US) : "";
        String lettersDigits = rawName.replaceAll("[^a-z0-9]", "");
        int hash = Math.abs((rawName + "#" + project.getId()).hashCode());
        String hexSuffix = String.format(Locale.US, "%04x", hash & 0xFFFF);
        String prefix2;
        if (lettersDigits.length() >= 2 && Character.isLetter(lettersDigits.charAt(0))) {
            prefix2 = lettersDigits.substring(0, 2);
        } else {
            prefix2 = "ap";
        }
        String sixCharCode = (prefix2 + hexSuffix).substring(0, 6);
        if ("vqxkpl".equals(sixCharCode)) {
            sixCharCode = "app001";
        }
        return "com.aistudio.floatconfig." + sixCharCode;
    }

    /**
     * Generates the full Map of Android project blueprint files (Manifest, Kotlin & Java sources,
     * XML layouts, Gradle scripts, and overlay_config.json) from the user's configured project and canvas widgets.
     */
    @NonNull
    public static Map<String, String> generateProjectBlueprintFiles(
            @NonNull StudioProjectEntity project,
            @NonNull List<CanvasComponentEntity> components
    ) {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("AndroidManifest.xml", generateManifestXml(project));
        files.put("build.gradle", generateAppBuildGradle(project));
        files.put("settings.gradle", generateSettingsGradle(project));
        // Include the complete multi-file Kotlin structure matching Studio Error APK
        files.putAll(KotlinProjectCodeEngine.generateKotlinFilesForProject(project, components));
        files.put("src/main/java/com/floating/modmenu/MainActivity.java", generateMainActivityJava(project, components));
        files.put("src/main/java/com/floating/modmenu/ProjectLauncherActivity.java", generateProjectLauncherJava(project));
        files.put("src/main/java/com/floating/modmenu/EmptyCanvasWorkspaceView.java", generateEmptyCanvasWorkspaceJava(project, components));
        files.put("src/main/java/com/floating/modmenu/BottomPropertyInspectorDock.java", generateBottomInspectorJava(components));
        files.put("src/main/java/com/floating/modmenu/FloatingModMenuService.java", generateFloatingServiceJava(project, components));
        files.put("src/main/java/com/floating/modmenu/BinaryOffsetPatcher.java", generateBinaryOffsetPatcherJava(project, components));
        files.put("src/main/res/layout/activity_main.xml", generateActivityMainXml(project));
        files.put("src/main/res/layout/inspector_dock.xml", generateInspectorDockXml());
        files.put("src/main/res/layout/floating_view.xml", generateFloatingViewXml(project, components));
        files.put("assets/overlay_config.json", generateOverlayConfigJson(project, components));
        return files;
    }

    @NonNull
    public static CompilationResult compileAndSignProjectApk(
            @NonNull Context context,
            @NonNull StudioProjectEntity project,
            @NonNull List<CanvasComponentEntity> components,
            @NonNull File outputApkFile
    ) throws Exception {
        return compileAndSignProjectApk(context, project, components, outputApkFile, null);
    }

    /**
     * Compiles, packages, and cryptographically signs (V1 + V2 + V3) a valid, installable Android APK
     * containing the user's configured floating window and complete generated Kotlin/Java project blueprint.
     */
    @NonNull
    public static CompilationResult compileAndSignProjectApk(
            @NonNull Context context,
            @NonNull StudioProjectEntity project,
            @NonNull List<CanvasComponentEntity> components,
            @NonNull File outputApkFile,
            @Nullable Map<String, String> customEditedFiles
    ) throws Exception {
        Map<String, String> blueprintFiles = generateProjectBlueprintFiles(project, components);
        if (customEditedFiles != null && !customEditedFiles.isEmpty()) {
            blueprintFiles.putAll(customEditedFiles);
        }
        String serviceJavaPreview = blueprintFiles.get("src/main/java/com/example/ui/CanvasWorkspaceComponents.kt");
        if (serviceJavaPreview == null || serviceJavaPreview.isEmpty()) {
            serviceJavaPreview = blueprintFiles.get("src/main/java/com/floating/modmenu/FloatingModMenuService.java");
        }
        if (serviceJavaPreview == null) {
            serviceJavaPreview = "";
        }

        File workDir = new File(context.getCacheDir(), "apk_compiler_staging");
        if (!workDir.exists()) {
            workDir.mkdirs();
        }

        // 1. Write the complete standalone Android project blueprint source archive (.zip)
        File blueprintZipFile = new File(
                outputApkFile.getParentFile(),
                outputApkFile.getName().replace(".apk", "_project_blueprint.zip")
        );
        writeBlueprintSourceZip(blueprintZipFile, blueprintFiles, components);

        // 2. Locate the real compiled runtime APK (base.apk) on the Android device or build outputs
        String sourceApkPath = context.getApplicationInfo().sourceDir;
        File baseApkFile = sourceApkPath != null ? new File(sourceApkPath) : null;
        if (baseApkFile == null || !baseApkFile.exists() || baseApkFile.length() <= 100_000L) {
            File[] candidates = new File[]{
                    new File(".build-outputs/app-debug.apk"),
                    new File("../.build-outputs/app-debug.apk"),
                    new File("APK_DOWNLOAD/app-debug.apk"),
                    new File("../APK_DOWNLOAD/app-debug.apk")
            };
            for (File candidate : candidates) {
                if (candidate.exists() && candidate.isFile() && candidate.length() > 100_000L) {
                    baseApkFile = candidate;
                    break;
                }
            }
        }

        String compiledPackageName = getCompiledAppPackageName(project);
        String compiledAppName = project.getName() != null && !project.getName().trim().isEmpty()
                ? project.getName().trim()
                : " ";
        String customAppLogoPath = project.getAppLogoPath() != null ? project.getAppLogoPath().trim() : "";
        String customFloatingLogoPath = project.getFloatingLogoPath() != null ? project.getFloatingLogoPath().trim() : "";

        if (baseApkFile != null && baseApkFile.exists() && baseApkFile.length() > 100_000L) {
            File unsignedMergedApk = new File(workDir, "unsigned_merged.apk");
            File signedTempApk = new File(workDir, "signed_output.apk");
            try {
                // Repackage base.apk into the user's standalone app with patched binary AndroidManifest.xml,
                // custom App Logo (or empty icon if none set), 4-byte aligned STORED resources.arsc,
                // 4096-byte aligned STORED native .so libs, and bundled user widgets
                buildAlignedUnsignedApkFromBase(
                        baseApkFile,
                        unsignedMergedApk,
                        blueprintFiles,
                        components,
                        compiledPackageName,
                        compiledAppName,
                        customAppLogoPath,
                        customFloatingLogoPath
                );

                // Sign with Google's official ApkSigner (V1 + V2 + V3 APK Signature Schemes)
                boolean signedOk = signApkWithDebugKey(context, unsignedMergedApk, signedTempApk);
                if (signedOk && signedTempApk.exists() && signedTempApk.length() > 100_000L) {
                    copyFile(signedTempApk, outputApkFile);
                } else if (unsignedMergedApk.exists() && unsignedMergedApk.length() > 100_000L) {
                    copyFile(unsignedMergedApk, outputApkFile);
                } else {
                    copyFile(baseApkFile, outputApkFile);
                }
            } catch (Exception e) {
                copyFile(baseApkFile, outputApkFile);
            } finally {
                if (unsignedMergedApk.exists()) unsignedMergedApk.delete();
                if (signedTempApk.exists()) signedTempApk.delete();
            }
        } else {
            copyFile(blueprintZipFile, outputApkFile);
        }

        return new CompilationResult(
                outputApkFile,
                blueprintZipFile,
                serviceJavaPreview,
                blueprintFiles,
                outputApkFile.length(),
                compiledPackageName,
                compiledAppName.trim()
        );
    }

    private static void buildAlignedUnsignedApkFromBase(
            @NonNull File baseApkFile,
            @NonNull File outUnsignedApk,
            @NonNull Map<String, String> blueprintFiles,
            @NonNull List<CanvasComponentEntity> components,
            @NonNull String compiledPackageName,
            @NonNull String compiledAppName,
            @NonNull String customAppLogoPath,
            @NonNull String customFloatingLogoPath
    ) throws IOException {
        byte[] replacementIconPng = buildLauncherIconPngBytes(customAppLogoPath);
        Set<String> writtenEntries = new HashSet<>();
        try (ZipFile baseZip = new ZipFile(baseApkFile);
             FileOutputStream fos = new FileOutputStream(outUnsignedApk);
             CountingOutputStream cos = new CountingOutputStream(fos);
             ZipOutputStream zos = new ZipOutputStream(cos)) {

            // PASS 1: Write all STORED (uncompressed) entries FIRST before any DEFLATED entry
            // so CountingOutputStream byte offsets are 100% exact (no Deflater Data Descriptors).
            // 1A. Write resources.arsc at 4-byte alignment (required by Android 11-15 PackageParser mmap).
            ZipEntry arscEntry = baseZip.getEntry("resources.arsc");
            if (arscEntry != null) {
                byte[] arscBytes;
                try (InputStream is = baseZip.getInputStream(arscEntry)) {
                    arscBytes = readAllBytes(is);
                }
                writeAlignedStoredEntry(zos, cos, "resources.arsc", arscBytes, 4);
                writtenEntries.add("resources.arsc");
            }

            // 1B. Write all native libraries (lib/**/*.so) as STORED with 4096-byte page alignment
            // so Android PackageParser extractNativeLibs validation succeeds on all devices.
            Enumeration<? extends ZipEntry> storedPassEntries = baseZip.entries();
            while (storedPassEntries.hasMoreElements()) {
                ZipEntry entry = storedPassEntries.nextElement();
                String name = entry.getName();
                if (writtenEntries.contains(name) || isSignatureFile(name)) {
                    continue;
                }
                if (name.startsWith("lib/") && name.endsWith(".so")) {
                    byte[] soBytes;
                    try (InputStream is = baseZip.getInputStream(entry)) {
                        soBytes = readAllBytes(is);
                    }
                    writeAlignedStoredEntry(zos, cos, name, soBytes, 4096);
                    writtenEntries.add(name);
                }
            }

            // PASS 2: Write patched binary AndroidManifest.xml + DEFLATED entries
            Enumeration<? extends ZipEntry> entries = baseZip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();

                // Skip entries already written in Pass 1, old signature files, and previous injected files
                if (writtenEntries.contains(name)
                        || isSignatureFile(name)
                        || "assets/overlay_config.json".equals(name)
                        || "assets/app_logo.png".equals(name)
                        || "assets/floating_logo.png".equals(name)
                        || name.startsWith("assets/generated_project/")
                        || name.startsWith("assets/images/img_")
                        || name.startsWith("assets/sounds/on_snd_")
                        || name.startsWith("assets/sounds/off_snd_")
                        || name.startsWith("src/")) {
                    continue;
                }

                byte[] entryBytes;
                try (InputStream is = baseZip.getInputStream(entry)) {
                    entryBytes = readAllBytes(is);
                }

                // Patch the binary AndroidManifest.xml so the compiled APK has the user project's
                // unique package ID and App Name instead of Studio Error's package ID and label!
                if ("AndroidManifest.xml".equals(name)) {
                    entryBytes = patchBinaryAndroidManifest(entryBytes, compiledPackageName, compiledAppName);
                } else if (replacementIconPng != null
                        && name.startsWith("res/")
                        && name.endsWith(".png")
                        && name.contains("ic_launcher")) {
                    // Replace Studio Error's pre-made launcher icon with the user's chosen App Logo
                    // (or a 100% blank/transparent icon if the user has not chosen a logo yet)
                    entryBytes = replacementIconPng;
                }

                ZipEntry newEntry = new ZipEntry(name);
                newEntry.setMethod(ZipEntry.DEFLATED);
                zos.putNextEntry(newEntry);
                zos.write(entryBytes);
                zos.closeEntry();
                writtenEntries.add(name);
            }

            // Inject active assets/overlay_config.json (marks this APK as a Compiled Standalone App!)
            String overlayConfigJson = blueprintFiles.get("assets/overlay_config.json");
            if (overlayConfigJson != null && writtenEntries.add("assets/overlay_config.json")) {
                writeDeflatedUtf8Entry(zos, "assets/overlay_config.json", overlayConfigJson);
            }

            if (!customAppLogoPath.isEmpty() && writtenEntries.add("assets/app_logo.png")) {
                injectCustomFileIfPresent(zos, customAppLogoPath, "assets/app_logo.png");
            }

            if (!customFloatingLogoPath.isEmpty() && writtenEntries.add("assets/floating_logo.png")) {
                injectCustomFileIfPresent(zos, customFloatingLogoPath, "assets/floating_logo.png");
            }

            // Inject complete generated Android project blueprint under assets/generated_project/
            // AND generated Kotlin/Java sources under src/
            for (Map.Entry<String, String> fileEntry : blueprintFiles.entrySet()) {
                String relPath = fileEntry.getKey();
                String content = fileEntry.getValue();
                String genPath = "assets/generated_project/" + relPath;
                if (writtenEntries.add(genPath)) {
                    writeDeflatedUtf8Entry(zos, genPath, content);
                }
                if (relPath.startsWith("src/") && writtenEntries.add(relPath)) {
                    writeDeflatedUtf8Entry(zos, relPath, content);
                }
            }

            // Inject any user-attached custom images and ON/OFF sound files
            for (CanvasComponentEntity comp : components) {
                String imgEntry = "assets/images/img_" + comp.getId() + ".jpg";
                if (writtenEntries.add(imgEntry)) {
                    injectCustomFileIfPresent(zos, comp.getCustomImagePath(), imgEntry);
                }
                String onSndEntry = "assets/sounds/on_snd_" + comp.getId() + ".mp3";
                if (writtenEntries.add(onSndEntry)) {
                    injectCustomFileIfPresent(zos, comp.getCustomSoundPath(), onSndEntry);
                }
                String offSndEntry = "assets/sounds/off_snd_" + comp.getId() + ".mp3";
                if (writtenEntries.add(offSndEntry)) {
                    injectCustomFileIfPresent(zos, comp.getOffCustomSoundPath(), offSndEntry);
                }
            }
        }
    }

    /**
     * Rebuilds the binary AndroidManifest.xml (AXML) String Pool chunk so that:
     * 1. The compiled APK has its own unique package name (plus matching provider authorities & permissions)
     *    so Android installs it as a standalone app instead of updating Studio Error.
     * 2. The compiled APK displays the user's created project name as its Android App Label.
     */
    @NonNull
    public static byte[] patchBinaryAndroidManifest(
            @NonNull byte[] axml,
            @NonNull String newPackageName,
            @NonNull String newAppLabel
    ) {
        try {
            if (axml.length < 36) {
                return fallbackInPlacePackagePatch(axml, newPackageName);
            }
            int xmlType = readU16LE(axml, 0);
            int xmlHeaderSize = readU16LE(axml, 2);
            if (xmlType != 0x0003 || xmlHeaderSize < 8) {
                return fallbackInPlacePackagePatch(axml, newPackageName);
            }

            int spStart = xmlHeaderSize;
            int spType = readU16LE(axml, spStart);
            int spHeaderSize = readU16LE(axml, spStart + 2);
            int spChunkSize = readIntLE(axml, spStart + 4);
            int stringCount = readIntLE(axml, spStart + 8);
            int styleCount = readIntLE(axml, spStart + 12);
            int flags = readIntLE(axml, spStart + 16);
            int stringsStart = readIntLE(axml, spStart + 20);

            if (spType != 0x0001 || spHeaderSize != 28 || styleCount != 0
                    || stringCount <= 0 || spStart + spChunkSize > axml.length) {
                return fallbackInPlacePackagePatch(axml, newPackageName);
            }

            boolean isUtf8 = (flags & 0x00000100) != 0;
            String[] strings = new String[stringCount];
            for (int i = 0; i < stringCount; i++) {
                int relOffset = readIntLE(axml, spStart + spHeaderSize + (i * 4));
                int absPos = spStart + stringsStart + relOffset;
                if (!isUtf8) {
                    int u16len = readU16LE(axml, absPos);
                    int hdrBytes = 2;
                    if ((u16len & 0x8000) != 0) {
                        u16len = ((u16len & 0x7FFF) << 16) | readU16LE(axml, absPos + 2);
                        hdrBytes = 4;
                    }
                    strings[i] = new String(axml, absPos + hdrBytes, u16len * 2, StandardCharsets.UTF_16LE);
                } else {
                    int p = absPos;
                    int b1 = axml[p++] & 0xFF;
                    if ((b1 & 0x80) != 0) p++;
                    int u8len = axml[p++] & 0xFF;
                    if ((u8len & 0x80) != 0) {
                        u8len = ((u8len & 0x7F) << 8) | (axml[p++] & 0xFF);
                    }
                    strings[i] = new String(axml, p, u8len, StandardCharsets.UTF_8);
                }
            }

            for (int i = 0; i < stringCount; i++) {
                String s = strings[i];
                if (s == null) continue;
                if (s.contains(STUDIO_BASE_PACKAGE)) {
                    strings[i] = s.replace(STUDIO_BASE_PACKAGE, newPackageName);
                } else if (s.equals(STUDIO_BASE_LABEL) || s.equalsIgnoreCase("STUDIO ERROR")) {
                    strings[i] = newAppLabel;
                }
            }

            ByteArrayOutputStream stringDataBaos = new ByteArrayOutputStream(spChunkSize);
            int[] newOffsets = new int[stringCount];
            for (int i = 0; i < stringCount; i++) {
                newOffsets[i] = stringDataBaos.size();
                String s = strings[i] != null ? strings[i] : "";
                if (!isUtf8) {
                    byte[] utf16Bytes = s.getBytes(StandardCharsets.UTF_16LE);
                    int charLen = s.length();
                    writeU16LE(stringDataBaos, charLen);
                    stringDataBaos.write(utf16Bytes);
                    writeU16LE(stringDataBaos, 0);
                } else {
                    byte[] utf8Bytes = s.getBytes(StandardCharsets.UTF_8);
                    int u16len = s.length();
                    int u8len = utf8Bytes.length;
                    if (u16len >= 0x80) {
                        stringDataBaos.write(0x80 | ((u16len >> 8) & 0x7F));
                        stringDataBaos.write(u16len & 0xFF);
                    } else {
                        stringDataBaos.write(u16len & 0x7F);
                    }
                    if (u8len >= 0x80) {
                        stringDataBaos.write(0x80 | ((u8len >> 8) & 0x7F));
                        stringDataBaos.write(u8len & 0xFF);
                    } else {
                        stringDataBaos.write(u8len & 0x7F);
                    }
                    stringDataBaos.write(utf8Bytes);
                    stringDataBaos.write(0);
                }
            }
            while (stringDataBaos.size() % 4 != 0) {
                stringDataBaos.write(0);
            }

            byte[] newStringData = stringDataBaos.toByteArray();
            int newStringsStart = spHeaderSize + (stringCount * 4);
            int newSpChunkSize = newStringsStart + newStringData.length;
            int newFlags = flags & ~0x00000001; // Clear SORTED_FLAG

            int tailOffset = spStart + spChunkSize;
            int tailLen = axml.length - tailOffset;
            int newTotalSize = xmlHeaderSize + newSpChunkSize + tailLen;

            ByteArrayOutputStream out = new ByteArrayOutputStream(newTotalSize);
            // 8-byte AXML header
            writeU16LE(out, 0x0003);
            writeU16LE(out, xmlHeaderSize);
            writeIntLE(out, newTotalSize);
            if (xmlHeaderSize > 8) {
                out.write(axml, 8, xmlHeaderSize - 8);
            }

            // 28-byte String Pool header
            writeU16LE(out, 0x0001);
            writeU16LE(out, spHeaderSize);
            writeIntLE(out, newSpChunkSize);
            writeIntLE(out, stringCount);
            writeIntLE(out, 0); // styleCount = 0
            writeIntLE(out, newFlags);
            writeIntLE(out, newStringsStart);
            writeIntLE(out, 0); // stylesStart = 0

            // String offsets table
            for (int i = 0; i < stringCount; i++) {
                writeIntLE(out, newOffsets[i]);
            }

            // Encoded string data
            out.write(newStringData);

            // Remaining AXML chunks (Resource ID map + XML namespace/element nodes)
            out.write(axml, tailOffset, tailLen);
            return out.toByteArray();
        } catch (Exception e) {
            return fallbackInPlacePackagePatch(axml, newPackageName);
        }
    }

    @NonNull
    private static byte[] fallbackInPlacePackagePatch(@NonNull byte[] axml, @NonNull String newPackageName) {
        if (newPackageName.length() != STUDIO_BASE_PACKAGE.length()) {
            return axml;
        }
        byte[] result = axml.clone();
        replaceBytesInPlace(
                result,
                STUDIO_BASE_PACKAGE.getBytes(StandardCharsets.UTF_16LE),
                newPackageName.getBytes(StandardCharsets.UTF_16LE)
        );
        replaceBytesInPlace(
                result,
                STUDIO_BASE_PACKAGE.getBytes(StandardCharsets.UTF_8),
                newPackageName.getBytes(StandardCharsets.UTF_8)
        );
        return result;
    }

    private static void replaceBytesInPlace(byte[] buffer, byte[] target, byte[] replacement) {
        if (target.length == 0 || target.length != replacement.length) return;
        for (int i = 0; i <= buffer.length - target.length; i++) {
            boolean match = true;
            for (int j = 0; j < target.length; j++) {
                if (buffer[i + j] != target[j]) {
                    match = false;
                    break;
                }
            }
            if (match) {
                System.arraycopy(replacement, 0, buffer, i, replacement.length);
                i += target.length - 1;
            }
        }
    }

    private static int readU16LE(byte[] b, int offset) {
        return (b[offset] & 0xFF) | ((b[offset + 1] & 0xFF) << 8);
    }

    private static int readIntLE(byte[] b, int offset) {
        return (b[offset] & 0xFF)
                | ((b[offset + 1] & 0xFF) << 8)
                | ((b[offset + 2] & 0xFF) << 16)
                | ((b[offset + 3] & 0xFF) << 24);
    }

    private static void writeU16LE(OutputStream out, int val) throws IOException {
        out.write(val & 0xFF);
        out.write((val >> 8) & 0xFF);
    }

    private static void writeIntLE(OutputStream out, int val) throws IOException {
        out.write(val & 0xFF);
        out.write((val >> 8) & 0xFF);
        out.write((val >> 16) & 0xFF);
        out.write((val >> 24) & 0xFF);
    }

    private static void writeAlignedStoredEntry(
            @NonNull ZipOutputStream zos,
            @NonNull CountingOutputStream cos,
            @NonNull String name,
            @NonNull byte[] data,
            int alignment
    ) throws IOException {
        CRC32 crc = new CRC32();
        crc.update(data);

        ZipEntry storedEntry = new ZipEntry(name);
        storedEntry.setMethod(ZipEntry.STORED);
        storedEntry.setSize(data.length);
        storedEntry.setCompressedSize(data.length);
        storedEntry.setCrc(crc.getValue());

        byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);
        long dataStartWithoutExtra = cos.getBytesWritten() + 30L + nameBytes.length;
        int remainder = (int) (dataStartWithoutExtra % alignment);
        if (remainder != 0) {
            int padBytes = alignment - remainder;
            storedEntry.setExtra(new byte[padBytes]);
        }

        zos.putNextEntry(storedEntry);
        zos.write(data);
        zos.closeEntry();
    }

    private static boolean signApkWithDebugKey(
            @NonNull Context context,
            @NonNull File inputUnsignedApk,
            @NonNull File outputSignedApk
    ) {
        try {
            byte[] pk8Bytes = loadSigningAssetBytes(context, "signing/debug_key.pk8");
            byte[] certBytes = loadSigningAssetBytes(context, "signing/debug_cert.x509.der");

            KeyFactory kf = KeyFactory.getInstance("RSA");
            PrivateKey privateKey = kf.generatePrivate(new PKCS8EncodedKeySpec(pk8Bytes));

            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            X509Certificate cert = (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(certBytes));

            ApkSigner.SignerConfig signerConfig = new ApkSigner.SignerConfig.Builder(
                    "ANDROID",
                    privateKey,
                    Collections.singletonList(cert)
            ).build();

            ApkSigner signer = new ApkSigner.Builder(Collections.singletonList(signerConfig))
                    .setInputApk(inputUnsignedApk)
                    .setOutputApk(outputSignedApk)
                    .setMinSdkVersion(24)
                    .setV1SigningEnabled(true)
                    .setV2SigningEnabled(true)
                    .setV3SigningEnabled(true)
                    .build();

            signer.sign();
            return outputSignedApk.exists() && outputSignedApk.length() > 100_000L;
        } catch (Exception e) {
            return false;
        }
    }

    @NonNull
    private static byte[] loadSigningAssetBytes(@NonNull Context context, @NonNull String assetRelPath) throws IOException {
        try (InputStream is = context.getAssets().open(assetRelPath)) {
            return readAllBytes(is);
        } catch (Exception ignored) {
            File[] fallbacks = new File[]{
                    new File("src/main/assets/" + assetRelPath),
                    new File("app/src/main/assets/" + assetRelPath)
            };
            for (File f : fallbacks) {
                if (f.exists() && f.isFile()) {
                    try (FileInputStream fis = new FileInputStream(f)) {
                        return readAllBytes(fis);
                    }
                }
            }
            throw new IOException("Signing asset not found: " + assetRelPath);
        }
    }

    private static void writeBlueprintSourceZip(
            @NonNull File destZip,
            @NonNull Map<String, String> blueprintFiles,
            @NonNull List<CanvasComponentEntity> components
    ) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(destZip);
             ZipOutputStream zos = new ZipOutputStream(fos)) {
            for (Map.Entry<String, String> entry : blueprintFiles.entrySet()) {
                writeDeflatedUtf8Entry(zos, entry.getKey(), entry.getValue());
            }
            for (CanvasComponentEntity comp : components) {
                injectCustomFileIfPresent(zos, comp.getCustomImagePath(), "assets/images/img_" + comp.getId() + ".jpg");
                injectCustomFileIfPresent(zos, comp.getCustomSoundPath(), "assets/sounds/on_snd_" + comp.getId() + ".mp3");
                injectCustomFileIfPresent(zos, comp.getOffCustomSoundPath(), "assets/sounds/off_snd_" + comp.getId() + ".mp3");
            }
        }
    }

    private static void writeDeflatedUtf8Entry(
            @NonNull ZipOutputStream zos,
            @NonNull String entryName,
            @NonNull String content
    ) throws IOException {
        ZipEntry entry = new ZipEntry(entryName);
        entry.setMethod(ZipEntry.DEFLATED);
        zos.putNextEntry(entry);
        zos.write(content.getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();
    }

    private static void injectCustomFileIfPresent(
            @NonNull ZipOutputStream zos,
            String filePath,
            @NonNull String entryName
    ) throws IOException {
        if (filePath == null || filePath.trim().isEmpty()) return;
        File f = new File(filePath.trim());
        if (f.exists() && f.isFile()) {
            ZipEntry entry = new ZipEntry(entryName);
            entry.setMethod(ZipEntry.DEFLATED);
            zos.putNextEntry(entry);
            try (FileInputStream fis = new FileInputStream(f)) {
                byte[] buf = new byte[8192];
                int read;
                while ((read = fis.read(buf)) != -1) {
                    zos.write(buf, 0, read);
                }
            }
            zos.closeEntry();
        }
    }

    @Nullable
    private static byte[] buildLauncherIconPngBytes(@NonNull String customAppLogoPath) {
        try {
            Bitmap iconBitmap = null;
            if (!customAppLogoPath.trim().isEmpty()) {
                File f = new File(customAppLogoPath.trim());
                if (f.exists() && f.isFile()) {
                    Bitmap decoded = BitmapFactory.decodeFile(f.getAbsolutePath());
                    if (decoded != null) {
                        iconBitmap = Bitmap.createScaledBitmap(decoded, 192, 192, true);
                    }
                }
            }
            if (iconBitmap == null) {
                // No logo pre-filled: create a 100% blank/empty transparent icon
                iconBitmap = Bitmap.createBitmap(192, 192, Bitmap.Config.ARGB_8888);
                iconBitmap.eraseColor(Color.TRANSPARENT);
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            iconBitmap.compress(Bitmap.CompressFormat.PNG, 100, baos);
            return baos.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean isSignatureFile(@NonNull String name) {
        if (!name.startsWith("META-INF/")) return false;
        String upper = name.toUpperCase(Locale.US);
        return upper.endsWith(".SF")
                || upper.endsWith(".RSA")
                || upper.endsWith(".DSA")
                || upper.endsWith(".EC")
                || upper.equals("META-INF/MANIFEST.MF")
                || upper.startsWith("META-INF/SIG-");
    }

    @NonNull
    private static byte[] readAllBytes(@NonNull InputStream is) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        int read;
        while ((read = is.read(buf)) != -1) {
            baos.write(buf, 0, read);
        }
        return baos.toByteArray();
    }

    private static void copyFile(@NonNull File src, @NonNull File dst) throws IOException {
        if (dst.getParentFile() != null && !dst.getParentFile().exists()) {
            dst.getParentFile().mkdirs();
        }
        try (FileInputStream in = new FileInputStream(src);
             FileOutputStream out = new FileOutputStream(dst)) {
            byte[] buf = new byte[16384];
            int len;
            while ((len = in.read(buf)) != -1) {
                out.write(buf, 0, len);
            }
        }
    }

    private static final class CountingOutputStream extends FilterOutputStream {
        private long bytesWritten = 0L;

        CountingOutputStream(OutputStream out) {
            super(out);
        }

        @Override
        public void write(int b) throws IOException {
            out.write(b);
            bytesWritten++;
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            out.write(b, off, len);
            bytesWritten += len;
        }

        long getBytesWritten() {
            return bytesWritten;
        }
    }

    // =========================================================================================
    // DYNAMIC SOURCE & XML BLUEPRINT GENERATORS
    // =========================================================================================

    @NonNull
    private static String generateManifestXml(@NonNull StudioProjectEntity project) {
        String pkg = getCompiledAppPackageName(project);
        int vCode = Math.max(1, project.getVersionCode());
        String vName = project.getVersionName() != null && !project.getVersionName().trim().isEmpty()
                ? project.getVersionName().trim()
                : "1.0";
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "    package=\"" + escapeXml(pkg) + "\"\n"
                + "    android:versionCode=\"" + vCode + "\"\n"
                + "    android:versionName=\"" + escapeXml(vName) + "\">\n\n"
                + "    <uses-permission android:name=\"android.permission.SYSTEM_ALERT_WINDOW\" />\n"
                + "    <uses-permission android:name=\"android.permission.FOREGROUND_SERVICE\" />\n"
                + "    <uses-permission android:name=\"android.permission.FOREGROUND_SERVICE_SPECIAL_USE\" />\n"
                + "    <uses-permission android:name=\"android.permission.VIBRATE\" />\n"
                + "    <uses-permission android:name=\"android.permission.POST_NOTIFICATIONS\" />\n\n"
                + "    <application\n"
                + "        android:allowBackup=\"true\"\n"
                + "        android:label=\"" + escapeXml(project.getName()) + "\"\n"
                + "        android:supportsRtl=\"true\"\n"
                + "        android:theme=\"@android:style/Theme.DeviceDefault.Light.NoActionBar\">\n\n"
                + "        <activity\n"
                + "            android:name=\".MainActivity\"\n"
                + "            android:exported=\"true\"\n"
                + "            android:windowSoftInputMode=\"adjustResize\">\n"
                + "            <intent-filter>\n"
                + "                <action android:name=\"android.intent.action.MAIN\" />\n"
                + "                <category android:name=\"android.intent.category.LAUNCHER\" />\n"
                + "            </intent-filter>\n"
                + "        </activity>\n\n"
                + "        <activity\n"
                + "            android:name=\".ProjectLauncherActivity\"\n"
                + "            android:exported=\"false\" />\n\n"
                + "        <service\n"
                + "            android:name=\".FloatingModMenuService\"\n"
                + "            android:enabled=\"true\"\n"
                + "            android:exported=\"false\"\n"
                + "            android:foregroundServiceType=\"specialUse\" />\n"
                + "    </application>\n"
                + "</manifest>\n";
    }

    @NonNull
    private static String generateAppBuildGradle(@NonNull StudioProjectEntity project) {
        String pkg = getCompiledAppPackageName(project);
        int minSdk = Math.max(21, project.getMinSdk());
        int targetSdk = Math.max(minSdk, project.getTargetSdk());
        int vCode = Math.max(1, project.getVersionCode());
        String vName = project.getVersionName() != null && !project.getVersionName().trim().isEmpty()
                ? project.getVersionName().trim()
                : "1.0";
        return "plugins {\n"
                + "    id 'com.android.application'\n"
                + "}\n\n"
                + "android {\n"
                + "    namespace '" + escapeJava(pkg) + "'\n"
                + "    compileSdk " + targetSdk + "\n\n"
                + "    defaultConfig {\n"
                + "        applicationId \"" + escapeJava(pkg) + "\"\n"
                + "        minSdk " + minSdk + "\n"
                + "        targetSdk " + targetSdk + "\n"
                + "        versionCode " + vCode + "\n"
                + "        versionName \"" + escapeJava(vName) + "\"\n"
                + "    }\n\n"
                + "    signingConfigs {\n"
                + "        debug {\n"
                + "            storeFile file('debug.keystore')\n"
                + "            storePassword 'android'\n"
                + "            keyAlias 'androiddebugkey'\n"
                + "            keyPassword 'android'\n"
                + "            v1SigningEnabled true\n"
                + "            v2SigningEnabled true\n"
                + "        }\n"
                + "    }\n\n"
                + "    buildTypes {\n"
                + "        debug {\n"
                + "            signingConfig signingConfigs.debug\n"
                + "        }\n"
                + "        release {\n"
                + "            minifyEnabled false\n"
                + "            signingConfig signingConfigs.debug\n"
                + "        }\n"
                + "    }\n\n"
                + "    compileOptions {\n"
                + "        sourceCompatibility JavaVersion.VERSION_11\n"
                + "        targetCompatibility JavaVersion.VERSION_11\n"
                + "    }\n"
                + "}\n\n"
                + "dependencies {\n"
                + "    implementation 'androidx.core:core:1.13.1'\n"
                + "}\n";
    }

    @NonNull
    private static String generateSettingsGradle(@NonNull StudioProjectEntity project) {
        return "rootProject.name = \"" + escapeJava(project.getName()) + "\"\n"
                + "include ':app'\n";
    }

    @NonNull
    private static String generateMainActivityJava(
            @NonNull StudioProjectEntity project,
            @NonNull List<CanvasComponentEntity> components
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("package com.floating.modmenu;\n\n");
        sb.append("import android.app.Activity;\n");
        sb.append("import android.content.Intent;\n");
        sb.append("import android.net.Uri;\n");
        sb.append("import android.os.Build;\n");
        sb.append("import android.os.Bundle;\n");
        sb.append("import android.provider.Settings;\n");
        sb.append("import android.widget.Button;\n");
        sb.append("import android.widget.FrameLayout;\n");
        sb.append("import android.widget.Toast;\n\n");
        sb.append("public class MainActivity extends Activity {\n\n");
        sb.append("    private EmptyCanvasWorkspaceView canvasWorkspaceView;\n");
        sb.append("    private BottomPropertyInspectorDock inspectorDock;\n\n");
        sb.append("    @Override\n");
        sb.append("    protected void onCreate(Bundle savedInstanceState) {\n");
        sb.append("        super.onCreate(savedInstanceState);\n");
        sb.append("        setContentView(R.layout.activity_main);\n\n");
        sb.append("        FrameLayout canvasHost = findViewById(R.id.empty_canvas_workspace_host);\n");
        sb.append("        FrameLayout inspectorHost = findViewById(R.id.bottom_inspector_dock_host);\n");
        sb.append("        canvasWorkspaceView = new EmptyCanvasWorkspaceView(this);\n");
        sb.append("        inspectorDock = new BottomPropertyInspectorDock(this);\n");
        sb.append("        canvasHost.addView(canvasWorkspaceView);\n");
        sb.append("        inspectorHost.addView(inspectorDock);\n\n");
        sb.append("        Button floatBtn = findViewById(R.id.btn_float_overlay);\n");
        sb.append("        floatBtn.setOnClickListener(v -> launchFloatingModMenu());\n");
        sb.append("    }\n\n");
        sb.append("    private void launchFloatingModMenu() {\n");
        sb.append("        if (!Settings.canDrawOverlays(this)) {\n");
        sb.append("            Intent perm = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,\n");
        sb.append("                    Uri.parse(\"package:\" + getPackageName()));\n");
        sb.append("            startActivity(perm);\n");
        sb.append("            return;\n");
        sb.append("        }\n");
        sb.append("        Intent svc = new Intent(this, FloatingModMenuService.class);\n");
        sb.append("        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {\n");
        sb.append("            startForegroundService(svc);\n");
        sb.append("        } else {\n");
        sb.append("            startService(svc);\n");
        sb.append("        }\n");
        sb.append("        Toast.makeText(this, \"Launched " + escapeJava(project.getOverlayTitle())
                + " (" + components.size() + " items)\", Toast.LENGTH_SHORT).show();\n");
        sb.append("    }\n");
        sb.append("}\n");
        return sb.toString();
    }

    @NonNull
    private static String generateProjectLauncherJava(@NonNull StudioProjectEntity project) {
        return "package com.floating.modmenu;\n\n"
                + "import android.app.Activity;\n"
                + "import android.content.Intent;\n"
                + "import android.os.Bundle;\n"
                + "import android.widget.Button;\n"
                + "import android.widget.LinearLayout;\n"
                + "import android.widget.TextView;\n\n"
                + "public class ProjectLauncherActivity extends Activity {\n"
                + "    @Override\n"
                + "    protected void onCreate(Bundle savedInstanceState) {\n"
                + "        super.onCreate(savedInstanceState);\n"
                + "        LinearLayout root = new LinearLayout(this);\n"
                + "        root.setOrientation(LinearLayout.VERTICAL);\n"
                + "        TextView title = new TextView(this);\n"
                + "        title.setText(\"Project: " + escapeJava(project.getName()) + "\");\n"
                + "        Button openBtn = new Button(this);\n"
                + "        openBtn.setText(\"Open Workspace\");\n"
                + "        openBtn.setOnClickListener(v -> startActivity(new Intent(this, MainActivity.class)));\n"
                + "        root.addView(title);\n"
                + "        root.addView(openBtn);\n"
                + "        setContentView(root);\n"
                + "    }\n"
                + "}\n";
    }

    @NonNull
    private static String generateEmptyCanvasWorkspaceJava(
            @NonNull StudioProjectEntity project,
            @NonNull List<CanvasComponentEntity> components
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("package com.floating.modmenu;\n\n");
        sb.append("import android.content.Context;\n");
        sb.append("import android.graphics.Canvas;\n");
        sb.append("import android.graphics.Color;\n");
        sb.append("import android.graphics.Paint;\n");
        sb.append("import android.widget.FrameLayout;\n\n");
        sb.append("public class EmptyCanvasWorkspaceView extends FrameLayout {\n");
        sb.append("    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);\n");
        sb.append("    public static final int CANVAS_WIDTH_DP = ").append(project.getCanvasWidthDp()).append(";\n");
        sb.append("    public static final int CANVAS_HEIGHT_DP = ").append(project.getCanvasHeightDp()).append(";\n");
        sb.append("    public static final boolean AUTO_FIX_SIZE = ").append(project.getAutoFixSize()).append(";\n");
        sb.append("    public static final int CONFIGURED_WIDGET_COUNT = ").append(components.size()).append(";\n\n");
        sb.append("    public EmptyCanvasWorkspaceView(Context context) {\n");
        sb.append("        super(context);\n");
        sb.append("        setWillNotDraw(false);\n");
        sb.append("        setBackgroundColor(Color.parseColor(\"").append(escapeJava(project.getCanvasBgColorHex())).append("\"));\n");
        sb.append("        gridPaint.setColor(0x2E94A3B8);\n");
        sb.append("        gridPaint.setStrokeWidth(2f);\n");
        sb.append("    }\n\n");
        sb.append("    @Override\n");
        sb.append("    protected void onDraw(Canvas canvas) {\n");
        sb.append("        super.onDraw(canvas);\n");
        sb.append("        for (int x = 0; x < getWidth(); x += 36) canvas.drawLine(x, 0, x, getHeight(), gridPaint);\n");
        sb.append("        for (int y = 0; y < getHeight(); y += 36) canvas.drawLine(0, y, getWidth(), y, gridPaint);\n");
        sb.append("    }\n");
        sb.append("}\n");
        return sb.toString();
    }

    @NonNull
    private static String generateBottomInspectorJava(@NonNull List<CanvasComponentEntity> components) {
        StringBuilder sb = new StringBuilder();
        sb.append("package com.floating.modmenu;\n\n");
        sb.append("import android.content.Context;\n");
        sb.append("import android.view.LayoutInflater;\n");
        sb.append("import android.widget.LinearLayout;\n\n");
        sb.append("public class BottomPropertyInspectorDock extends LinearLayout {\n");
        sb.append("    public BottomPropertyInspectorDock(Context context) {\n");
        sb.append("        super(context);\n");
        sb.append("        setOrientation(VERTICAL);\n");
        sb.append("        LayoutInflater.from(context).inflate(R.layout.inspector_dock, this, true);\n");
        sb.append("    }\n\n");
        sb.append("    public void applyComponentPatch(int index, boolean turnOn) {\n");
        sb.append("        BinaryOffsetPatcher.executeConfiguredComponentWrite(getContext().getFilesDir(), index, turnOn);\n");
        sb.append("    }\n");
        sb.append("}\n");
        return sb.toString();
    }

    @NonNull
    private static String generateBinaryOffsetPatcherJava(
            @NonNull StudioProjectEntity project,
            @NonNull List<CanvasComponentEntity> components
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("package com.floating.modmenu;\n\n");
        sb.append("import java.io.File;\n");
        sb.append("import java.io.RandomAccessFile;\n\n");
        sb.append("/**\n");
        sb.append(" * Dynamically generated Binary File Offset Reader & Writer for project: ")
                .append(escapeJava(project.getName())).append("\n");
        sb.append(" */\n");
        sb.append("public final class BinaryOffsetPatcher {\n\n");
        sb.append("    public static final class ComponentPatchSpec {\n");
        sb.append("        public final long id;\n");
        sb.append("        public final String label;\n");
        sb.append("        public final String targetFilePath;\n");
        sb.append("        public final String byteOffsetHex;\n");
        sb.append("        public final String onPayloadHex;\n");
        sb.append("        public final String offPayloadHex;\n\n");
        sb.append("        public ComponentPatchSpec(long id, String label, String targetFilePath, String byteOffsetHex, String onPayloadHex, String offPayloadHex) {\n");
        sb.append("            this.id = id;\n");
        sb.append("            this.label = label;\n");
        sb.append("            this.targetFilePath = targetFilePath;\n");
        sb.append("            this.byteOffsetHex = byteOffsetHex;\n");
        sb.append("            this.onPayloadHex = onPayloadHex;\n");
        sb.append("            this.offPayloadHex = offPayloadHex;\n");
        sb.append("        }\n");
        sb.append("    }\n\n");
        sb.append("    public static final ComponentPatchSpec[] SPECS = new ComponentPatchSpec[] {\n");
        for (int i = 0; i < components.size(); i++) {
            CanvasComponentEntity c = components.get(i);
            sb.append("        new ComponentPatchSpec(")
                    .append(c.getId()).append("L, \"")
                    .append(escapeJava(c.getLabel())).append("\", \"")
                    .append(escapeJava(c.getTargetFilePath())).append("\", \"")
                    .append(escapeJava(c.getByteOffsetHex())).append("\", \"")
                    .append(escapeJava(c.getOnPayloadHex())).append("\", \"")
                    .append(escapeJava(c.getOffPayloadHex())).append("\")")
                    .append(i < components.size() - 1 ? ",\n" : "\n");
        }
        sb.append("    };\n\n");
        sb.append("    public static void executeConfiguredComponentWrite(File fallbackDir, int index, boolean isOn) {\n");
        sb.append("        if (index < 0 || index >= SPECS.length) return;\n");
        sb.append("        ComponentPatchSpec spec = SPECS[index];\n");
        sb.append("        writeHexPayloadAtOffset(fallbackDir, spec.targetFilePath, spec.byteOffsetHex, isOn ? spec.onPayloadHex : spec.offPayloadHex);\n");
        sb.append("    }\n\n");
        sb.append("    public static void writeHexPayloadAtOffset(File fallbackDir, String targetPath, String offsetHex, String payloadHex) {\n");
        sb.append("        try {\n");
        sb.append("            File target = (targetPath == null || targetPath.trim().isEmpty())\n");
        sb.append("                    ? new File(fallbackDir, \"overlay_state.bin\")\n");
        sb.append("                    : new File(targetPath.trim());\n");
        sb.append("            if (target.getParentFile() != null && !target.getParentFile().exists()) {\n");
        sb.append("                target.getParentFile().mkdirs();\n");
        sb.append("            }\n");
        sb.append("            long offset = parseOffset(offsetHex);\n");
        sb.append("            byte[] payload = parsePayloadBytes(payloadHex);\n");
        sb.append("            try (RandomAccessFile raf = new RandomAccessFile(target, \"rw\")) {\n");
        sb.append("                if (raf.length() < offset + payload.length) {\n");
        sb.append("                    raf.setLength(offset + payload.length);\n");
        sb.append("                }\n");
        sb.append("                raf.seek(offset);\n");
        sb.append("                raf.write(payload);\n");
        sb.append("                raf.getFD().sync();\n");
        sb.append("            }\n");
        sb.append("        } catch (Exception ignored) {\n");
        sb.append("        }\n");
        sb.append("    }\n\n");
        sb.append("    private static long parseOffset(String raw) {\n");
        sb.append("        if (raw == null) return 0L;\n");
        sb.append("        String clean = raw.trim().toLowerCase();\n");
        sb.append("        try {\n");
        sb.append("            if (clean.startsWith(\"0x\")) return Long.parseLong(clean.substring(2), 16);\n");
        sb.append("            return Long.parseLong(clean);\n");
        sb.append("        } catch (Exception e) {\n");
        sb.append("            return 0L;\n");
        sb.append("        }\n");
        sb.append("    }\n\n");
        sb.append("    private static byte[] parsePayloadBytes(String raw) {\n");
        sb.append("        if (raw == null || raw.trim().isEmpty()) return new byte[]{0};\n");
        sb.append("        String clean = raw.trim().replace(\"0x\", \"\").replace(\"0X\", \"\").replaceAll(\"\\\\s+\", \"\");\n");
        sb.append("        if (clean.length() % 2 != 0) clean = \"0\" + clean;\n");
        sb.append("        int len = clean.length() / 2;\n");
        sb.append("        byte[] out = new byte[len];\n");
        sb.append("        for (int i = 0; i < len; i++) {\n");
        sb.append("            int hi = Character.digit(clean.charAt(i * 2), 16);\n");
        sb.append("            int lo = Character.digit(clean.charAt(i * 2 + 1), 16);\n");
        sb.append("            out[i] = (byte) ((Math.max(0, hi) << 4) | Math.max(0, lo));\n");
        sb.append("        }\n");
        sb.append("        return out;\n");
        sb.append("    }\n");
        sb.append("}\n");
        return sb.toString();
    }

    @NonNull
    private static String generateFloatingServiceJava(
            @NonNull StudioProjectEntity project,
            @NonNull List<CanvasComponentEntity> components
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("package com.floating.modmenu;\n\n");
        sb.append("import android.app.Service;\n");
        sb.append("import android.content.Context;\n");
        sb.append("import android.content.Intent;\n");
        sb.append("import android.graphics.Color;\n");
        sb.append("import android.graphics.PixelFormat;\n");
        sb.append("import android.os.Build;\n");
        sb.append("import android.os.IBinder;\n");
        sb.append("import android.view.Gravity;\n");
        sb.append("import android.view.MotionEvent;\n");
        sb.append("import android.view.View;\n");
        sb.append("import android.view.WindowManager;\n");
        sb.append("import android.widget.Button;\n");
        sb.append("import android.widget.EditText;\n");
        sb.append("import android.widget.FrameLayout;\n");
        sb.append("import android.widget.LinearLayout;\n");
        sb.append("import android.widget.SeekBar;\n");
        sb.append("import android.widget.Switch;\n");
        sb.append("import android.widget.TextView;\n\n");
        sb.append("// Raw Java Floating Mod Menu Service generated by STUDIO ERROR\n");
        sb.append("public class FloatingModMenuService extends Service {\n");
        sb.append("    public static final String TITLE = \"").append(escapeJava(project.getOverlayTitle())).append("\";\n");
        sb.append("    public static final int WIDTH_DP = ").append(project.getCanvasWidthDp()).append(";\n");
        sb.append("    public static final int HEIGHT_DP = ").append(project.getCanvasHeightDp()).append(";\n");
        sb.append("    public static final boolean AUTO_FIX_SIZE = ").append(project.getAutoFixSize()).append(";\n\n");
        sb.append("    private WindowManager windowManager;\n");
        sb.append("    private View floatingRoot;\n\n");
        sb.append("    @Override\n");
        sb.append("    public void onCreate() {\n");
        sb.append("        super.onCreate();\n");
        sb.append("        windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);\n");
        sb.append("        showFloatingOverlay();\n");
        sb.append("    }\n\n");
        sb.append("    private void showFloatingOverlay() {\n");
        sb.append("        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O\n");
        sb.append("                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY\n");
        sb.append("                : WindowManager.LayoutParams.TYPE_PHONE;\n");
        sb.append("        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(\n");
        sb.append("                WindowManager.LayoutParams.WRAP_CONTENT,\n");
        sb.append("                WindowManager.LayoutParams.WRAP_CONTENT,\n");
        sb.append("                type,\n");
        sb.append("                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,\n");
        sb.append("                PixelFormat.TRANSLUCENT\n");
        sb.append("        );\n");
        sb.append("        lp.gravity = Gravity.TOP | Gravity.START;\n");
        sb.append("        lp.x = 32;\n");
        sb.append("        lp.y = 160;\n\n");
        sb.append("        LinearLayout root = new LinearLayout(this);\n");
        sb.append("        root.setOrientation(LinearLayout.VERTICAL);\n");
        sb.append("        root.setBackgroundColor(Color.parseColor(\"").append(escapeJava(project.getCanvasBgColorHex())).append("\"));\n\n");

        for (int i = 0; i < components.size(); i++) {
            CanvasComponentEntity c = components.get(i);
            sb.append("        // Widget #").append(i + 1).append(": ")
                    .append(c.getLabel()).append(" [").append(c.getType()).append("]\n");
            sb.append("        // Target: ").append(c.getTargetFilePath())
                    .append(" | Offset: ").append(c.getByteOffsetHex())
                    .append(" | ON=").append(c.getOnPayloadHex())
                    .append(" | OFF=").append(c.getOffPayloadHex()).append("\n");
            sb.append("        // ON Sound: ").append(c.getSoundTrigger())
                    .append(" | OFF Sound: ").append(c.getOffSoundTrigger()).append("\n");
            if ("TOGGLE".equals(c.getType())) {
                sb.append("        Switch widget_").append(i).append(" = new Switch(this);\n");
                sb.append("        widget_").append(i).append(".setText(\"").append(escapeJava(c.getLabel())).append("\");\n");
                sb.append("        widget_").append(i).append(".setOnCheckedChangeListener((btn, isChecked) -> {\n");
                sb.append("            BinaryOffsetPatcher.executeConfiguredComponentWrite(getFilesDir(), ").append(i).append(", isChecked);\n");
                sb.append("        });\n");
                sb.append("        root.addView(widget_").append(i).append(");\n\n");
            } else {
                sb.append("        Button widget_").append(i).append(" = new Button(this);\n");
                sb.append("        widget_").append(i).append(".setText(\"").append(escapeJava(c.getLabel())).append("\");\n");
                sb.append("        final boolean[] state_").append(i).append(" = new boolean[]{false};\n");
                sb.append("        widget_").append(i).append(".setOnClickListener(v -> {\n");
                sb.append("            state_").append(i).append("[0] = !state_").append(i).append("[0];\n");
                sb.append("            BinaryOffsetPatcher.executeConfiguredComponentWrite(getFilesDir(), ").append(i).append(", state_").append(i).append("[0]);\n");
                sb.append("        });\n");
                sb.append("        root.addView(widget_").append(i).append(");\n\n");
            }
        }

        sb.append("        floatingRoot = root;\n");
        sb.append("        if (windowManager != null) {\n");
        sb.append("            windowManager.addView(floatingRoot, lp);\n");
        sb.append("        }\n");
        sb.append("    }\n\n");
        sb.append("    @Override\n");
        sb.append("    public void onDestroy() {\n");
        sb.append("        if (floatingRoot != null && windowManager != null) {\n");
        sb.append("            windowManager.removeView(floatingRoot);\n");
        sb.append("            floatingRoot = null;\n");
        sb.append("        }\n");
        sb.append("        super.onDestroy();\n");
        sb.append("    }\n\n");
        sb.append("    @Override\n");
        sb.append("    public IBinder onBind(Intent intent) {\n");
        sb.append("        return null;\n");
        sb.append("    }\n");
        sb.append("}\n");
        return sb.toString();
    }

    @NonNull
    private static String generateActivityMainXml(@NonNull StudioProjectEntity project) {
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                + "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "    android:layout_width=\"match_parent\"\n"
                + "    android:layout_height=\"match_parent\"\n"
                + "    android:orientation=\"vertical\"\n"
                + "    android:background=\"#E2E8F0\">\n"
                + "    <LinearLayout\n"
                + "        android:layout_width=\"match_parent\"\n"
                + "        android:layout_height=\"56dp\"\n"
                + "        android:background=\"#0288D1\"\n"
                + "        android:gravity=\"center_vertical\"\n"
                + "        android:orientation=\"horizontal\"\n"
                + "        android:padding=\"8dp\">\n"
                + "        <TextView\n"
                + "            android:layout_width=\"0dp\"\n"
                + "            android:layout_height=\"wrap_content\"\n"
                + "            android:layout_weight=\"1\"\n"
                + "            android:text=\"" + escapeXml(project.getName()) + "\"\n"
                + "            android:textColor=\"#FFFFFF\"\n"
                + "            android:textSize=\"16sp\"\n"
                + "            android:textStyle=\"bold\" />\n"
                + "        <Button\n"
                + "            android:id=\"@+id/btn_float_overlay\"\n"
                + "            android:layout_width=\"wrap_content\"\n"
                + "            android:layout_height=\"38dp\"\n"
                + "            android:text=\"Float\" />\n"
                + "    </LinearLayout>\n"
                + "    <FrameLayout\n"
                + "        android:id=\"@+id/empty_canvas_workspace_host\"\n"
                + "        android:layout_width=\"match_parent\"\n"
                + "        android:layout_height=\"0dp\"\n"
                + "        android:layout_weight=\"1\" />\n"
                + "    <FrameLayout\n"
                + "        android:id=\"@+id/bottom_inspector_dock_host\"\n"
                + "        android:layout_width=\"match_parent\"\n"
                + "        android:layout_height=\"wrap_content\" />\n"
                + "</LinearLayout>\n";
    }

    @NonNull
    private static String generateInspectorDockXml() {
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                + "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "    android:layout_width=\"match_parent\"\n"
                + "    android:layout_height=\"wrap_content\"\n"
                + "    android:orientation=\"vertical\"\n"
                + "    android:background=\"#FFFFFF\"\n"
                + "    android:padding=\"10dp\">\n"
                + "    <EditText\n"
                + "        android:id=\"@+id/et_component_label\"\n"
                + "        android:layout_width=\"match_parent\"\n"
                + "        android:layout_height=\"wrap_content\"\n"
                + "        android:hint=\"Component Label\" />\n"
                + "    <EditText\n"
                + "        android:id=\"@+id/et_target_file_path\"\n"
                + "        android:layout_width=\"match_parent\"\n"
                + "        android:layout_height=\"wrap_content\"\n"
                + "        android:hint=\"Target File Path\" />\n"
                + "    <EditText\n"
                + "        android:id=\"@+id/et_byte_offset_hex\"\n"
                + "        android:layout_width=\"match_parent\"\n"
                + "        android:layout_height=\"wrap_content\"\n"
                + "        android:hint=\"Byte Offset Hex (0x04)\" />\n"
                + "    <EditText\n"
                + "        android:id=\"@+id/et_on_payload_hex\"\n"
                + "        android:layout_width=\"match_parent\"\n"
                + "        android:layout_height=\"wrap_content\"\n"
                + "        android:hint=\"ON Value (0x01)\" />\n"
                + "    <EditText\n"
                + "        android:id=\"@+id/et_off_payload_hex\"\n"
                + "        android:layout_width=\"match_parent\"\n"
                + "        android:layout_height=\"wrap_content\"\n"
                + "        android:hint=\"OFF Value (0x00)\" />\n"
                + "</LinearLayout>\n";
    }

    @NonNull
    private static String generateFloatingViewXml(
            @NonNull StudioProjectEntity project,
            @NonNull List<CanvasComponentEntity> components
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
        sb.append("<!-- Dynamically generated Floating View XML for ")
                .append(escapeXml(project.getOverlayTitle()))
                .append(" (").append(project.getCanvasWidthDp()).append("dp x ")
                .append(project.getCanvasHeightDp()).append("dp) -->\n");
        sb.append("<FrameLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n");
        sb.append("    android:id=\"@+id/floating_canvas_frame\"\n");
        sb.append("    android:layout_width=\"").append(project.getCanvasWidthDp()).append("dp\"\n");
        sb.append("    android:layout_height=\"").append(project.getCanvasHeightDp()).append("dp\"\n");
        sb.append("    android:background=\"").append(escapeXml(project.getCanvasBgColorHex())).append("\">\n");

        for (CanvasComponentEntity comp : components) {
            String xmlTag;
            switch (comp.getType()) {
                case "TOGGLE":
                    xmlTag = "Switch";
                    break;
                case "BUTTON":
                    xmlTag = "Button";
                    break;
                case "SLIDER":
                    xmlTag = "SeekBar";
                    break;
                case "INPUT":
                    xmlTag = "EditText";
                    break;
                case "IMAGE":
                    xmlTag = "ImageView";
                    break;
                default:
                    xmlTag = "TextView";
                    break;
            }
            sb.append("    <").append(xmlTag).append("\n");
            sb.append("        android:id=\"@+id/widget_").append(comp.getId()).append("\"\n");
            sb.append("        android:layout_width=\"").append(comp.getWidthDp()).append("dp\"\n");
            sb.append("        android:layout_height=\"").append(comp.getHeightDp()).append("dp\"\n");
            sb.append("        android:layout_marginStart=\"").append(comp.getPosXDp()).append("dp\"\n");
            sb.append("        android:layout_marginTop=\"").append(comp.getPosYDp()).append("dp\"\n");
            if (!"IMAGE".equals(comp.getType()) && !"SLIDER".equals(comp.getType())) {
                sb.append("        android:text=\"").append(escapeXml(comp.getLabel())).append("\"\n");
                sb.append("        android:textColor=\"").append(escapeXml(comp.getTextColorHex())).append("\"\n");
            }
            sb.append("        android:background=\"").append(escapeXml(comp.getBgColorHex())).append("\"\n");
            sb.append("        android:tag=\"").append(escapeXml(comp.getByteOffsetHex()))
                    .append("|").append(escapeXml(comp.getOnPayloadHex()))
                    .append("|").append(escapeXml(comp.getOffPayloadHex())).append("\" />\n");
        }
        sb.append("</FrameLayout>\n");
        return sb.toString();
    }

    @NonNull
    private static String generateOverlayConfigJson(
            @NonNull StudioProjectEntity project,
            @NonNull List<CanvasComponentEntity> components
    ) {
        boolean hasLogo = project.getAppLogoPath() != null
                && !project.getAppLogoPath().trim().isEmpty()
                && new File(project.getAppLogoPath().trim()).exists();
        boolean hasFloatingLogo = project.getFloatingLogoPath() != null
                && !project.getFloatingLogoPath().trim().isEmpty()
                && new File(project.getFloatingLogoPath().trim()).exists();
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"standaloneCompiledApp\": true,\n");
        sb.append("  \"packageName\": \"").append(escapeJava(getCompiledAppPackageName(project))).append("\",\n");
        sb.append("  \"projectName\": \"").append(escapeJava(project.getName())).append("\",\n");
        sb.append("  \"workspaceProjectName\": \"").append(escapeJava(project.getProjectName())).append("\",\n");
        sb.append("  \"versionCode\": ").append(project.getVersionCode()).append(",\n");
        sb.append("  \"versionName\": \"").append(escapeJava(project.getVersionName())).append("\",\n");
        sb.append("  \"minSdk\": ").append(project.getMinSdk()).append(",\n");
        sb.append("  \"targetSdk\": ").append(project.getTargetSdk()).append(",\n");
        sb.append("  \"overlayTitle\": \"").append(escapeJava(project.getOverlayTitle())).append("\",\n");
        sb.append("  \"appLogoAsset\": \"").append(hasLogo ? "app_logo.png" : "").append("\",\n");
        sb.append("  \"floatingLogoAsset\": \"").append(hasFloatingLogo ? "floating_logo.png" : "").append("\",\n");
        sb.append("  \"canvasWidthDp\": ").append(project.getCanvasWidthDp()).append(",\n");
        sb.append("  \"canvasHeightDp\": ").append(project.getCanvasHeightDp()).append(",\n");
        sb.append("  \"canvasBgColorHex\": \"").append(escapeJava(project.getCanvasBgColorHex())).append("\",\n");
        sb.append("  \"autoFixSize\": ").append(project.getAutoFixSize()).append(",\n");
        sb.append("  \"components\": [\n");
        for (int i = 0; i < components.size(); i++) {
            CanvasComponentEntity c = components.get(i);
            boolean hasCustomImg = c.getCustomImagePath() != null && !c.getCustomImagePath().trim().isEmpty()
                    && new File(c.getCustomImagePath().trim()).exists();
            boolean hasOnSnd = c.getCustomSoundPath() != null && !c.getCustomSoundPath().trim().isEmpty()
                    && new File(c.getCustomSoundPath().trim()).exists();
            boolean hasOffSnd = c.getOffCustomSoundPath() != null && !c.getOffCustomSoundPath().trim().isEmpty()
                    && new File(c.getOffCustomSoundPath().trim()).exists();
            sb.append("    {")
                    .append("\"id\": ").append(c.getId()).append(", ")
                    .append("\"type\": \"").append(escapeJava(c.getType())).append("\", ")
                    .append("\"label\": \"").append(escapeJava(c.getLabel())).append("\", ")
                    .append("\"x\": ").append(c.getPosXDp()).append(", ")
                    .append("\"y\": ").append(c.getPosYDp()).append(", ")
                    .append("\"width\": ").append(c.getWidthDp()).append(", ")
                    .append("\"height\": ").append(c.getHeightDp()).append(", ")
                    .append("\"bgHex\": \"").append(escapeJava(c.getBgColorHex())).append("\", ")
                    .append("\"textHex\": \"").append(escapeJava(c.getTextColorHex())).append("\", ")
                    .append("\"onSound\": \"").append(escapeJava(c.getSoundTrigger())).append("\", ")
                    .append("\"offSound\": \"").append(escapeJava(c.getOffSoundTrigger())).append("\", ")
                    .append("\"customImageAsset\": \"").append(hasCustomImg ? "images/img_" + c.getId() + ".jpg" : "").append("\", ")
                    .append("\"onSoundAsset\": \"").append(hasOnSnd ? "sounds/on_snd_" + c.getId() + ".mp3" : "").append("\", ")
                    .append("\"offSoundAsset\": \"").append(hasOffSnd ? "sounds/off_snd_" + c.getId() + ".mp3" : "").append("\", ")
                    .append("\"targetFile\": \"").append(escapeJava(c.getTargetFilePath())).append("\", ")
                    .append("\"offsetHex\": \"").append(escapeJava(c.getByteOffsetHex())).append("\", ")
                    .append("\"onHex\": \"").append(escapeJava(c.getOnPayloadHex())).append("\", ")
                    .append("\"offHex\": \"").append(escapeJava(c.getOffPayloadHex())).append("\", ")
                    .append("\"sliderMax\": ").append(c.getSliderMax()).append(", ")
                    .append("\"currentValue\": \"").append(escapeJava(c.getCurrentValue())).append("\", ")
                    .append("\"linkUrl\": \"").append(escapeJava(c.getLinkUrl() != null ? c.getLinkUrl() : "")).append("\"")
                    .append("}").append(i < components.size() - 1 ? ",\n" : "\n");
        }
        sb.append("  ]\n");
        sb.append("}\n");
        return sb.toString();
    }

    @NonNull
    private static String sanitizeId(@NonNull String raw) {
        String s = raw.trim().toLowerCase(Locale.US).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (s.isEmpty() || Character.isDigit(s.charAt(0))) {
            return "mod_" + s;
        }
        return s;
    }

    @NonNull
    private static String escapeXml(@NonNull String input) {
        return input
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    @NonNull
    private static String escapeJava(@NonNull String input) {
        return input
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "");
    }
}
