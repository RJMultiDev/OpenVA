package top.niunaijun.blackbox.fake.service;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;

import black.android.credentials.BRICredentialManagerStub;
import black.android.os.BRServiceManager;
import top.niunaijun.blackbox.fake.hook.BinderInvocationStub;
import top.niunaijun.blackbox.fake.hook.MethodHook;
import top.niunaijun.blackbox.fake.hook.ProxyMethod;
import top.niunaijun.blackbox.utils.Slog;


/**
 * Proxy for {@code android.credentials.ICredentialManager} (Android 14+).
 *
 * <p>When a sandboxed app calls {@code CredentialManager.getCredential()} with a
 * {@link android.credentials.CredentialOption} that has a non-empty
 * {@code allowedProviders} set, the system server invokes
 * {@code CredentialManagerService.enforcePermissionForAllowedProviders()} which throws
 * {@link SecurityException} unless the caller holds the signature-priv permission
 * {@code android.permission.CREDENTIAL_MANAGER_SET_ALLOWED_PROVIDERS}. Because the
 * sandboxed app runs under the BlackBox host UID, it never has this permission and
 * the request crashes with:
 *
 * <pre>
 *   java.lang.SecurityException: uid &lt;N&gt; does not have
 *       android.permission.CREDENTIAL_MANAGER_SET_ALLOWED_PROVIDERS.
 * </pre>
 *
 * <p>The hook clears the {@code allowedProviders} set on every option (and on
 * {@code CreateCredentialRequest} for the create path) before forwarding, so the
 * server-side validation skips the permission check entirely. As a defensive
 * fallback any remaining {@link SecurityException} is swallowed so the original
 * call site does not propagate the crash.
 */
public class ICredentialManagerProxy extends BinderInvocationStub {
    public static final String TAG = "ICredentialManagerProxy";
    private static final String SERVICE = "credential";

    public ICredentialManagerProxy() {
        super(BRServiceManager.get().getService(SERVICE));
    }

    @Override
    protected Object getWho() {
        return BRICredentialManagerStub.get().asInterface(BRServiceManager.get().getService(SERVICE));
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        replaceSystemService(SERVICE);
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }

    @ProxyMethod("executeGetCredential")
    public static class ExecuteGetCredential extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return stripAndInvoke(who, method, args);
        }
    }

    @ProxyMethod("executePrepareGetCredential")
    public static class ExecutePrepareGetCredential extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return stripAndInvoke(who, method, args);
        }
    }

    @ProxyMethod("executeGetCandidateCredentials")
    public static class ExecuteGetCandidateCredentials extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return stripAndInvoke(who, method, args);
        }
    }

    @ProxyMethod("executeCreateCredential")
    public static class ExecuteCreateCredential extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return stripAndInvoke(who, method, args);
        }
    }

    private static Object stripAndInvoke(Object who, Method method, Object[] args) throws Throwable {
        if (args != null) {
            for (Object arg : args) {
                stripAllowedProvidersFromRequest(arg);
            }
        }
        try {
            return method.invoke(who, args);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof SecurityException
                    && cause.getMessage() != null
                    && cause.getMessage().contains("CREDENTIAL_MANAGER_SET_ALLOWED_PROVIDERS")) {
                Slog.w(TAG, method.getName()
                        + ": swallowed CREDENTIAL_MANAGER_SET_ALLOWED_PROVIDERS SecurityException");
                return null;
            }
            throw cause != null ? cause : e;
        }
    }

    private static void stripAllowedProvidersFromRequest(Object request) {
        if (request == null) return;
        String name = request.getClass().getName();
        try {
            if ("android.credentials.GetCredentialRequest".equals(name)) {
                Method getOptions = request.getClass().getMethod("getCredentialOptions");
                Object options = getOptions.invoke(request);
                if (options instanceof List) {
                    for (Object option : (List<?>) options) {
                        clearAllowedProviders(option);
                    }
                }
            } else if ("android.credentials.CreateCredentialRequest".equals(name)) {
                clearAllowedProviders(request);
            }
        } catch (Throwable t) {
            Slog.w(TAG, "stripAllowedProvidersFromRequest failed for " + name + ": " + t.getMessage());
        }
    }

    private static void clearAllowedProviders(Object target) {
        if (target == null) return;
        try {
            Field f = target.getClass().getDeclaredField("mAllowedProviders");
            f.setAccessible(true);
            Object value = f.get(target);
            if (value instanceof Collection) {
                Collection<?> col = (Collection<?>) value;
                if (!col.isEmpty()) {
                    col.clear();
                    Slog.d(TAG, "Cleared allowedProviders on " + target.getClass().getSimpleName());
                }
            }
        } catch (NoSuchFieldException ignored) {
            // Field naming may differ on a vendor build; nothing to do.
        } catch (Throwable t) {
            Slog.w(TAG, "Failed to clear allowedProviders on "
                    + target.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }
}
