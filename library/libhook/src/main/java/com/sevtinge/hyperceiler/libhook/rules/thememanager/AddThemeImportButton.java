/*
 * This file is part of HyperCeiler.
 *
 * HyperCeiler is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 * Copyright (C) 2023-2026 HyperCeiler Contributions
 */
package com.sevtinge.hyperceiler.libhook.rules.thememanager;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.sevtinge.hyperceiler.common.log.XposedLog;
import com.sevtinge.hyperceiler.libhook.base.BaseHook;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import io.github.lingqiqi5211.ezhooktool.xposed.common.HookParam;
import io.github.lingqiqi5211.ezhooktool.xposed.java.IMethodHook;

/**
 * Adds a fixed "Import (.mtz) / استيراد" bottom button in ThemeTabActivity.
 * Solves:
 * 1. Overlapping with system navigation bar by applying dynamic WindowInsets navigation bar padding.
 * 2. Real MTZ parsing and theme indexing: extracts components, generates MIUI .mrm metadata,
 *    places sub-resources in .data/content/ and .data/preview/, and sends theme completion broadcast.
 */
public class AddThemeImportButton extends BaseHook {

    private static final int REQUEST_CODE_PICK_THEME = 0x4854; // "HT"

    @Override
    public void init() {
        Class<?> themeTabCls = findClassIfExists("com.android.thememanager.activity.ThemeTabActivity");
        if (themeTabCls == null) {
            themeTabCls = findClassIfExists("com.android.thememanager.ThemeResourceTabActivity");
        }
        if (themeTabCls == null) return;

        // 1. Hook onCreate to inject bottom button
        findAndHookMethod(themeTabCls, "onCreate", Bundle.class, new IMethodHook() {
            @Override
            public void after(HookParam param) {
                Activity activity = (Activity) param.getThisObject();
                new Handler(Looper.getMainLooper()).postDelayed(() -> addImportButton(activity), 300);
            }
        });

        // 2. Hook onActivityResult to handle chosen .mtz file
        findAndHookMethod(themeTabCls, "onActivityResult", int.class, int.class, Intent.class, new IMethodHook() {
            @Override
            public void before(HookParam param) {
                int requestCode = (int) param.getArgs()[0];
                int resultCode = (int) param.getArgs()[1];
                Intent data = (Intent) param.getArgs()[2];

                if (requestCode == REQUEST_CODE_PICK_THEME && resultCode == Activity.RESULT_OK && data != null) {
                    Uri uri = data.getData();
                    Activity activity = (Activity) param.getThisObject();
                    if (uri != null && activity != null) {
                        handlePickedThemeUri(activity, uri);
                        param.setResult(null);
                    }
                }
            }
        });
    }

    private void addImportButton(Activity activity) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;

        try {
            ViewGroup decorView = (ViewGroup) activity.getWindow().getDecorView();
            if (decorView.findViewWithTag("hyperceiler_theme_import_btn") != null) return;

            Context ctx = activity;

            Button importBtn = new Button(ctx);
            importBtn.setTag("hyperceiler_theme_import_btn");
            importBtn.setText("Import (.mtz)");
            importBtn.setTextColor(Color.WHITE);
            importBtn.setTextSize(16);
            importBtn.setElevation(16f);

            // Modern rounded pill drawable with HyperOS accent
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(Color.parseColor("#007AFF"));
            bg.setCornerRadius(60f);
            importBtn.setBackground(bg);

            int btnHeight = (int) (48 * ctx.getResources().getDisplayMetrics().density);
            int marginHoriz = (int) (32 * ctx.getResources().getDisplayMetrics().density);
            int baseMarginBottom = (int) (24 * ctx.getResources().getDisplayMetrics().density);

            // Calculate bottom inset (navigation bar height) to prevent overlapping
            int navBarHeight = 0;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                WindowInsets insets = activity.getWindow().getDecorView().getRootWindowInsets();
                if (insets != null) {
                    navBarHeight = insets.getInsets(WindowInsets.Type.navigationBars()).bottom;
                }
            }

            if (navBarHeight == 0) {
                int resourceId = ctx.getResources().getIdentifier("navigation_bar_height", "dimen", "android");
                if (resourceId > 0) {
                    navBarHeight = ctx.getResources().getDimensionPixelSize(resourceId);
                }
            }

            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    btnHeight
            );
            lp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
            lp.leftMargin = marginHoriz;
            lp.rightMargin = marginHoriz;
            lp.bottomMargin = baseMarginBottom + navBarHeight;

            importBtn.setLayoutParams(lp);

            // Dynamically listen for insets changes (e.g. keyboard or nav bar mode switch)
            ViewCompat.setOnApplyWindowInsetsListener(decorView, (v, insets) -> {
                int bottomInset = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
                FrameLayout.LayoutParams currentLp = (FrameLayout.LayoutParams) importBtn.getLayoutParams();
                if (currentLp != null) {
                    currentLp.bottomMargin = baseMarginBottom + bottomInset;
                    importBtn.setLayoutParams(currentLp);
                }
                return insets;
            });

            importBtn.setOnClickListener(v -> openFilePicker(activity));

            decorView.addView(importBtn);
            XposedLog.i(TAG, getPackageName(), "Successfully added import button to ThemeTabActivity with safe insets");
        } catch (Throwable t) {
            XposedLog.w(TAG, getPackageName(), "Failed to add import button", t);
        }
    }

    private void openFilePicker(Activity activity) {
        try {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("*/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            activity.startActivityForResult(Intent.createChooser(intent, "Select .mtz Theme"), REQUEST_CODE_PICK_THEME);
        } catch (Throwable t) {
            Toast.makeText(activity, "Failed to open file picker: " + t.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void handlePickedThemeUri(Activity activity, Uri uri) {
        Toast.makeText(activity, "Importing theme...", Toast.LENGTH_SHORT).show();

        new Thread(() -> {
            File tempMtz = null;
            try {
                // 1. Copy selected stream to a temp file
                File cacheDir = activity.getCacheDir();
                tempMtz = new File(cacheDir, "import_temp_" + System.currentTimeMillis() + ".mtz");

                try (InputStream in = activity.getContentResolver().openInputStream(uri);
                     FileOutputStream out = new FileOutputStream(tempMtz)) {
                    byte[] buffer = new byte[16384];
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        out.write(buffer, 0, read);
                    }
                }

                // 2. Perform real MIUI theme extraction & metadata generation
                String themeTitle = processAndInstallMtz(activity, tempMtz);

                new Handler(Looper.getMainLooper()).post(() -> {
                    Toast.makeText(activity, "Successfully imported: " + themeTitle, Toast.LENGTH_LONG).show();
                    // Recreate activity so ThemeTabActivity immediately lists the newly imported theme
                    activity.recreate();
                });
            } catch (Throwable t) {
                XposedLog.w(TAG, getPackageName(), "processAndInstallMtz failed", t);
                new Handler(Looper.getMainLooper()).post(() ->
                        Toast.makeText(activity, "Import failed: " + t.getMessage(), Toast.LENGTH_LONG).show());
            } finally {
                if (tempMtz != null && tempMtz.exists()) {
                    tempMtz.delete();
                }
            }
        }).start();
    }

    /**
     * Extracts .mtz archive, creates metadata .mrm, places sub-resource modules into .data/content/,
     * extracts previews to .data/preview/, and registers the theme into ThemeManager storage.
     */
    private String processAndInstallMtz(Activity activity, File mtzFile) throws Exception {
        File extFiles = activity.getExternalFilesDir(null);
        File themeBaseDir = new File(extFiles, "MIUI/theme/.data");

        File metaThemeDir = new File(themeBaseDir, "meta/theme");
        File contentThemeDir = new File(themeBaseDir, "content/theme");
        File previewThemeDir = new File(themeBaseDir, "preview/theme");
        File rightsThemeDir = new File(themeBaseDir, "rights/theme");

        metaThemeDir.mkdirs();
        contentThemeDir.mkdirs();
        previewThemeDir.mkdirs();
        rightsThemeDir.mkdirs();

        String themeUuid = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();

        String title = "Imported Theme";
        String author = "ThirdParty";
        String designer = "ThirdParty";
        String version = "1.0";
        String description = "Imported via HyperCeiler";

        List<JSONObject> subResources = new ArrayList<>();
        List<String> builtInThumbnails = new ArrayList<>();
        List<String> builtInPreviews = new ArrayList<>();

        File targetPreviewDir = new File(previewThemeDir, themeUuid);
        targetPreviewDir.mkdirs();

        byte[] mtzBytes = readFully(new FileInputStream(mtzFile));
        String sha1Hash = getSHA1(mtzBytes);

        // Process archive entries
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(mtzFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                if (entry.isDirectory()) {
                    continue;
                }

                if ("description.xml".equals(name)) {
                    byte[] descBytes = readStreamBytes(zis);
                    String xml = new String(descBytes, StandardCharsets.UTF_8);
                    title = extractXmlTag(xml, "title", title);
                    author = extractXmlTag(xml, "author", author);
                    designer = extractXmlTag(xml, "designer", designer);
                    version = extractXmlTag(xml, "version", version);
                    description = extractXmlTag(xml, "description", description);
                } else if (name.startsWith("preview/")) {
                    String previewFileName = new File(name).getName();
                    File previewOut = new File(targetPreviewDir, previewFileName);
                    writeToFile(previewOut, zis);

                    if (previewFileName.contains("small") || previewFileName.contains("thumbnail")) {
                        builtInThumbnails.add(previewFileName);
                    } else {
                        builtInPreviews.add(previewFileName);
                    }
                } else if (!name.contains("/")) {
                    // Top-level theme module component (e.g. icons, lockscreen, wallpaper, com.android.systemui)
                    String subUuid = UUID.randomUUID().toString();
                    File subContentDir = new File(themeBaseDir, "content/" + name);
                    subContentDir.mkdirs();
                    File subContentFile = new File(subContentDir, subUuid + ".mrc");
                    writeToFile(subContentFile, zis);

                    JSONObject subRes = new JSONObject();
                    subRes.put("localId", subUuid);
                    subRes.put("resourceCode", name);
                    subRes.put("extraMeta", new JSONObject());
                    subRes.put("metaPath", JSONObject.NULL);
                    subRes.put("contentPath", JSONObject.NULL);
                    subResources.add(subRes);

                    // Also generate corresponding .mrm in meta/<component>/ if it's a primary component
                    if ("icons".equals(name) || "lockscreen".equals(name) || "wallpaper".equals(name) || "launcher".equals(name)) {
                        try {
                            File subMetaDir = new File(themeBaseDir, "meta/" + name);
                            subMetaDir.mkdirs();
                            JSONObject subMrm = new JSONObject();
                            subMrm.put("localId", subUuid);
                            subMrm.put("onlineId", JSONObject.NULL);
                            subMrm.put("assemblyId", themeUuid);
                            subMrm.put("productId", themeUuid);
                            subMrm.put("hash", subUuid);
                            subMrm.put("platform", 10);
                            subMrm.put("size", subContentFile.length());
                            subMrm.put("updatedTime", now);
                            subMrm.put("version", version);

                            JSONObject subTitles = new JSONObject();
                            subTitles.put("fallback", title);
                            subTitles.put("en_US", title);
                            subMrm.put("titles", subTitles);

                            JSONObject subAuthors = new JSONObject();
                            subAuthors.put("fallback", author);
                            subAuthors.put("en_US", author);
                            subMrm.put("authors", subAuthors);

                            JSONObject subDesigners = new JSONObject();
                            subDesigners.put("fallback", designer);
                            subDesigners.put("en_US", designer);
                            subMrm.put("designers", subDesigners);

                            JSONObject subDesc = new JSONObject();
                            subDesc.put("fallback", "");
                            subMrm.put("descriptions", subDesc);

                            JSONObject subThumbs = new JSONObject();
                            subThumbs.put("fallback", new JSONArray(builtInThumbnails));
                            subMrm.put("builtInThumbnails", subThumbs);

                            JSONObject subPrev = new JSONObject();
                            subPrev.put("fallback", new JSONArray(builtInPreviews));
                            subMrm.put("builtInPreviews", subPrev);

                            subMrm.put("thumbnails", new JSONArray());
                            subMrm.put("previews", new JSONArray());

                            JSONArray parentArray = new JSONArray();
                            JSONObject parentObj = new JSONObject();
                            parentObj.put("localId", themeUuid);
                            parentObj.put("resourceCode", "theme");
                            parentObj.put("extraMeta", new JSONObject());
                            parentObj.put("metaPath", JSONObject.NULL);
                            parentObj.put("contentPath", JSONObject.NULL);
                            parentArray.put(parentObj);
                            subMrm.put("parentResources", parentArray);

                            subMrm.put("subResources", new JSONArray());
                            subMrm.put("extraMeta", new JSONObject());
                            subMrm.put("metaPath", JSONObject.NULL);
                            subMrm.put("contentPath", JSONObject.NULL);
                            subMrm.put("rightsPath", JSONObject.NULL);
                            subMrm.put("screenRatio", JSONObject.NULL);
                            subMrm.put("fontWeight", JSONObject.NULL);
                            subMrm.put("miuiAdapterVersion", 2.0);

                            File subMrmFile = new File(subMetaDir, subUuid + ".mrm");
                            try (FileOutputStream subFos = new FileOutputStream(subMrmFile)) {
                                subFos.write(subMrm.toString(2).getBytes(StandardCharsets.UTF_8));
                            }
                        } catch (Throwable t) {
                            XposedLog.w(TAG, getPackageName(), "Failed to create sub-mrm for " + name, t);
                        }
                    }
                }
            }
        }

        // If no explicit thumbnails found, use available previews
        if (builtInThumbnails.isEmpty() && !builtInPreviews.isEmpty()) {
            builtInThumbnails.add(builtInPreviews.get(0));
        }

        // 3. Construct .mrm JSON descriptor
        JSONObject mrm = new JSONObject();
        mrm.put("localId", themeUuid);
        mrm.put("onlineId", themeUuid);
        mrm.put("assemblyId", themeUuid);
        mrm.put("productId", themeUuid);
        mrm.put("hash", sha1Hash);
        mrm.put("platform", 10);
        mrm.put("size", mtzFile.length());
        mrm.put("updatedTime", now);
        mrm.put("version", version);

        JSONObject authorsObj = new JSONObject();
        authorsObj.put("fallback", author);
        authorsObj.put("en_US", author);
        authorsObj.put("zh_CN", author);
        mrm.put("authors", authorsObj);

        JSONObject designersObj = new JSONObject();
        designersObj.put("fallback", designer);
        designersObj.put("en_US", designer);
        designersObj.put("zh_CN", designer);
        mrm.put("designers", designersObj);

        JSONObject titlesObj = new JSONObject();
        titlesObj.put("fallback", title);
        titlesObj.put("en_US", title);
        titlesObj.put("zh_CN", title);
        mrm.put("titles", titlesObj);

        JSONObject descriptionsObj = new JSONObject();
        descriptionsObj.put("fallback", description);
        descriptionsObj.put("en_US", description);
        descriptionsObj.put("zh_CN", description);
        mrm.put("descriptions", descriptionsObj);

        JSONObject builtInThumbnailsObj = new JSONObject();
        builtInThumbnailsObj.put("fallback", new JSONArray(builtInThumbnails));
        mrm.put("builtInThumbnails", builtInThumbnailsObj);

        JSONObject builtInPreviewsObj = new JSONObject();
        builtInPreviewsObj.put("fallback", new JSONArray(builtInPreviews));
        mrm.put("builtInPreviews", builtInPreviewsObj);

        mrm.put("thumbnails", new JSONArray());
        mrm.put("previews", new JSONArray());
        mrm.put("parentResources", new JSONArray());
        mrm.put("subResources", new JSONArray(subResources));

        mrm.put("extraMeta", new JSONObject());
        mrm.put("metaPath", JSONObject.NULL);
        mrm.put("contentPath", JSONObject.NULL);
        mrm.put("rightsPath", JSONObject.NULL);
        mrm.put("screenRatio", JSONObject.NULL);
        mrm.put("supportHomeSearchBar", false);
        mrm.put("packageVersion", version);
        mrm.put("packageName", JSONObject.NULL);
        mrm.put("officialIcons", false);
        mrm.put("subResourceType", JSONObject.NULL);
        mrm.put("resSubType", JSONObject.NULL);
        mrm.put("isRearScreenEditable", false);
        mrm.put("supportAon", false);
        mrm.put("wallpaperStyle", 0);
        mrm.put("isSingleResource", false);
        mrm.put("iconsCount", JSONObject.NULL);
        mrm.put("fontWeight", JSONObject.NULL);
        mrm.put("price", -1);
        mrm.put("isBackUpVersion", false);
        mrm.put("themeType", 0);
        mrm.put("isRearScreenNeedLogin", false);
        mrm.put("miuiAdapterVersion", 2.0);

        // 4. Write .mrm file
        File mrmFile = new File(metaThemeDir, themeUuid + ".mrm");
        try (FileOutputStream fos = new FileOutputStream(mrmFile)) {
            fos.write(mrm.toString(2).getBytes(StandardCharsets.UTF_8));
        }

        // 5. Create 0-byte placeholder .mrc file in content/theme/
        File mrcFile = new File(contentThemeDir, themeUuid + ".mrc");
        mrcFile.createNewFile();

        // 6. Create valid dummy ODRL rights file (.mra) in rights/theme/ matching sha1Hash and themeUuid
        String rightsXml = "<o-ex:rights xmlns:o-ex=\"http://odrl.net/1.1/ODRL-EX\" " +
                "xmlns:o-dd=\"http://odrl.net/1.1/ODRL-DD\" " +
                "xmlns:oma-dd=\"http://www.openmobilealliance.com/oma-dd\" " +
                "xmlns:ds=\"http://www.w3.org/2000/09/xmldsig#\" " +
                "xmlns:xenc=\"http://www.w3.org/2001/04/xmlenc#\" " +
                "o-ex:id=\"hyperceiler_rights\"><o-ex:context><o-dd:version>2.1</o-dd:version>" +
                "<o-dd:uid>" + now + "</o-dd:uid></o-ex:context><o-ex:agreement><o-ex:asset>" +
                "<o-ex:context><o-dd:uid>" + themeUuid + "</o-dd:uid></o-ex:context><o-ex:digest>" +
                "<ds:DigestMethod Algorithm=\"nop\"/><ds:DigestValue/></o-ex:digest><ds:KeyInfo>" +
                "<xenc:EncryptedKey><xenc:EncryptionMethod Algorithm=\"nop\"/><ds:KeyInfo>" +
                "<ds:RetrievalMethod URI=\"nop\"/></ds:KeyInfo><xenc:CipherData><xenc:CipherValue/>" +
                "</xenc:CipherData></xenc:EncryptedKey></ds:KeyInfo></o-ex:asset><o-ex:permission>" +
                "<o-dd:display><o-ex:constraint><o-dd:count>0</o-dd:count></o-ex:constraint></o-dd:display>" +
                "<o-dd:execute><o-ex:constraint><oma-dd:individual><o-ex:context><o-dd:uid>m-1</o-dd:uid>" +
                "</o-ex:context></oma-dd:individual></o-ex:constraint></o-dd:execute><o-dd:export/>" +
                "</o-ex:permission></o-ex:agreement></o-ex:rights>";

        try {
            File mraFileUuid = new File(rightsThemeDir, themeUuid + ".mra");
            try (FileOutputStream mraFos = new FileOutputStream(mraFileUuid)) {
                mraFos.write(rightsXml.getBytes(StandardCharsets.UTF_8));
            }
            if (sha1Hash != null && !sha1Hash.isEmpty()) {
                File mraFileHash = new File(rightsThemeDir, sha1Hash + ".mra");
                try (FileOutputStream mraFos = new FileOutputStream(mraFileHash)) {
                    mraFos.write(rightsXml.getBytes(StandardCharsets.UTF_8));
                }
            }
        } catch (Throwable t) {
            XposedLog.w(TAG, getPackageName(), "Failed to write .mra rights file", t);
        }

        // 7. Broadcast resource import complete to ThemeManager
        Intent broadcast = new Intent("action_resource_import_complete");
        broadcast.setPackage("com.android.thememanager");
        broadcast.putExtra("extra_theme_uuid", themeUuid);
        broadcast.putExtra("extra_resource", themeUuid);
        broadcast.putExtra("extra_resource_id", themeUuid);
        activity.sendBroadcast(broadcast);

        XposedLog.i(TAG, getPackageName(), "Successfully installed theme: " + title + " (" + themeUuid + ")");
        return title;
    }

    private static String extractXmlTag(String xml, String tag, String defaultValue) {
        Pattern pattern = Pattern.compile("<" + tag + "[^>]*>(?:<!\\[CDATA\\[)?(.*?)(?:\\]\\]>)?<\\/" + tag + ">", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(xml);
        if (matcher.find()) {
            String val = matcher.group(1);
            if (val != null && !val.trim().isEmpty()) {
                return val.trim();
            }
        }
        return defaultValue;
    }

    private static byte[] readStreamBytes(InputStream is) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int n;
        while ((n = is.read(buffer)) != -1) {
            baos.write(buffer, 0, n);
        }
        return baos.toByteArray();
    }

    private static byte[] readFully(InputStream is) throws Exception {
        try (is) {
            return readStreamBytes(is);
        }
    }

    private static void writeToFile(File out, InputStream is) throws Exception {
        try (FileOutputStream fos = new FileOutputStream(out)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) != -1) {
                fos.write(buf, 0, n);
            }
        }
    }

    private static String getSHA1(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] digest = md.digest(data);
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return UUID.randomUUID().toString().replace("-", "");
        }
    }
}
