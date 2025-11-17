# DynamicBaseUrlSwitcher Usage Examples

## Overview

`DynamicBaseUrlSwitcher` автоматически управляет `KEY_OC_BASE_URL` в `AccountManager` на основе состояния сети. Он использует `BaseUrlChooser` для выбора лучшего доступного URL и обновляет аккаунт при изменениях.

## Architecture

```
NetworkStateObserver → BaseUrlChooser → DynamicBaseUrlSwitcher → AccountManager
                                                ↓
                                    ManageDynamicUrlSwitchingUseCase
```

## Basic Usage

### 1. В LoginViewModel (при входе)

```kotlin
class LoginViewModel(
    private val manageDynamicUrlSwitching: ManageDynamicUrlSwitchingUseCase
) : ViewModel() {
    
    fun onLoginSuccess(accountName: String) {
        val account = Account(accountName, getString(R.string.account_type))
        
        // Запустить динамическое переключение URL
        manageDynamicUrlSwitching.startDynamicUrlSwitching(account)
    }
}
```

### 2. В FileDisplayActivity (при выходе)

```kotlin
class FileDisplayActivity : AppCompatActivity() {
    
    private val manageDynamicUrlSwitching: ManageDynamicUrlSwitchingUseCase by inject()
    
    fun onLogout() {
        // Остановить динамическое переключение URL
        manageDynamicUrlSwitching.stopDynamicUrlSwitching()
        
        // ... остальная логика выхода
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // На всякий случай остановить при уничтожении активности
        if (isFinishing) {
            manageDynamicUrlSwitching.stopDynamicUrlSwitching()
        }
    }
}
```

### 3. В Application класе (глобальное управление)

```kotlin
class MainApp : Application() {
    
    private val manageDynamicUrlSwitching: ManageDynamicUrlSwitchingUseCase by inject()
    
    fun onUserLoggedIn(account: Account) {
        // Запустить при входе
        manageDynamicUrlSwitching.startDynamicUrlSwitching(account)
    }
    
    fun onUserLoggedOut() {
        // Остановить при выходе
        manageDynamicUrlSwitching.stopDynamicUrlSwitching()
    }
    
    override fun onTerminate() {
        super.onTerminate()
        // Очистить при завершении приложения
        manageDynamicUrlSwitching.stopDynamicUrlSwitching()
    }
}
```

### 4. Проверка состояния

```kotlin
fun checkDynamicUrlStatus() {
    if (manageDynamicUrlSwitching.isActive()) {
        val currentAccount = manageDynamicUrlSwitching.getCurrentAccount()
        Log.d("Status", "Dynamic URL switching active for: ${currentAccount?.name}")
    } else {
        Log.d("Status", "Dynamic URL switching is not active")
    }
}
```

### 5. В RemoveAccountUseCase (очистка при удалении аккаунта)

```kotlin
class RemoveAccountUseCase(
    private val accountsRepository: AccountsRepository,
    private val manageDynamicUrlSwitching: ManageDynamicUrlSwitchingUseCase,
    // ... other dependencies
) : BaseUseCase<Unit, RemoveAccountUseCase.Params>() {
    
    override fun run(params: Params) {
        // Остановить динамическое переключение
        val currentAccount = manageDynamicUrlSwitching.getCurrentAccount()
        if (currentAccount?.name == params.accountName) {
            manageDynamicUrlSwitching.stopDynamicUrlSwitching()
        }
        
        // ... остальная логика удаления аккаунта
        accountsRepository.deleteAccount(params.accountName)
    }
}
```

## Advanced: Direct Usage of DynamicBaseUrlSwitcher

Если нужен прямой доступ (обычно не рекомендуется, используйте UseCase):

```kotlin
class MyRepository(
    private val dynamicBaseUrlSwitcher: DynamicBaseUrlSwitcher
) {
    
    fun startManaging(account: Account) {
        dynamicBaseUrlSwitcher.startDynamicUrlSwitching(account)
    }
    
    fun stopManaging() {
        dynamicBaseUrlSwitcher.stopDynamicUrlSwitching()
    }
    
    fun cleanup() {
        // Полная очистка ресурсов (отменяет scope)
        dynamicBaseUrlSwitcher.dispose()
    }
}
```

## Testing

### Unit Test Example

```kotlin
@Test
fun `should start dynamic URL switching on login`() = runTest {
    val account = Account("test@example.com", "owncloud")
    val useCase: ManageDynamicUrlSwitchingUseCase = get()
    
    useCase.startDynamicUrlSwitching(account)
    
    assertTrue(useCase.isActive())
    assertEquals(account, useCase.getCurrentAccount())
}
```

## Important Notes

1. **Lifecycle Management**: Всегда вызывайте `stopDynamicUrlSwitching()` при выходе из системы
2. **Single Account**: Поддерживается только один аккаунт одновременно
3. **Automatic Switching**: URL обновляется автоматически при изменении сети
4. **Priority**: LOCAL > PUBLIC > REMOTE (из `BaseUrlChooser`)
5. **Thread Safety**: Все операции thread-safe благодаря coroutine scope

## Troubleshooting

### URL не обновляется
- Проверьте, что `startDynamicUrlSwitching()` был вызван
- Убедитесь, что URL сохранены в `CurrentDeviceStorage`
- Проверьте логи Timber для отладки

### Утечки памяти
- Всегда вызывайте `stopDynamicUrlSwitching()` при выходе
- Не держите ссылки на Account после остановки

### Ошибки Permission
- Убедитесь, что приложение имеет права на изменение аккаунтов
- Проверьте AndroidManifest.xml для нужных permissions

