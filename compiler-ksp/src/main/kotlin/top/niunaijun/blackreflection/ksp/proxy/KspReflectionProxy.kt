package top.niunaijun.blackreflection.ksp.proxy

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.squareup.javapoet.ClassName
import com.squareup.javapoet.JavaFile
import com.squareup.javapoet.MethodSpec
import com.squareup.javapoet.TypeSpec
import javax.lang.model.element.Modifier
import top.niunaijun.blackreflection.ksp.model.KspReflectionInfo
import top.niunaijun.blackreflection.ksp.util.stripPackage

/**
 * Generates the `BR<ClassName>` utility class:
 *
 *     public final class BRClassName {
 *         public static IClassNameStatic get() { return BR.create(IClassNameStatic.class, null, false); }
 *         public static IClassNameStatic getWithException() { return BR.create(IClassNameStatic.class, null, true); }
 *         public static IClassNameContext get(Object caller) { return BR.create(IClassNameContext.class, caller, false); }
 *         public static IClassNameContext getWithException(Object caller) { return BR.create(IClassNameContext.class, caller, true); }
 *         public static Class<?> getRealClass() { return top.niunaijun.blackreflection.utils.ClassUtil.classReady(IClassNameContext.class); }
 *     }
 */
class KspReflectionProxy(
    private val info: KspReflectionInfo,
    private val decl: KSClassDeclaration,
) {

    private val mPackageName: String = decl.packageName.asString()

    private val mContextInterface: ClassName = ClassName.get(
        decl.packageName.asString(),
        decl.simpleName.asString() + "Context"
    )
    private val mStaticInterface: ClassName = ClassName.get(
        decl.packageName.asString(),
        decl.simpleName.asString() + "Static"
    )

    /** Emit package — the package of the BR class. */
    val emitPackage: String get() = mPackageName
    /** Final Java class simple name (e.g. "BRFooBar"). */
    val simpleName: String get() = "BR" + stripPackage(info.className, mPackageName)

    fun generateJavaFile(): JavaFile {
        val br = ClassName.get("top.niunaijun.blackreflection", "BlackReflection")
        val classSpec = TypeSpec.classBuilder(simpleName)
            .addModifiers(Modifier.PUBLIC)
            .addMethod(genGetMethod(true, mStaticInterface, br))
            .addMethod(genGetMethod(false, mStaticInterface, br))
            .addMethod(genGetCallerMethod(true, mContextInterface, br))
            .addMethod(genGetCallerMethod(false, mContextInterface, br))
            .addMethod(genGetRealClassMethod(mContextInterface))
            .build()
        return JavaFile.builder(mPackageName, classSpec).build()
    }

    private fun genGetMethod(withException: Boolean, ret: ClassName, br: ClassName): MethodSpec {
        val builder = MethodSpec.methodBuilder("get" + if (withException) "WithException" else "")
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .returns(ret)
        builder.addStatement(
            "return \$T.create(\$T.class, null, \$L)",
            br, ret, withException
        )
        return builder.build()
    }

    private fun genGetCallerMethod(withException: Boolean, ret: ClassName, br: ClassName): MethodSpec {
        val builder = MethodSpec.methodBuilder("get" + if (withException) "WithException" else "")
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .addParameter(ClassName.get("java.lang", "Object"), "caller", Modifier.FINAL)
            .returns(ret)
        builder.addStatement(
            "return \$T.create(\$T.class, caller, \$L)",
            br, ret, withException
        )
        return builder.build()
    }

    private fun genGetRealClassMethod(ret: ClassName): MethodSpec {
        val builder = MethodSpec.methodBuilder("getRealClass")
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .returns(ClassName.get(Class::class.java))
        builder.addStatement(
            "return top.niunaijun.blackreflection.utils.ClassUtil.classReady(\$T.class)",
            ret
        )
        return builder.build()
    }
}
