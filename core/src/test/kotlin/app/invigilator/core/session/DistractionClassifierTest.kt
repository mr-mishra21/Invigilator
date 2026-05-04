package app.invigilator.core.session

import org.junit.Assert.assertEquals
import org.junit.Test

class DistractionClassifierTest {

    private val classifier = DistractionClassifier()

    @Test
    fun invigilator_itself_is_study() {
        assertEquals(AppCategory.STUDY, classifier.classify("app.invigilator"))
    }

    @Test
    fun khan_academy_is_study() {
        assertEquals(AppCategory.STUDY, classifier.classify("org.khanacademy.android"))
    }

    @Test
    fun byjus_is_study() {
        assertEquals(AppCategory.STUDY, classifier.classify("com.byjus.thelearningapp"))
    }

    @Test
    fun phone_dialer_is_essential() {
        assertEquals(AppCategory.ESSENTIAL, classifier.classify("com.google.android.dialer"))
    }

    @Test
    fun launcher_is_essential() {
        assertEquals(AppCategory.ESSENTIAL, classifier.classify("com.android.launcher3"))
    }

    @Test
    fun systemui_is_essential() {
        assertEquals(AppCategory.ESSENTIAL, classifier.classify("com.android.systemui"))
    }

    @Test
    fun instagram_is_distracting() {
        assertEquals(AppCategory.DISTRACTING, classifier.classify("com.instagram.android"))
    }

    @Test
    fun youtube_is_distracting() {
        assertEquals(AppCategory.DISTRACTING, classifier.classify("com.google.android.youtube"))
    }

    @Test
    fun whatsapp_is_distracting() {
        assertEquals(AppCategory.DISTRACTING, classifier.classify("com.whatsapp"))
    }

    @Test
    fun unknown_app_is_unknown() {
        assertEquals(AppCategory.UNKNOWN, classifier.classify("com.example.somerandomapp"))
    }
}
