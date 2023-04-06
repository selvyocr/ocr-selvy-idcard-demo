package com.selvasai.selvyocr.idcard.sample2.util;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.os.Build;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class Utils {
    private static ProgressDialog mDialog;

    /**
     * Progress Dialog를 입력된 값으로 표시.
     *
     * @param context Context
     * @param show    Dialog를 표시할지 여부, true - 표시
     * @param string  Dialog에 보일 문구
     */
    public static void progressDialog(Context context, boolean show, String string) {
        if (mDialog != null && mDialog.isShowing()) {
            mDialog.dismiss();
            mDialog = null;
        }

        if (show && context != null) {
            mDialog = ProgressDialog.show(context, null, string, true, false);
        }
    }

    public static void recycleBitmap(Bitmap bitmap) {
        if (bitmap != null && !bitmap.isRecycled()) {
            bitmap.recycle();
            bitmap = null;
        }
    }

    private static String getABIName() {
        final String[] abis = {"arm64-v8a", "armeabi-v7a"};

        String targetABI = null;
        for (String abi : abis) {
            for (String supportedABI : Build.SUPPORTED_ABIS) {
                if (supportedABI.equalsIgnoreCase(abi)) {
                    targetABI = abi;
                    break;
                }
            }

            if (targetABI != null) {
                break;
            }
        }

        if (targetABI == null) {
            targetABI = abis[1];
        }

        return targetABI;
    }

    public static boolean copyLibsFromAssets(Context context) {
        final String[] libs = {"libSelvyOCRforIdCard.so", "libSelvyOCRforIdCardml.so"};

        final String targetABI = getABIName();

        for (final String libName : libs) {
            final String assetPath = targetABI + "/" + libName;

            File file = new File(context.getFilesDir(), libName);
            if (!file.exists()) {
                if (!copyAsset(context, assetPath, libName)) {
                    return false;
                }
            } else {
                if (!checkSum(context, file, assetPath)) {
                    if (!copyAsset(context, assetPath, libName)) {
                        return false;
                    }
                }
            }
        }

        return true;
    }

    private static boolean checkSum(Context context, File file, String assetPath) {
        try {
            AssetManager am = context.getResources().getAssets();
            InputStream in = am.open(assetPath);

            int size = in.available();
            in.close();

            if (size == file.length()) {
                return true;
            }
        } catch (IOException e) {
            return false;
        }

        return false;
    }

    private static boolean copyAsset(Context context, String assetPath, String fileName) {
        try {
            AssetManager assetManager = context.getAssets();
            InputStream in = assetManager.open(assetPath);

            OutputStream out = context.openFileOutput(fileName, Context.MODE_PRIVATE);
            copyFile(in, out);
            in.close();

            out.flush();
            out.close();
        } catch (Exception e) {
            return false;
        }

        return true;
    }

    private static void copyFile(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[1024];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
    }
}
