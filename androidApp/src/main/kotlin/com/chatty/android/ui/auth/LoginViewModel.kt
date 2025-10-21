package com.chatty.android.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chatty.domain.usecase.LoginUseCase
import com.chatty.domain.usecase.RegisterUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LoginUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val isLoggedIn: Boolean = false
)

class LoginViewModel(
    private val loginUseCase: LoginUseCase,
    private val registerUseCase: RegisterUseCase
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()
    
    fun login(username: String, password: String) {
        viewModelScope.launch {
            println("🔑 LoginViewModel: Starting login for user: $username")
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            loginUseCase(username, password)
                .onSuccess {
                    println("✅ LoginViewModel: Login successful!")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isLoggedIn = true,
                        error = null
                    )
                }
                .onFailure { error ->
                    println("❌ LoginViewModel: Login failed - ${error.message}")
                    error.printStackTrace()
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = error.message ?: "Login failed"
                    )
                }
        }
    }
    
    fun register(username: String, email: String, password: String, displayName: String) {
        viewModelScope.launch {
            println("📝 LoginViewModel: Starting registration for user: $username, email: $email, displayName: $displayName")
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            registerUseCase(username, email, password, displayName)
                .onSuccess {
                    println("✅ LoginViewModel: Registration successful!")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isLoggedIn = true,
                        error = null
                    )
                }
                .onFailure { error ->
                    println("❌ LoginViewModel: Registration failed - ${error.message}")
                    println("❌ LoginViewModel: Error details: ${error.toString()}")
                    error.printStackTrace()
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = error.message ?: "Registration failed"
                    )
                }
        }
    }
    
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
