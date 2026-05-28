package black.android.app;

import top.niunaijun.blackreflection.annotation.BClassName;
import top.niunaijun.blackreflection.annotation.BStaticMethod;
import android.os.IBinder;
import android.os.IInterface;

@BClassName("android.app.ILocaleManager$Stub")
public interface ILocaleManagerStub {
    @BStaticMethod
    IInterface asInterface(IBinder obj);
}
