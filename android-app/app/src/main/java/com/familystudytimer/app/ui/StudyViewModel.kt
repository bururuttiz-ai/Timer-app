package com.familystudytimer.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.familystudytimer.app.data.ChildEntity
import com.familystudytimer.app.data.DailyRecordEntity
import com.familystudytimer.app.data.StudyRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class ChildUiState(
    val child: ChildEntity,
    val todayRecord: DailyRecordEntity,
    val isStudying: Boolean,
    val displaySeconds: Long,
)

class StudyViewModel(private val repository: StudyRepository) : ViewModel() {

    private val _children = MutableStateFlow<List<ChildUiState>>(emptyList())
    val children: StateFlow<List<ChildUiState>> = _children.asStateFlow()

    private val _needsSetup = MutableStateFlow(false)
    val needsSetup: StateFlow<Boolean> = _needsSetup.asStateFlow()

    private val _isLoaded = MutableStateFlow(false)
    val isLoaded: StateFlow<Boolean> = _isLoaded.asStateFlow()

    init {
        viewModelScope.launch {
            while (isActive) {
                refresh()
                _isLoaded.value = true
                kotlinx.coroutines.delay(1000)
            }
        }
    }

    private suspend fun refresh() {
        val childList = repository.getChildren()
        _needsSetup.value = childList.isEmpty()
        _children.value = childList.map { child ->
            ChildUiState(
                child = child,
                todayRecord = repository.getOrCreateTodayRecord(child.id),
                isStudying = repository.isStudying(child.id),
                displaySeconds = repository.currentDisplaySeconds(child.id),
            )
        }
    }

    fun createChildren(name1: String, name2: String) {
        viewModelScope.launch {
            repository.createChild(name1.ifBlank { "こども1" }, sortOrder = 0)
            repository.createChild(name2.ifBlank { "こども2" }, sortOrder = 1)
            refresh()
        }
    }

    /** trueが返れば、達成の合図として呼び出し側で音を鳴らす等に使える。 */
    fun toggleStudy(childId: Long, onStopped: (achievedJustNow: Boolean) -> Unit) {
        viewModelScope.launch {
            val wasStudying = repository.isStudying(childId)
            if (wasStudying) {
                val result = repository.pauseStudy(childId)
                refresh()
                onStopped(result?.achievedJustNow == true)
            } else {
                repository.startStudy(childId)
                refresh()
            }
        }
    }

    fun manualAdjust(childId: Long, deltaMinutes: Int) {
        viewModelScope.launch {
            repository.manualAdjustMinutes(childId, deltaMinutes)
            refresh()
        }
    }

    class Factory(private val repository: StudyRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return StudyViewModel(repository) as T
        }
    }
}
