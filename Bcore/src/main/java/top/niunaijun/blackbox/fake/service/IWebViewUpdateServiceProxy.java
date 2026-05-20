package top.niunaijun.blackbox.fake.service;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import black.android.os.BRServiceManager;
import black.android.webkit.BRIWebViewUpdateServiceStub;
import top.niunaijun.blackbox.fake.hook.BinderInvocationStub;
import top.niunaijun.blackbox.fake.hook.MethodHook;
import top.niunaijun.blackbox.fake.hook.ProxyMethod;
import top.niunaijun.blackbox.utils.Slog;


/**
 * Proxy for {@code android.webkit.IWebViewUpdateService} (system service binder
 * {@code "webviewupdate"}, present since API 22).
 *
 * <p>The previous implementation extended {@code ClassInvocationStub} with
 * {@code getWho()} returning {@code null}, which causes
 * {@link top.niunaijun.blackbox.fake.hook.ClassInvocationStub#injectHook()} to
 * bail out before registering any of its {@code @ProxyMethod} classes, so none
 * of the method hooks actually fired and the cloned app talked to the real
 * binder directly. This rewrite uses {@code BinderInvocationStub("webviewupdate")},
 * the same pattern as {@code ICredentialManagerProxy} / {@code ILocaleManagerProxy},
 * so the binder is swapped into the sandbox {@code ServiceManager} cache and
 * client {@code IWebViewUpdateService$Stub.asInterface()} calls return a proxy
 * that routes through this class.
 *
 * <p>The proxy keeps the cloned app talking to the host WebView provider but
 * adds defensive fallbacks so a transient lookup failure (which has been
 * observed on Android 13+ when the host WebView package is being updated)
 * does not surface as a crash in the cloned app.
 */
public class IWebViewUpdateServiceProxy extends BinderInvocationStub {
    public static final String TAG = "IWebViewUpdateServiceProxy";
    private static final String SERVICE = "webviewupdate";

    public IWebViewUpdateServiceProxy() {
        super(BRServiceManager.get().getService(SERVICE));
    }

    @Override
    protected Object getWho() {
        return BRIWebViewUpdateServiceStub.get().asInterface(
                BRServiceManager.get().getService(SERVICE));
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        replaceSystemService(SERVICE);
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }

    @ProxyMethod("waitForAndGetProvider")
    public static class WaitForAndGetProvider extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return passThroughOrNull(who, method, args);
        }
    }

    @ProxyMethod("getCurrentWebViewPackage")
    public static class GetCurrentWebViewPackage extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return passThroughOrNull(who, method, args);
        }
    }

    @ProxyMethod("getCurrentWebViewPackageName")
    public static class GetCurrentWebViewPackageName extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return passThroughOrNull(who, method, args);
        }
    }

    @ProxyMethod("getValidWebViewPackages")
    public static class GetValidWebViewPackages extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return passThroughOrNull(who, method, args);
        }
    }

    @ProxyMethod("isMultiProcessEnabled")
    public static class IsMultiProcessEnabled extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            try {
                return method.invoke(who, args);
            } catch (InvocationTargetException e) {
                Slog.w(TAG, "isMultiProcessEnabled failed, defaulting to false", e.getCause());
                return false;
            }
        }
    }

    
    private static Object passThroughOrNull(Object who, Method method, Object[] args)
            throws Throwable {
        try {
            return method.invoke(who, args);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            Slog.w(TAG, method.getName() + ": passing through as null", cause);
            return null;
        }
    }
}
