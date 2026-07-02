package top.niunaijun.blackbox.fake.service;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;

import java.io.File;
import java.lang.reflect.Method;

import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.app.BActivityThread;
import top.niunaijun.blackbox.fake.hook.MethodHook;
import top.niunaijun.blackbox.fake.hook.ProxyMethod;
import top.niunaijun.blackbox.fake.provider.FileProviderHandler;
import top.niunaijun.blackbox.utils.ComponentUtils;
import top.niunaijun.blackbox.utils.MethodParameterUtils;
import top.niunaijun.blackbox.utils.Slog;
import top.niunaijun.blackbox.utils.compat.BuildCompat;
import top.niunaijun.blackbox.utils.compat.StartActivityCompat;

import static android.content.pm.PackageManager.GET_META_DATA;


public class ActivityManagerCommonProxy {
    public static final String TAG = "CommonStub";

    @ProxyMethod("startActivity")
    public static class StartActivity extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            MethodParameterUtils.replaceFirstAppPkg(args);
            Intent intent = getIntent(args);
            Slog.d(TAG, "Hook in : " + intent);
            assert intent != null;

            if (maybeRouteSandboxAccountPicker(intent)) {
                return method.invoke(who, args);
            }


            if (intent.getParcelableExtra("_B_|_target_") != null) {
                return method.invoke(who, args);
            }
            if (ComponentUtils.isRequestInstall(intent)) {
                File file = FileProviderHandler.convertFile(BActivityThread.getApplication(), intent.getData());
                
                
                if (file != null && file.exists()) {
                    try {
                        PackageInfo packageInfo = BlackBoxCore.getPackageManager().getPackageArchiveInfo(file.getAbsolutePath(), 0);
                        if (packageInfo != null) {
                            String packageName = packageInfo.packageName;
                            String hostPackageName = BlackBoxCore.getHostPkg();
                            if (packageName.equals(hostPackageName)) {
                                Slog.w(TAG, "Blocked attempt to install BlackBox app from within BlackBox: " + packageName);
                                
                                return 0;
                            }
                        }
                    } catch (Exception e) {
                        Slog.w(TAG, "Could not verify if this is BlackBox app: " + e.getMessage());
                    }
                }
                
                if (BlackBoxCore.get().requestInstallPackage(file, BActivityThread.getUserId())) {
                    return 0;
                }
                intent.setData(FileProviderHandler.convertFileUri(BActivityThread.getApplication(), intent.getData()));
                return method.invoke(who, args);
            }
            String dataString = intent.getDataString();
            if (dataString != null && dataString.equals("package:" + BActivityThread.getAppPackageName())) {
                intent.setData(Uri.parse("package:" + BlackBoxCore.getHostPkg()));
            }

            if ("com.google.android.gms.auth".equals(intent.getAction()) ||
                "android.accounts.AccountAuthenticator".equals(intent.getAction())) {
                intent.setPackage("com.google.android.gms");
            } else if (android.provider.Settings.ACTION_ADD_ACCOUNT.equals(intent.getAction()) ||
                "android.settings.ADD_ACCOUNT_SETTINGS".equals(intent.getAction())) {
                String[] accountTypes = intent.getStringArrayExtra("account_types");
                if (accountTypes != null && accountTypes.length > 0) {
                    for (String type : accountTypes) {
                        if ("com.google".equals(type)) {
                            intent.setPackage("com.google.android.gms");
                            break;
                        }
                    }
                }
            }

            ResolveInfo resolveInfo = BlackBoxCore.getBPackageManager().resolveActivity(
                    intent,
                    GET_META_DATA,
                    StartActivityCompat.getResolvedType(args),
                    BActivityThread.getUserId());
            if (resolveInfo == null) {
                String origPackage = intent.getPackage();
                if (intent.getPackage() == null && intent.getComponent() == null) {
                    intent.setPackage(BActivityThread.getAppPackageName());
                } else {
                    origPackage = intent.getPackage();
                }
                resolveInfo = BlackBoxCore.getBPackageManager().resolveActivity(
                        intent,
                        GET_META_DATA,
                        StartActivityCompat.getResolvedType(args),
                        BActivityThread.getUserId());
                if (resolveInfo == null) {
                    intent.setPackage(origPackage);
                    String targetPkg = intent.getPackage();
                    if (targetPkg == null && intent.getComponent() != null) {
                        targetPkg = intent.getComponent().getPackageName();
                    }
                    if (targetPkg != null && (targetPkg.equals("com.google.android.gms") || targetPkg.equals("com.android.vending") || targetPkg.equals("com.google.android.gsf"))) {
                        Slog.w(TAG, "Suppressed unresolvable GMS activity start: " + intent);
                        return 0;
                    }
                    return method.invoke(who, args);
                }
            }


            intent.setExtrasClassLoader(who.getClass().getClassLoader());
            intent.setComponent(new ComponentName(resolveInfo.activityInfo.packageName, resolveInfo.activityInfo.name));

            // Bypass BAL restrictions by setting ActivityOptions
            if (android.os.Build.VERSION.SDK_INT >= 34) {
                Bundle options = StartActivityCompat.getOptions(args);
                android.app.ActivityOptions activityOptions;
                if (options == null) {
                    activityOptions = android.app.ActivityOptions.makeBasic();
                } else {
                    try {
                        Method fromBundle = android.app.ActivityOptions.class.getMethod("fromBundle", Bundle.class);
                        activityOptions = (android.app.ActivityOptions) fromBundle.invoke(null, options);
                    } catch (Throwable e) {
                        // Fallback if fromBundle is missing, which is unlikely but we don't want to crash.
                        // However, on API 34+ fromBundle is present (hidden or not, we might be able to call it via reflection).
                        // Another safe way is to just put the key into the bundle directly.
                        activityOptions = null;
                        options.putBoolean("android.pendingIntent.backgroundActivityAllowed", true);
                        options.putBoolean("android.pendingIntent.backgroundActivityAllowedByPermission", true);
                    }
                }

                if (activityOptions != null) {
                    try {
                        Method setPendingIntentBackgroundActivityStartMode = android.app.ActivityOptions.class.getMethod("setPendingIntentBackgroundActivityStartMode", int.class);
                        setPendingIntentBackgroundActivityStartMode.invoke(activityOptions, 1); // MODE_BACKGROUND_ACTIVITY_START_ALLOWED = 1
                    } catch (Throwable e) {
                    }
                    options = activityOptions.toBundle();
                }

                int optionsIndex = StartActivityCompat.getOptionsIndex();
                if (optionsIndex >= 0 && optionsIndex < args.length) {
                    args[optionsIndex] = options;
                }
            }

            BlackBoxCore.getBActivityManager().startActivityAms(BActivityThread.getUserId(),
                    StartActivityCompat.getIntent(args),
                    StartActivityCompat.getResolvedType(args),
                    StartActivityCompat.getResultTo(args),
                    StartActivityCompat.getResultWho(args),
                    StartActivityCompat.getRequestCode(args),
                    StartActivityCompat.getFlags(args),
                    StartActivityCompat.getOptions(args));
            return 0;
        }

        private Intent getIntent(Object[] args) {
            int index;
            if (BuildCompat.isR()) {
                index = 3;
            } else {
                index = 2;
            }
            if (args[index] instanceof Intent) {
                return (Intent) args[index];
            }
            for (Object arg : args) {
                if (arg instanceof Intent) {
                    return (Intent) arg;
                }
            }
            return null;
        }

        /**
         * Detect Android's system ChooseTypeAndAccountActivity / ChooseAccountActivity and
         * redirect to a sandbox-aware picker so callers see accounts from BAccountManagerService
         * (their sandbox) rather than the host's AccountManager.
         *
         * Returns true when the intent was rewritten and should be forwarded to the host AM as-is.
         */
        private boolean maybeRouteSandboxAccountPicker(Intent intent) {
            if (intent == null) {
                return false;
            }
            ComponentName component = intent.getComponent();
            if (component == null) {
                return false;
            }
            if (!"android".equals(component.getPackageName())) {
                return false;
            }
            String className = component.getClassName();
            if (className == null) {
                return false;
            }
            boolean isPicker = className.equals("android.accounts.ChooseTypeAndAccountActivity")
                    || className.equals("android.accounts.ChooseAccountActivity")
                    || className.endsWith(".ChooseTypeAndAccountActivity")
                    || className.endsWith(".ChooseAccountActivity");

            // Prevent looping: if we already replaced it or it's calling GMS account picker
            if ("com.google.android.gms.common.account.AccountPickerActivity".equals(className) ||
                "com.google.android.gms".equals(intent.getPackage()) ||
                "rj.openva".equals(intent.getPackage())) {
                return false;
            }
            if (!isPicker) {
                return false;
            }
            // Only intercept when called from inside a sandboxed app process. The host
            // BlackBox UI may legitimately need to open the system picker.
            if (BActivityThread.getAppConfig() == null) {
                return false;
            }
            int userId = BActivityThread.getUserId();
            if (userId < 0) {
                return false;
            }
            try {
                String callingPkg = BActivityThread.getAppPackageName();
                if (callingPkg == null) {
                    callingPkg = "";
                }
                intent.setComponent(new ComponentName(
                        BlackBoxCore.getHostPkg(),
                        "top.niunaijun.blackboxa.view.account.SandboxAccountPickerActivity"));
                intent.setPackage(null);
                intent.putExtra("openva.sandbox.userId", userId);
                intent.putExtra("openva.sandbox.callingPkg", callingPkg);
                Slog.d(TAG, "Substituting system AccountPicker with sandbox picker for user "
                        + userId + " caller=" + callingPkg);
                return true;
            } catch (Throwable t) {
                Slog.w(TAG, "maybeRouteSandboxAccountPicker failed, letting original intent through: "
                        + t.getMessage());
                return false;
            }
        }
    }

    @ProxyMethod("startActivities")
    public static class StartActivities extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            int index = getIntents();
            Intent[] intents = (Intent[]) args[index++];
            String[] resolvedTypes = (String[]) args[index++];
            IBinder resultTo = (IBinder) args[index++];
            Bundle options = (Bundle) args[index];
            
            if (!ComponentUtils.isSelf(intents)) {
                return method.invoke(who, args);
            }

            for (Intent intent : intents) {
                intent.setExtrasClassLoader(who.getClass().getClassLoader());
            }
            return BlackBoxCore.getBActivityManager().startActivities(BActivityThread.getUserId(),
                    intents, resolvedTypes, resultTo, options);
        }

        public int getIntents() {
            if (BuildCompat.isR()) {
                return 3;
            }
            return 2;
        }
    }

    @ProxyMethod("startIntentSenderForResult")
    public static class StartIntentSenderForResult extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("activityResumed")
    public static class ActivityResumed extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            BlackBoxCore.getBActivityManager().onActivityResumed((IBinder) args[0]);
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("activityDestroyed")
    public static class ActivityDestroyed extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            BlackBoxCore.getBActivityManager().onActivityDestroyed((IBinder) args[0]);
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("finishActivity")
    public static class FinishActivity extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            BlackBoxCore.getBActivityManager().onFinishActivity((IBinder) args[0]);
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("getAppTasks")
    public static class GetAppTasks extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            MethodParameterUtils.replaceFirstAppPkg(args);
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("getCallingPackage")
    public static class getCallingPackage extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return BlackBoxCore.getBActivityManager().getCallingPackage((IBinder) args[0], BActivityThread.getUserId());
        }
    }

    @ProxyMethod("getCallingActivity")
    public static class getCallingActivity extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return BlackBoxCore.getBActivityManager().getCallingActivity((IBinder) args[0], BActivityThread.getUserId());
        }
    }
}
