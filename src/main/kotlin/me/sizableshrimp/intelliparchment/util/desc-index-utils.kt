package me.sizableshrimp.intelliparchment.util

import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier
import com.intellij.psi.impl.source.PsiParameterImpl

val PsiParameterImpl.jvmIndex: Byte
    get() {
        val containingMethod = this.declarationScope as? PsiMethod ?: return -1
        val isStatic = containingMethod.modifierList.hasModifierProperty(PsiModifier.STATIC)
        val params = containingMethod.qualifiedMemberReference.descriptor?.substringAfter('(')?.substringBefore(')') ?: return -1
        val thisIndex = containingMethod.parameterList.getParameterIndex(this)

        var i = 0
        var curIndex = 0
        var jvmIndex: Byte = if (isStatic) 0 else 1
        while (i < params.length) {
            if (curIndex == thisIndex)
                return jvmIndex
            when (params[i]) {
                'D', 'J' -> jvmIndex++
                'L' -> i = params.indexOf(';', startIndex = i) // i++ will add one to this
            }
            jvmIndex++
            curIndex++
            i++
        }

        return -1
    }