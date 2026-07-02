package top.niunaijun.blackreflection.ksp

import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType
import top.niunaijun.blackreflection.ksp.model.KspReflectionInfo
import top.niunaijun.blackreflection.ksp.proxy.KspReflectionInterfaceProxy
import top.niunaijun.blackreflection.ksp.proxy.KspReflectionProxy

/**
 * KSP2 port of the JSR-269 BlackReflectionProcessor.
 *
 * For each @BClass / @BClassName interface, generate:
 *   - a `<Pkg>.BR<Fqcn>` entry class
 *   - a `<Pkg>.<Iface>Context` instance interface
 *   - a `<Pkg>.<Iface>Static` static interface
 *
 * For each @BMethod / @BStaticMethod / @BField / @BStaticField / @BConstructor
 * inside an @BClass/@BClassName interface, generate the matching Context/Static
 * abstract method plus `_set_` / `_check_` helpers (matching the JSR-269 version).
 */
class BlackReflectionKspProcessor(private val environment: SymbolProcessorEnvironment) : SymbolProcessor {

    private val blackReflectionProxies = mutableMapOf<String, KspReflectionProxy>()
    private val interfaceProxies = mutableMapOf<String, KspReflectionInterfaceProxy>()
    private val realMaps = mutableMapOf<String, String>()

    override fun process(resolver: Resolver): List<KSAnnotated> {
        // First pass: collect @BClass / @BClassName-annotated interfaces
        resolver.getSymbolsWithAnnotation(B_CLASS_FQCN)
            .toList()
            .filterIsInstance<KSClassDeclaration>()
            .forEach { decl ->
                val arg = decl.annotations.first { it.shortName.asString() == "BClass" }
                    .arguments.first { it.name?.asString() == "value" }
                val realName = ((arg.value as KSType).declaration as KSClassDeclaration)
                    .qualifiedName!!.asString()
                registerInterface(decl, realName)
            }

        resolver.getSymbolsWithAnnotation(B_CLASS_NAME_FQCN)
            .toList()
            .filterIsInstance<KSClassDeclaration>()
            .forEach { decl ->
                val ann = decl.annotations.first { it.shortName.asString() == "BClassName" }
                val realName = ann.arguments.first().value as String
                registerInterface(decl, realName)
            }

        // Second pass: collect member-level annotations
        for (fqcn in MEMBER_ANN_FQCNS) {
            val short = fqcn.substringAfterLast(".")
            val isStatic = short.startsWith("BStatic") || short == "BConstructor"
            val isField = short.endsWith("Field")
            resolver.getSymbolsWithAnnotation(fqcn)
                .toList()
                .filterIsInstance<KSFunctionDeclaration>()
                .forEach { fn ->
                    val owner = fn.parentDeclaration as? KSClassDeclaration ?: return@forEach
                    val ownerName = owner.qualifiedName?.asString() ?: return@forEach
                    if (owner.classKind != ClassKind.INTERFACE) return@forEach
                    if (!interfaceProxies.containsKey(ownerName + "Context")) return@forEach
                    val variantKey = if (isStatic) ownerName + "Static" else ownerName + "Context"
                    val interfaceProxy = interfaceProxies.getOrPut(variantKey) {
                        KspReflectionInterfaceProxy(
                            owner.packageName.asString(),
                            variantKey,
                            ownerName
                        )
                    }
                    interfaceProxy.add(fn, isField)
                }
        }

        // Third pass: emit Java files
        for ((_, proxy) in interfaceProxies) {
            proxy.setRealMap(realMaps)
            val out = environment.codeGenerator.createNewFile(
                Dependencies(false),
                proxy.emitPackage,
                proxy.simpleName + ".java",
                "java"
            )
            proxy.generateJavaFile().writeTo(out.writer().buffered())
            out.close()
        }
        for ((_, proxy) in blackReflectionProxies) {
            val out = environment.codeGenerator.createNewFile(
                Dependencies(false),
                proxy.emitPackage,
                proxy.simpleName + ".java",
                "java"
            )
            proxy.generateJavaFile().writeTo(out.writer().buffered())
            out.close()
        }

        return emptyList()
    }

    private fun registerInterface(decl: KSClassDeclaration, realName: String) {
        if (decl.classKind != ClassKind.INTERFACE) return
        val ownerName = decl.qualifiedName?.asString() ?: return
        val info = KspReflectionInfo(ownerName, realName)
        blackReflectionProxies.getOrPut(ownerName) { KspReflectionProxy(info, decl) }
        val contextVariant = ownerName + "Context"
        val staticVariant = ownerName + "Static"
        interfaceProxies.getOrPut(contextVariant) {
            KspReflectionInterfaceProxy(
                decl.packageName.asString(),
                contextVariant,
                ownerName
            )
        }
        interfaceProxies.getOrPut(staticVariant) {
            KspReflectionInterfaceProxy(
                decl.packageName.asString(),
                staticVariant,
                ownerName
            )
        }
        realMaps[ownerName] = realName
    }

    companion object {
        private const val B_CLASS_FQCN = "top.niunaijun.blackreflection.annotation.BClass"
        private const val B_CLASS_NAME_FQCN = "top.niunaijun.blackreflection.annotation.BClassName"

        private val MEMBER_ANN_FQCNS = listOf(
            "top.niunaijun.blackreflection.annotation.BMethod",
            "top.niunaijun.blackreflection.annotation.BStaticMethod",
            "top.niunaijun.blackreflection.annotation.BField",
            "top.niunaijun.blackreflection.annotation.BStaticField",
            "top.niunaijun.blackreflection.annotation.BConstructor",
        )
    }
}
