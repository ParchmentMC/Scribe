/*
 * Minecraft Dev for IntelliJ
 *
 * https://minecraftdev.org
 *
 * Copyright (c) 2021 minecraft-dev
 *
 * MIT License
 */

package me.sizableshrimp.intelliparchment.util

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiType
import com.intellij.psi.util.TypeConversionUtil
import org.jetbrains.plugins.gradle.util.GradleUtil

fun PsiElement.findModule(): Module? = ModuleUtilCore.findModuleForPsiElement(this)

@Suppress("UnstableApiUsage")
fun PsiFile.findGradleModule(): DataNode<ModuleData>? = findModule()?.let { GradleUtil.findGradleModuleData(it) }

fun PsiElement.findContainingClass(): PsiClass? = findParent(resolveReferences = false)

private val PsiElement.ancestors: Sequence<PsiElement>
    get() = generateSequence(this) { if (it is PsiFile) null else it.parent }

private inline fun <reified T : PsiElement> PsiElement.findParent(resolveReferences: Boolean): T? {
    return findParent({ false }, resolveReferences)
}

private inline fun <reified T : PsiElement> PsiElement.findParent(
    stop: (PsiElement) -> Boolean,
    resolveReferences: Boolean
): T? {
    var el: PsiElement = this

    while (true) {
        if (resolveReferences && el is PsiReference) {
            el = el.resolve() ?: return null
        }

        if (el is T) {
            return el
        }

        if (el is PsiFile || el is PsiDirectory || stop(el)) {
            return null
        }

        el = el.parent ?: return null
    }
}

private inline fun <reified T : PsiElement> PsiElement.findChild(): T? {
    return firstChild?.findSibling(strict = false)
}

private inline fun <reified T : PsiElement> PsiElement.findSibling(strict: Boolean): T? {
    var sibling = if (strict) nextSibling ?: return null else this
    while (true) {
        if (sibling is T) {
            return sibling
        }

        sibling = sibling.nextSibling ?: return null
    }
}

private inline fun PsiElement.forEachChild(func: (PsiElement) -> Unit) {
    firstChild?.forEachSibling(func, strict = false)
}

private inline fun PsiElement.forEachSibling(func: (PsiElement) -> Unit, strict: Boolean) {
    var sibling = if (strict) nextSibling ?: return else this
    while (true) {
        func(sibling)
        sibling = sibling.nextSibling ?: return
    }
}

val PsiElement.constantValue: Any?
    get() = JavaPsiFacade.getInstance(project).constantEvaluationHelper.computeConstantExpression(this)

private val ACCESS_MODIFIERS =
    listOf(PsiModifier.PUBLIC, PsiModifier.PROTECTED, PsiModifier.PRIVATE, PsiModifier.PACKAGE_LOCAL)

infix fun PsiElement.equivalentTo(other: PsiElement): Boolean {
    return manager.areElementsEquivalent(this, other)
}

fun PsiType?.isErasureEquivalentTo(other: PsiType?): Boolean {
    // TODO: Do more checks for generics instead
    return TypeConversionUtil.erasure(this) == TypeConversionUtil.erasure(other)
}

fun PsiMethod.findAllSuperMethods(): MutableList<PsiMethod> {
    val all = mutableListOf<PsiMethod>()
    val toFind = ArrayDeque<PsiMethod>()
    toFind.add(this)

    while (toFind.isNotEmpty()) {
        val next = toFind.removeLast()
        next.findSuperMethods().forEach {
            all.add(it)
            toFind.add(it)
        }
    }

    return all
}

