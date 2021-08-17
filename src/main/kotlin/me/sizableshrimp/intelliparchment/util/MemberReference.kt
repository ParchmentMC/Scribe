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

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import java.io.Serializable

/**
 * Represents a reference to a class member (a method or a field). It may
 * resolve to multiple members if [matchAll] is set or if the member is
 * not full qualified.
 */
data class MemberReference(
    val name: String,
    val descriptor: String? = null,
    val owner: String? = null,
    val matchAll: Boolean = false
) : Serializable {

    val qualified
        get() = this.owner != null

    val withoutOwner
        get() = if (this.owner == null) this else MemberReference(this.name, this.descriptor, null, this.matchAll)

    fun matchOwner(psiClass: PsiClass): Boolean {
        return this.owner == null || this.owner == psiClass.fullQualifiedName
    }
}

// Method

val PsiMethod.memberReference
    get() = MemberReference(internalName, descriptor)

val PsiMethod.qualifiedMemberReference
    get() = MemberReference(internalName, descriptor, containingClass?.fullQualifiedName)

fun PsiMethod.getQualifiedMemberReference(owner: PsiClass): MemberReference {
    return MemberReference(internalName, descriptor, owner.fullQualifiedName)
}

fun PsiMethod?.isSameReference(reference: PsiMethod?): Boolean =
    this != null && (this === reference || qualifiedMemberReference == reference?.qualifiedMemberReference)

// Field
val PsiField.simpleMemberReference
    get() = MemberReference(name)

val PsiField.memberReference
    get() = MemberReference(name, descriptor)

val PsiField.simpleQualifiedMemberReference
    get() = MemberReference(name, null, containingClass!!.fullQualifiedName)

val PsiField.qualifiedMemberReference
    get() = MemberReference(name, descriptor, containingClass!!.fullQualifiedName)

fun PsiField.getQualifiedMemberReference(owner: PsiClass): MemberReference {
    return MemberReference(name, descriptor, owner.fullQualifiedName)
}
