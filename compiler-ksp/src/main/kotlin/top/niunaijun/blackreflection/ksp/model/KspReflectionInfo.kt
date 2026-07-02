package top.niunaijun.blackreflection.ksp.model

import com.google.devtools.ksp.symbol.KSClassDeclaration

/**
 * Holds the data for a @BClass / @BClassName-annotated interface.
 *
 * - [realClass]: the runtime class name the BlackReflection code will reflect into.
 * - [className]: the FQCN of the annotated interface (used as the key in the proxies map).
 */
class KspReflectionInfo(
    val className: String,
    val realClass: String,
)

/**
 * One element of a @BClass/@BClassName-annotated interface:
 * either a @BMethod / @BStaticMethod / @BField / @BStaticField / @BConstructor
 * annotated function/property.
 *
 * The KSP2 source declaration is retained so the proxy can read name/parameters/returnType.
 */
class KspInterfaceInfo(
    val classDecl: KSClassDeclaration,
    val isField: Boolean,
)
