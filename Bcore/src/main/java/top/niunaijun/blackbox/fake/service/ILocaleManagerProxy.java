package top.niunaijun.blackbox.fake.service;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import black.android.app.BRILocaleManagerStub;
import black.android.os.BRServiceManager;
import top.niunaijun.blackbox.fake.hook.BinderInvocationStub;
import top.niunaijun.blackbox.fake.hook.MethodHook;
import top.niunaijun.blackbox.fake.hook.ProxyMethod;
import top.niunaijun.blackbox.utils.Slog;


/**
 * Proxy for {@code android.app.ILocaleManager} (Android 13+ / API 33).
 *
 * <p>When a sandboxed app (e.g. Google Play Store) calls
 * {@link android.app.LocaleManager#setApplicationLocales}, the system service
 * resolves the calling UID for {@code appPackageName} and, when it does not
 * match the caller's actual UID, enforces
 * {@code android.permission.CHANGE_CONFIGURATION}. The sandbox process runs
 * under the BlackBox host UID, which never holds that signature-priv
 * permission, so every call lands in:
 *
 * <pre>
 *   java.lang.SecurityException: setApplicationLocales: Neither user &lt;N&gt;
 *       nor current process has android.permission.CHANGE_CONFIGURATION.
 * </pre>
 *
 * <p>The exception still surfaces in the app's crash reporter even when the
 * app catches it. Since the sandbox manages its own per-app locale state via
 * the existing configuration overrides anyway, we swallow the
 * {@link SecurityException} for {@code setApplicationLocales} and
 * {@code setOverrideLocaleConfig} (both gated by the same permission check),
 * letting the call no-op cleanly.
 */
public class ILocaleManagerProxy extends BinderInvocationStub {
    public static final String TAG = "ILocaleManagerProxy";
    private static final String SERVICE = "locale";

    public ILocaleManagerProxy() {
        super(BRServiceManager.get().getService(SERVICE));
    }

    @Override
    protected Object getWho() {
        return BRILocaleManagerStub.get().asInterface(BRServiceManager.get().getService(SERVICE));
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        replaceSystemService(SERVICE);
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }

    @ProxyMethod("setApplicationLocales")
    public static class SetApplicationLocales extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return swallowChangeConfiguration(who, method, args);
        }
    }

    @ProxyMethod("setOverrideLocaleConfig")
    public static class SetOverrideLocaleConfig extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return swallowChangeConfiguration(who, method, args);
        }
    }

    private static Object swallowChangeConfiguration(Object who, Method method, Object[] args)
            throws Throwable {
        try {
            return method.invoke(who, args);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof SecurityException
                    && cause.getMessage() != null
                    && cause.getMessage().contains("CHANGE_CONFIGURATION")) {
                Slog.w(TAG, method.getName()
                        + ": swallowed CHANGE_CONFIGURATION SecurityException");
                return null;
            }
            throw cause != null ? cause : e;
        }
    }
}
