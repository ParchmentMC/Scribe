package me.sizableshrimp.intelliparchment.gradle

import java.io.File

interface ForgeGradleModel {
    fun getMcVersion(): String
    fun getExtractSrgTaskName(): String
    fun getExtractSrgTaskOutput(): File
    fun getClientMappings(): File?
}