package fi.kotinaytto.tv.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fi.kotinaytto.tv.data.DashboardRepository
import fi.kotinaytto.tv.data.DashboardState
import fi.kotinaytto.tv.data.toDashboardState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class DashboardViewModel(
    private val repository: DashboardRepository = DashboardRepository(),
) : ViewModel() {
    private val _state = MutableStateFlow(DashboardState())
    val state: StateFlow<DashboardState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            refreshAll()
            while (isActive) {
                delay(45_000L)
                refreshAll()
            }
        }
        viewModelScope.launch {
            while (isActive) {
                delay(8_000L)
                repository.fetchShoppingOnly()
                    .onSuccess { list -> _state.update { it.copy(shopping = list) } }
            }
        }
    }

    private suspend fun refreshAll() {
        repository.fetchDashboard()
            .onSuccess { json ->
                _state.value = json.toDashboardState().copy(error = null)
            }
            .onFailure { e ->
                _state.update { it.copy(error = e.message ?: "Virhe") }
            }
    }
}
