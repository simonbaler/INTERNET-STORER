package com.example.features.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class OnboardingSlide(
    val id: String,
    val title: String,
    val description: String,
    val note: String? = null,
    val badge: String
)

class OnboardingViewModel(
    private val appContainer: AppContainer
) : ViewModel() {

    val slides = listOf(
        OnboardingSlide(
            id = "welcome",
            title = "Your digital world, wherever you are.",
            description = "InternetStorer helps keep your important digital resources available even when your Internet connection isn't.",
            badge = "Digital Continuity"
        ),
        OnboardingSlide(
            id = "offline",
            title = "No Internet? No panic. ♡",
            description = "Your saved files, local knowledge and offline features remain available even when connectivity disappears.",
            badge = "Offline-First"
        ),
        OnboardingSlide(
            id = "nearby",
            title = "Stay connected nearby.",
            description = "InternetStorer will later support secure device-to-device communication and local file sharing without cellular towers or routers.",
            note = "Device-to-device mesh planned for Phase 4 & 5",
            badge = "Peer-to-Peer"
        ),
        OnboardingSlide(
            id = "ai",
            title = "Intelligence that stays with you.",
            description = "InternetStorer is engineered to support offline on-device AI, private embeddings, and local knowledge retrieval.",
            note = "On-device AI engine planned for Phase 8",
            badge = "Offline AI"
        ),
        OnboardingSlide(
            id = "ready",
            title = "Let's build your offline world.",
            description = "Your Internet connection is helpful, but it should never be the only thing your digital life depends on.",
            badge = "Phase 01 Active"
        )
    )

    private val _currentSlideIndex = MutableStateFlow(0)
    val currentSlideIndex: StateFlow<Int> = _currentSlideIndex.asStateFlow()

    fun nextSlide(onCompleted: () -> Unit) {
        if (_currentSlideIndex.value < slides.size - 1) {
            _currentSlideIndex.value += 1
        } else {
            completeOnboarding(onCompleted)
        }
    }

    fun previousSlide() {
        if (_currentSlideIndex.value > 0) {
            _currentSlideIndex.value -= 1
        }
    }

    fun completeOnboarding(onCompleted: () -> Unit) {
        viewModelScope.launch {
            appContainer.preferencesManager.setOnboardingCompleted(true)
            appContainer.activityRepository.recordActivity(
                title = "Onboarding Completed",
                description = "User established local continuity orientation",
                category = "ONBOARDING"
            )
            onCompleted()
        }
    }
}
