package top.niunaijun.blackbox.core;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.utils.Slog;

public class GmsPreloadManager {
    private static final String TAG = "GmsPreloadManager";
    private static final ExecutorService sExecutor = Executors.newSingleThreadExecutor();

    public static void init(int userId) {
        if (BlackBoxCore.get().isInstalled("com.google.android.gms", userId)) {
            Slog.d(TAG, "GMS is already installed for user " + userId + ", skipping preload.");
            return;
        }

        sExecutor.submit(() -> {
            try {
                Context context = BlackBoxCore.getContext();
                String[] apksToInstall = {
                        "gsf.apk",
                        "gms.apk",
                        "vending.apk"
                };

                for (String apkName : apksToInstall) {
                    File outFile = new File(context.getCacheDir(), apkName);

                    try (InputStream is = context.getAssets().open("gapps/" + apkName);
                         OutputStream os = new FileOutputStream(outFile)) {

                        byte[] buffer = new byte[8192];
                        int read;
                        while ((read = is.read(buffer)) != -1) {
                            os.write(buffer, 0, read);
                        }
                    } catch (Exception e) {
                        Slog.e(TAG, "Failed to extract " + apkName + " from assets", e);
                        continue;
                    }

                    Slog.d(TAG, "Installing " + apkName + " for user " + userId);
                    BlackBoxCore.get().installPackageAsUser(outFile, userId);


                    outFile.delete();
                }
                Slog.d(TAG, "GMS preload finished successfully for user " + userId);
            } catch (Exception e) {
                Slog.e(TAG, "GMS preload failed for user " + userId, e);
            }
        });
    }
}
