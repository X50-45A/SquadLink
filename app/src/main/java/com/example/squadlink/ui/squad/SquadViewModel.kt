package com.example.squadlink.ui.squad

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.squadlink.data.FirebaseAccountRepository
import com.example.squadlink.data.UserPreferencesRepository
import com.example.squadlink.model.AccountRole
import com.example.squadlink.model.SquadMemberProfile
import com.example.squadlink.model.SquadRole
import com.example.squadlink.model.SquadSummary
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SquadUiState(
    val currentUserId: String = "",
    val currentUserName: String = "",
    val currentUserCallsign: String = "",
    val currentSquadRole: SquadRole = SquadRole.RIFLEMAN,
    val currentAccountRole: AccountRole = AccountRole.PLAYER,
    val squadName: String = "",
    val squadCode: String = "",
    val squadLeaderId: String = "",
    val members: List<SquadMemberProfile> = emptyList(),
    val isBusy: Boolean = false,
    val errorMessage: String? = null
) {
    val hasSquad: Boolean
        get() = squadName.isNotBlank()

    val isLeader: Boolean
        get() = currentUserId.isNotBlank() && currentUserId == squadLeaderId
}

@OptIn(ExperimentalCoroutinesApi::class)
class SquadViewModel(
    private val accountRepository: FirebaseAccountRepository
) : ViewModel() {

    private val viewState = MutableStateFlow(SquadUiState())

    private val profileFlow = accountRepository.observeCurrentProfile()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null
        )

    private val membersFlow: StateFlow<List<SquadMemberProfile>> = profileFlow
        .flatMapLatest { profile ->
            if (profile?.squadId.isNullOrBlank()) {
                flowOf(emptyList())
            } else {
                accountRepository.observeSquadMembers(profile!!.squadId)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    private val squadFlow: StateFlow<SquadSummary?> = profileFlow
        .flatMapLatest { profile ->
            if (profile?.squadId.isNullOrBlank()) {
                flowOf(null)
            } else {
                accountRepository.observeSquad(profile!!.squadId)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null
        )

    val uiState: StateFlow<SquadUiState> = combine(
        profileFlow,
        squadFlow,
        membersFlow,
        viewState
    ) { profile, squad, members, state ->
        state.copy(
            currentUserId = profile?.uid.orEmpty(),
            currentUserName = profile?.displayName.orEmpty(),
            currentUserCallsign = profile?.callsign.orEmpty(),
            currentSquadRole = profile?.squadRole ?: SquadRole.RIFLEMAN,
            currentAccountRole = profile?.role ?: AccountRole.PLAYER,
            squadName = squad?.name ?: profile?.squadName.orEmpty(),
            squadCode = squad?.joinCode ?: profile?.squadCode.orEmpty(),
            squadLeaderId = squad?.createdBy.orEmpty(),
            members = members
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SquadUiState()
    )

    fun createSquad(name: String) {
        runBusyAction {
            accountRepository.createSquad(name)
        }
    }

    fun joinSquad(code: String) {
        runBusyAction {
            accountRepository.joinSquad(code)
        }
    }

    fun leaveSquad() {
        runBusyAction {
            accountRepository.leaveSquad()
        }
    }

    fun removeMember(memberId: String) {
        runBusyAction {
            accountRepository.removeMember(memberId)
        }
    }

    fun clearError() {
        viewState.update { it.copy(errorMessage = null) }
    }

    private fun runBusyAction(action: suspend () -> Unit) {
        viewModelScope.launch {
            viewState.update { it.copy(isBusy = true, errorMessage = null) }
            try {
                action()
            } catch (error: Throwable) {
                viewState.update {
                    it.copy(
                        errorMessage = error.message
                            ?: "No se pudo completar la accion del escuadron."
                    )
                }
            }
            viewState.update { it.copy(isBusy = false) }
        }
    }

    class Factory(private val repo: UserPreferencesRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SquadViewModel(FirebaseAccountRepository(repo)) as T
    }
}
