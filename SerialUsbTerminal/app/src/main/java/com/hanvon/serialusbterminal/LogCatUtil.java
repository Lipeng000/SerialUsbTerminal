package com.hanvon.serialusbterminal;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import java.io.File;
import java.util.concurrent.TimeUnit;

public class LogCatUtil {
    private static final String TAG = "DumpLog";

    public static File saveAndroidLog(Context ctx) {
        File logFile = new File(ctx.getExternalFilesDir(null), "logcat/" + System.currentTimeMillis() + ".log");
        return saveAndroidLog(logFile);
    }

    public static File saveAndroidLog(File logFile) {
        File dir = logFile.getParentFile();
        if (dir != null) {
            if (!dir.exists()) {
                if (!dir.mkdirs()) {
                    Log.e(TAG, "Failed to create directory on external storage for log: " + logFile.getAbsolutePath());
                }
            }
        }

        String[] cmd = {"logcat", "-d", "-v", "threadtime", "-f", "logcat.log"};
        cmd[cmd.length - 1] = logFile.getAbsolutePath();
        try {
            Process process = Runtime.getRuntime().exec(cmd);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                process.waitFor(10, TimeUnit.SECONDS);
            }
            return logFile;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
