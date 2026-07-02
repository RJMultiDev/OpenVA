package top.niunaijun.blackreflection.ksp

import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeReference
import com.google.devtools.ksp.symbol.Nullability
import com.squareup.javapoet.ClassName
import com.squareup.javapoet.ParameterizedTypeName
import com.squareup.javapoet.TypeName

/**
 * KSType -> JavaPoet TypeName bridge.
 *
 * KSP does not ship a built-in bridge, so we walk declaration/arguments manually.
 * For primitive types we return the matching JavaPoet primitive TypeName (e.g. TypeName.INT).
 */
internal fun KSType.toTypeName(): TypeName {
    val decl = declaration
    if (decl is KSClassDeclaration) {
        val primitive = primitiveTypeName(decl.simpleName.asString())
        val pkg = decl.packageName.asString()
        val simple = decl.simpleName.asString()
        val raw: TypeName = if (primitive != null) {
            primitive
        } else {
            val base = if (pkg.isEmpty()) ClassName.get("", simple)
            else ClassName.get(pkg, simple)
            if (arguments.isEmpty()) base
            else ParameterizedTypeName.get(base, *arguments.map { arg ->
                val at = arg.type?.resolve()
                if (at == null) TypeName.OBJECT else at.toTypeName()
            }.toTypedArray())
        }
        return when (nullability) {
            Nullability.NULLABLE -> raw.box()
            else -> raw
        }
    }
    return TypeName.OBJECT
}

internal fun KSTypeReference.toTypeName(): TypeName = resolve().toTypeName()

private fun primitiveTypeName(simple: String): TypeName? = when (simple) {
    "int" -> TypeName.INT
    "long" -> TypeName.LONG
    "short" -> TypeName.SHORT
    "byte" -> TypeName.BYTE
    "char" -> TypeName.CHAR
    "float" -> TypeName.FLOAT
    "double" -> TypeName.DOUBLE
    "boolean" -> TypeName.BOOLEAN
    else -> null
}
