package com.example.notify.utils;

import android.content.Context;
import android.content.res.AssetManager;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class AssetUtils {
    public static void copyAssets(Context context, String assetPath, String targetPath) throws IOException {
        AssetManager assetManager = context.getAssets();
        String[] assets = assetManager.list(assetPath);
        if (assets.length == 0) {
            copyFile(assetManager, assetPath, targetPath);
        } else {
            File targetDir = new File(targetPath);
            if (!targetDir.exists()) {
                targetDir.mkdirs();
            }
            for (String asset : assets) {
                String fullAssetPath = assetPath.isEmpty() ? asset : assetPath + "/" + asset;
                copyAssets(context, fullAssetPath, targetPath + "/" + asset);
            }
        }
    }

    private static void copyFile(AssetManager assetManager, String assetName, String targetPath) throws IOException {
        try (InputStream in = assetManager.open(assetName);
             OutputStream out = new FileOutputStream(targetPath)) {
            byte[] buffer = new byte[1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        }
    }
}
