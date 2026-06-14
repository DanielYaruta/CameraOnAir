package com.example.cameraonair

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class Task(val id: Int, val title: String, val isDone: Boolean = false)

data class UiState(
    val counter: Int = 0,
    val tasks: List<Task> = emptyList(),
    val isLoading: Boolean = false
)

class StatefulViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(
        UiState(
            tasks = listOf(
                Task(1, "Записать демо-видео"),
                Task(2, "Добавить Compose-экраны"),
                Task(3, "Настроить ViewModel"),
                Task(4, "Протестировать приложение"),
                Task(5, "Обновить README"),
            )
        )
    )
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var nextTaskId = 6

    fun increment() = _uiState.update { it.copy(counter = it.counter + 1) }
    fun decrement() = _uiState.update { it.copy(counter = it.counter - 1) }
    fun reset() = _uiState.update { it.copy(counter = 0) }

    fun toggleTask(id: Int) = _uiState.update { state ->
        state.copy(tasks = state.tasks.map { task ->
            if (task.id == id) task.copy(isDone = !task.isDone) else task
        })
    }

    fun addTask(title: String) {
        if (title.isBlank()) return
        _uiState.update { state ->
            state.copy(tasks = state.tasks + Task(nextTaskId++, title))
        }
    }

    fun toggleLoading() = _uiState.update { it.copy(isLoading = !it.isLoading) }
}
