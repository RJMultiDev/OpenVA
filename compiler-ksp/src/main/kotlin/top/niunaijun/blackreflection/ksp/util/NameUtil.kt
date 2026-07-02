package top.niunaijun.blackreflection.ksp.util

import com.google.devtools.ksp.symbol.KSClassDeclaration

internal fun KSClassDeclaration.pkgOf(): String = packageName.asString()

internal fun KSClassDeclaration.simpleOf(): String = simpleName.asString()

/** Strip the package prefix and join remaining parts to form a flat simple class name. */
internal fun stripPackage(fqcn: String, pkg: String): String {
    val s = if (fqcn.startsWith(pkg + ".")) fqcn.substring(pkg.length + 1) else fqcn
    return s.replace(".", "")
}
