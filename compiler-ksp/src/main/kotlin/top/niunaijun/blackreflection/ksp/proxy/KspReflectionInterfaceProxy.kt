package top.niunaijun.blackreflection.ksp.proxy

import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSValueParameter
import com.google.devtools.ksp.symbol.KSType
import com.squareup.javapoet.AnnotationSpec
import com.squareup.javapoet.ClassName
import com.squareup.javapoet.JavaFile
import com.squareup.javapoet.MethodSpec
import com.squareup.javapoet.ParameterSpec
import com.squareup.javapoet.TypeName
import com.squareup.javapoet.TypeSpec
import javax.lang.model.element.Modifier
import top.niunaijun.blackreflection.annotation.BClassNameNotProcess
import top.niunaijun.blackreflection.annotation.BConstructorNotProcess
import top.niunaijun.blackreflection.annotation.BFieldCheckNotProcess
import top.niunaijun.blackreflection.annotation.BFieldNotProcess
import top.niunaijun.blackreflection.annotation.BFieldSetNotProcess
import top.niunaijun.blackreflection.annotation.BMethodCheckNotProcess
import top.niunaijun.blackreflection.annotation.BParamClass
import top.niunaijun.blackreflection.annotation.BParamClassName
import top.niunaijun.blackreflection.ksp.toTypeName

/**
 * Generates one of the two interfaces per @BClass/@BClassName-annotated
 * interface (Context and Static variants).
 *
 *     @BClassNameNotProcess("com.foo.Bar")
 *     public interface IBarContext {
 *         @BFieldNotProcess
 *         int x();
 *         void _set_x(Object value);
 *         Field _check_x();
 *         ...
 *     }
 */
class KspReflectionInterfaceProxy(
    private val pkg: String,
    private val targetClassName: String,
    val origClassName: String,
) {

    private val items = mutableListOf<KSFunctionDeclaration>()
    private val fields = mutableListOf<KSFunctionDeclaration>()
    private var realMap: Map<String, String> = emptyMap()

    /** Final Java class simple name (e.g. "IFooContext"). */
    val simpleName: String get() = targetClassName.substringAfterLast(".")

    /** Package the file is emitted to (taken from origClassName; matches JSR-269 version). */
    val emitPackage: String get() = realMap[origClassName]?.substringBeforeLast(".")
        ?: origClassName.substringBeforeLast(".")

    fun add(fn: KSFunctionDeclaration, isField: Boolean) {
        items.add(fn)
        if (isField) fields.add(fn)
    }

    fun setRealMap(m: Map<String, String>) {
        realMap = m
    }

    fun generateJavaFile(): JavaFile {
        val realName = realMap[origClassName] ?: origClassName
        val outPkg = realName.substringBeforeLast(".")

        val builder = TypeSpec.interfaceBuilder(targetClassName)
            .addAnnotation(
                AnnotationSpec.builder(BClassNameNotProcess::class.java)
                    .addMember("value", "\$S", realName)
                    .build()
            )
            .addModifiers(Modifier.PUBLIC)

        for (fn in items) {
            val isField = fields.contains(fn)
            builder.addMethod(buildAbstractMethod(fn, isField = isField))
            if (isField) {
                builder.addMethod(buildFieldSet(fn))
                builder.addMethod(buildFieldCheck(fn))
            } else {
                val isCtor = fn.annotations.any { it.shortName.asString() == "BConstructor" }
                if (!isCtor) {
                    builder.addMethod(buildMethodCheck(fn))
                }
            }
        }
        return JavaFile.builder(outPkg, builder.build()).build()
    }

    private fun buildAbstractMethod(fn: KSFunctionDeclaration, isField: Boolean): MethodSpec {
        val mb = MethodSpec.methodBuilder(fn.simpleName.asString())
            .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
        for (p in fn.parameters) mb.addParameter(buildParameter(p))
        val retType: TypeName = fn.returnType?.toTypeName() ?: TypeName.OBJECT
        mb.returns(retType.box())
        if (isField) {
            mb.addAnnotation(AnnotationSpec.builder(BFieldNotProcess::class.java).build())
        } else {
            val isCtor = fn.annotations.any { it.shortName.asString() == "BConstructor" }
            if (isCtor) {
                mb.addAnnotation(AnnotationSpec.builder(BConstructorNotProcess::class.java).build())
            }
        }
        return mb.build()
    }

    private fun buildFieldSet(fn: KSFunctionDeclaration): MethodSpec {
        val b = MethodSpec.methodBuilder("_set_" + fn.simpleName.asString())
            .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
            .addParameter(ClassName.get("java.lang", "Object"), "value", Modifier.FINAL)
            .addAnnotation(AnnotationSpec.builder(BFieldSetNotProcess::class.java).build())
        return b.build()
    }

    private fun buildFieldCheck(fn: KSFunctionDeclaration): MethodSpec {
        val b = MethodSpec.methodBuilder("_check_" + fn.simpleName.asString())
            .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
            .addAnnotation(AnnotationSpec.builder(BFieldCheckNotProcess::class.java).build())
            .returns(ClassName.get("java.lang.reflect", "Field"))
        return b.build()
    }

    private fun buildMethodCheck(fn: KSFunctionDeclaration): MethodSpec {
        val b = MethodSpec.methodBuilder("_check_" + fn.simpleName.asString())
            .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
            .addAnnotation(AnnotationSpec.builder(BMethodCheckNotProcess::class.java).build())
            .returns(ClassName.get("java.lang.reflect", "Method"))
        for (p in fn.parameters) b.addParameter(buildParameter(p))
        return b.build()
    }

    private fun buildParameter(p: KSValueParameter): ParameterSpec {
        val pName = p.name?.asString() ?: "p"
        val typeName: TypeName = p.type.toTypeName()
        val builder = ParameterSpec.builder(typeName, pName)
        for (ann in p.annotations) {
            val simple = ann.shortName.asString()
            if (simple == "BParamClassName") {
                builder.addAnnotation(annotationSpecFromKs(ann))
            } else if (simple == "BParamClass") {
                val arg = ann.arguments.firstOrNull { it.name?.asString() == "value" }
                val real = (arg?.value as? KSType)?.declaration
                if (real != null) {
                    val cn = ClassName.get(
                        real.packageName.asString(),
                        real.simpleName.asString()
                    )
                    builder.addAnnotation(
                        AnnotationSpec.builder(BParamClass::class.java)
                            .addMember("value", "\$T.class", cn)
                            .build()
                    )
                }
            }
        }
        return builder.build()
    }

    private fun annotationSpecFromKs(ann: KSAnnotation): AnnotationSpec {
        val resolved = ann.annotationType.resolve()
        val decl = resolved.declaration
        val pkgName = decl.packageName.asString()
        val simpleName = decl.simpleName.asString()
        val cn = ClassName.get(pkgName, simpleName)
        val builder = AnnotationSpec.builder(cn)
        for (a in ann.arguments) {
            val name = a.name?.asString()
            val v = a.value
            when (v) {
                is String -> {
                    if (name != null) builder.addMember(name, "\$S", v)
                    else builder.addMember("\$S", v)
                }
                is KSType -> {
                    val cn2 = ClassName.get(
                        v.declaration.packageName.asString(),
                        v.declaration.simpleName.asString()
                    )
                    val key = name ?: "value"
                    builder.addMember(key, "\$T.class", cn2)
                }
                else -> {
                    val s = v?.toString() ?: "null"
                    val key = name ?: "value"
                    builder.addMember(key, "\$L", s)
                }
            }
        }
        return builder.build()
    }
}