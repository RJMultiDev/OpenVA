package black.android.credentials;

import android.os.IBinder;
import android.os.IInterface;

import top.niunaijun.blackreflection.annotation.BClassName;
import top.niunaijun.blackreflection.annotation.BStaticMethod;

@BClassName("android.credentials.ICredentialManager")
public interface ICredentialManager {
    @BClassName("android.credentials.ICredentialManager$Stub")
    interface Stub {
        @BStaticMethod
        IInterface asInterface(IBinder IBinder0);
    }
}
