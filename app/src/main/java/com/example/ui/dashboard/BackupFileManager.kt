package com.example.ui.dashboard

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object BackupFileManager {

    fun createZipBackup(jsonString: String, files: List<File>, outputFile: File) {
        ZipOutputStream(FileOutputStream(outputFile)).use { zos ->
            // Add JSON content
            val entry = ZipEntry("data.json")
            zos.putNextEntry(entry)
            zos.write(jsonString.toByteArray())
            zos.closeEntry()

            // Add files
            files.forEach { file ->
                if (file.exists()) {
                    val zipEntry = ZipEntry("attachments/${file.name}")
                    zos.putNextEntry(zipEntry)
                    FileInputStream(file).use { fis ->
                        fis.copyTo(zos)
                    }
                    zos.closeEntry()
                }
            }
        }
    }

    fun extractZipBackup(zipFile: File, outputDir: File): String? {
        var jsonContent: String? = null
        
        ZipInputStream(FileInputStream(zipFile)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (entry.name == "data.json") {
                    jsonContent = zis.bufferedReader().readText()
                } else if (entry.name.startsWith("attachments/")) {
                    val outputFile = File(outputDir, entry.name.substring("attachments/".length))
                    FileOutputStream(outputFile).use { fos ->
                        zis.copyTo(fos)
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        
        return jsonContent
    }
}

