package com.example

import com.example.model.AppLanguage
import com.example.model.LanguageMode
import com.example.model.SubjectCategory
import com.example.model.getAppStrings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLocalizationTest {
    @Test
    fun allAppLanguages_haveCompleteTranslations() {
        for (language in AppLanguage.entries) {
            val strings = getAppStrings(language)
            assertTrue("Missing appTitle for $language", strings.appTitle.isNotBlank())
            assertTrue("Missing heroTitle for $language", strings.heroTitle.isNotBlank())
            assertTrue("Missing settingsTitle for $language", strings.settingsTitle.isNotBlank())
            assertFalse("Missing samplePrompts for $language", strings.samplePrompts.isEmpty())

            for (subject in SubjectCategory.entries) {
                assertTrue(
                    "Missing subject translation for $subject in $language",
                    strings.subjectName(subject).isNotBlank()
                )
            }
            for (mode in LanguageMode.entries) {
                assertTrue(
                    "Missing languageMode translation for $mode in $language",
                    strings.languageModeName(mode).isNotBlank()
                )
            }
        }
    }
}
