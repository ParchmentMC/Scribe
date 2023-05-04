/*
 * Minecraft Dev for IntelliJ
 *
 * https://minecraftdev.org
 *
 * Copyright (c) 2021 minecraft-dev
 *
 * MIT License
 */

package org.parchmentmc.scribe.util

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Key
import com.intellij.psi.JavaRecursiveElementWalkingVisitor
import com.intellij.psi.LambdaUtil
import com.intellij.psi.PsiAnonymousClass
import com.intellij.psi.PsiArrayType
import com.intellij.psi.PsiCapturedWildcardType
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiField
import com.intellij.psi.PsiLambdaExpression
import com.intellij.psi.PsiMember
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiPrimitiveType
import com.intellij.psi.PsiType
import com.intellij.psi.PsiTypes
import com.intellij.psi.PsiWildcardType
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.ParameterizedCachedValue
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.TypeConversionUtil
import it.unimi.dsi.fastutil.objects.Object2IntMap
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.MethodNode
import java.util.WeakHashMap

private const val INTERNAL_CONSTRUCTOR_NAME = "<init>"
private val LOGGER = Logger.getInstance("ScribeBytecodeUtil")
private val LAMBDA_NAME_KEY = Key.create<ParameterizedCachedValue<Object2IntMap<PsiLambdaExpression>, PsiClass>>("SCRIBE_LAMBDA_NAME")

// Type

val PsiPrimitiveType.internalName: Char
    get() = when (this) {
        PsiTypes.byteType() -> 'B'
        PsiTypes.charType() -> 'C'
        PsiTypes.doubleType() -> 'D'
        PsiTypes.floatType() -> 'F'
        PsiTypes.intType() -> 'I'
        PsiTypes.longType() -> 'J'
        PsiTypes.shortType() -> 'S'
        PsiTypes.booleanType() -> 'Z'
        PsiTypes.voidType() -> 'V'
        else -> throw IllegalArgumentException("Unsupported primitive type: $this")
    }

fun getPrimitiveType(internalName: Char): PsiPrimitiveType? {
    return when (internalName) {
        'B' -> PsiTypes.byteType()
        'C' -> PsiTypes.charType()
        'D' -> PsiTypes.doubleType()
        'F' -> PsiTypes.floatType()
        'I' -> PsiTypes.intType()
        'J' -> PsiTypes.longType()
        'S' -> PsiTypes.shortType()
        'Z' -> PsiTypes.booleanType()
        'V' -> PsiTypes.voidType()
        else -> null
    }
}

private fun PsiClassType.erasure() = TypeConversionUtil.erasure(this) as PsiClassType

@Throws(ClassNameResolutionFailedException::class)
private fun PsiClassType.appendInternalName(builder: StringBuilder): StringBuilder =
    erasure().resolve()?.appendInternalName(builder) ?: builder

@Throws(ClassNameResolutionFailedException::class)
private fun PsiType.appendDescriptor(builder: StringBuilder): StringBuilder {
    return when (this) {
        is PsiPrimitiveType -> builder.append(internalName)
        is PsiArrayType -> componentType.appendDescriptor(builder.append('['))
        is PsiClassType -> appendInternalName(builder.append('L')).append(';')
        is PsiWildcardType -> extendsBound.appendDescriptor(builder)
        is PsiCapturedWildcardType -> wildcard.appendDescriptor(builder)
        else -> {
            LOGGER.error("Unsupported PsiType: $this")
            builder
        }
    }
}

// Class

@Throws(ClassNameResolutionFailedException::class)
private fun PsiClass.appendInternalName(builder: StringBuilder): StringBuilder {
    return outerQualifiedName?.let { builder.append(it.replace('.', '/')) } ?: buildInternalName(builder)
}

@Throws(ClassNameResolutionFailedException::class)
private fun PsiClass.buildInternalName(builder: StringBuilder): StringBuilder {
    buildInnerName(builder, { it.outerQualifiedName?.replace('.', '/') })
    return builder
}

// Method

val PsiMethod.internalName: String
    get() = if (isConstructor) INTERNAL_CONSTRUCTOR_NAME else name

val PsiMethod.descriptor: String?
    get() {
        return try {
            appendDescriptor(StringBuilder()).toString()
        } catch (e: ClassNameResolutionFailedException) {
            null
        }
    }

@Throws(ClassNameResolutionFailedException::class)
private fun PsiMethod.appendDescriptor(builder: StringBuilder): StringBuilder {
    builder.append('(')
    if (this.isEnumConstructor()) {
        // Append fixed, synthetic parameters for Enum constructors.
        builder.append("Ljava/lang/String;I")
    } else if (this.hasSyntheticOuterClassParameter()) {
        this.containingClass?.containingClass?.appendInternalName(builder.append('L'))?.append(';')
    }
    for (parameter in parameterList.parameters) {
        parameter.type.appendDescriptor(builder)
    }
    builder.append(')')
    return (returnType ?: PsiTypes.voidType()).appendDescriptor(builder)
}

// Lambda

val PsiLambdaExpression.internalName: String?
    get() = this.internalIndex?.let { "lambda$" + this.suffix + '$' + it }

val PsiLambdaExpression.internalIndex: Int?
    get() {
        val upper = PsiTreeUtil.getParentOfType(this, PsiClass::class.java) ?: return null
        var value = upper.getUserData(LAMBDA_NAME_KEY)

        if (value == null) {
            value = CachedValuesManager.getManager(upper.project).createParameterizedCachedValue({
                val map = Object2IntOpenHashMap<PsiLambdaExpression>()
                it.accept(object : JavaRecursiveElementWalkingVisitor() {
                    var index = 0

                    override fun visitLambdaExpression(expression: PsiLambdaExpression) {
                        map[expression] = index

                        var parent = PsiTreeUtil.getParentOfType(expression, PsiLambdaExpression::class.java, PsiField::class.java, PsiMethod::class.java, PsiClass::class.java)
                        if (parent is PsiLambdaExpression) {
                            // This is a lambda inside a lambda, and it seems javac resolves the index of nested lambdas before an outer one
                            var nestAmount = 0
                            while (parent is PsiLambdaExpression) {
                                nestAmount++
                                map.computeIntIfPresent(parent) { _, v -> v + 1 }
                                parent = PsiTreeUtil.getParentOfType(parent, PsiLambdaExpression::class.java, PsiField::class.java, PsiMethod::class.java, PsiClass::class.java)
                            }
                            map[expression] = index - nestAmount
                        }

                        index++

                        super.visitLambdaExpression(expression)
                    }

                    override fun visitClass(aClass: PsiClass) {
                        if (aClass === it) {
                            super.visitClass(aClass)
                        }
                    }
                })
                CachedValueProvider.Result.create(map, it)
            }, false)
            upper.putUserData(LAMBDA_NAME_KEY, value)
        }

        return value.getValue(upper).getInt(this)
    }

val PsiLambdaExpression.suffix: String
    get() {
        val member: PsiMember? = PsiTreeUtil.getParentOfType(this, PsiMethod::class.java, PsiClass::class.java, PsiField::class.java)
        return if (member is PsiMethod) {
            if (member.isConstructor) "new" else member.name
        } else if (member is PsiField) {
            if (member.containingClass is PsiAnonymousClass) {
                ""
            } else if (member.hasModifierProperty(PsiModifier.STATIC)) {
                "static"
            } else {
                "new"
            }
        } else {
            "static"
        }
    }

val PsiLambdaExpression.descriptor: String?
    get() {
        val basicDescriptor = this.basicDescriptor ?: return null
        val targetMethodNode = this.findMethodNode() ?: return basicDescriptor

        // We didn't find the right method if the basic descriptor doesn't match at the end
        if (!targetMethodNode.desc.endsWith(basicDescriptor.substring(1)))
            return basicDescriptor

        return targetMethodNode.desc
    }

val PsiLambdaExpression.isStatic: Boolean?
    get() = findMethodNode()?.access?.let { it.and(Opcodes.ACC_STATIC) == Opcodes.ACC_STATIC }

private val lambdaMethodNodeCache: MutableMap<PsiLambdaExpression, MethodNode?> = WeakHashMap()

fun PsiLambdaExpression.findMethodNode(): MethodNode? = lambdaMethodNodeCache.computeIfAbsent(this) {
    val internalName = this.internalName ?: return@computeIfAbsent null

    return@computeIfAbsent findContainingClass()?.findClassNode()?.methods?.find { it.name == internalName }
}

val PsiLambdaExpression.basicDescriptor: String?
    get() {
        return try {
            appendDescriptor(StringBuilder()).toString()
        } catch (e: ClassNameResolutionFailedException) {
            null
        }
    }

@Throws(ClassNameResolutionFailedException::class)
private fun PsiLambdaExpression.appendDescriptor(builder: StringBuilder): StringBuilder {
    builder.append('(')
    val functionalInterfaceType = this.functionalInterfaceType
    for (i in 0 until parameterList.parameters.size) {
        LambdaUtil.getLambdaParameterFromType(functionalInterfaceType, i)?.appendDescriptor(builder)
    }
    builder.append(')')
    return (LambdaUtil.getFunctionalInterfaceReturnType(functionalInterfaceType) ?: PsiTypes.voidType()).appendDescriptor(builder)
}

// Field

val PsiField.descriptor: String?
    get() {
        return try {
            appendDescriptor(StringBuilder()).toString()
        } catch (e: ClassNameResolutionFailedException) {
            null
        }
    }

@Throws(ClassNameResolutionFailedException::class)
private fun PsiField.appendDescriptor(builder: StringBuilder): StringBuilder = type.appendDescriptor(builder)
