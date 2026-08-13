# Subfay Android SDK

Kotlin SDK for integrating Subfay into your Android apps.

## Requirements

- Android API 21+ (Lollipop 5.0)
- Kotlin 1.9+
- Gradle 8.0+

## Installation

### Gradle (Kotlin DSL)

Add to your `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.subfay:sdk-android:1.0.0")
}
```

### Gradle (Groovy)

```groovy
dependencies {
    implementation 'com.subfay:sdk-android:1.0.0'
}
```

### Maven

```xml
<dependency>
    <groupId>com.subfay</groupId>
    <artifactId>sdk-android</artifactId>
    <version>1.0.0</version>
</dependency>
```

## Quick Start

### 1. Configure SDK

In your `Application` class:

```kotlin
import android.app.Application
import com.subfay.sdk.Subfay
import com.subfay.sdk.Environment

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        Subfay.configure(
            context = this,
            apiKey = "your_api_key_here",
            environment = Environment.PRODUCTION
        )
    }
}
```

Don't forget to register in `AndroidManifest.xml`:

```xml
<application
    android:name=".MyApplication"
    ...>
</application>
```

### 2. Identify User

After user logs in:

```kotlin
import kotlinx.coroutines.launch

lifecycleScope.launch {
    Subfay.identify(externalUserId = "user_123")
}
```

### 3. Check Entitlements

```kotlin
// Check single entitlement
lifecycleScope.launch {
    val hasPremium = Subfay.hasEntitlement("premium_access")

    if (hasPremium) {
        // Show premium features
    } else {
        // Show paywall
    }
}

// Get all entitlements
lifecycleScope.launch {
    val entitlements = Subfay.getEntitlements()
    println("User has: $entitlements")
}
```

## Advanced Usage

### Observe Entitlement Changes (Flow)

```kotlin
import kotlinx.coroutines.flow.collect

lifecycleScope.launch {
    Subfay.entitlementsFlow.collect { entitlements ->
        println("Entitlements updated: $entitlements")
        updateUI()
    }
}
```

### Jetpack Compose Integration

```kotlin
import androidx.compose.runtime.*
import com.subfay.sdk.rememberEntitlements
import com.subfay.sdk.rememberHasEntitlement

@Composable
fun PremiumFeature() {
    val hasPremium by rememberHasEntitlement("premium_access")

    if (hasPremium) {
        PremiumContent()
    } else {
        Paywall()
    }
}

@Composable
fun EntitlementsList() {
    val entitlements by rememberEntitlements()

    LazyColumn {
        items(entitlements) { entitlement ->
            Text(text = entitlement)
        }
    }
}
```

### Configuration Options

```kotlin
import com.subfay.sdk.ConfigOptions
import com.subfay.sdk.LogLevel

val options = ConfigOptions(
    cacheExpiration = 3600_000L,  // 1 hour in ms
    automaticSync = true,          // Auto-sync on app launch
    logLevel = LogLevel.DEBUG,     // Verbose logging
    timeout = 30_000L,             // 30 second timeout
    baseURL = null                 // Use default URL
)

Subfay.configure(
    context = this,
    apiKey = "your_api_key",
    environment = Environment.PRODUCTION,
    options = options
)
```

### Manual Sync

Force refresh entitlements from server:

```kotlin
lifecycleScope.launch {
    val entitlements = Subfay.syncEntitlements()
}
```

### Logout

Clear user data:

```kotlin
Subfay.logout()
```

## Activity Example

```kotlin
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.subfay.sdk.Subfay
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        lifecycleScope.launch {
            checkPremiumAccess()
        }
    }

    private suspend fun checkPremiumAccess() {
        try {
            val hasPremium = Subfay.hasEntitlement("premium_access")

            if (hasPremium) {
                showPremiumContent()
            } else {
                showPaywall()
            }
        } catch (e: Exception) {
            println("Error checking entitlement: ${e.message}")
        }
    }

    private fun showPremiumContent() {
        // Show premium UI
    }

    private fun showPaywall() {
        // Show paywall
    }
}
```

## Fragment Example

```kotlin
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.subfay.sdk.Subfay
import kotlinx.coroutines.launch

class ProfileFragment : Fragment(R.layout.fragment_profile) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        lifecycleScope.launch {
            val entitlements = Subfay.getEntitlements()
            updateUI(entitlements)
        }
    }

    private fun updateUI(entitlements: List<String>) {
        // Update views based on entitlements
    }
}
```

## ViewModel Example

```kotlin
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.subfay.sdk.Subfay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel : ViewModel() {

    private val _hasPremium = MutableStateFlow(false)
    val hasPremium: StateFlow<Boolean> = _hasPremium.asStateFlow()

    init {
        observeEntitlements()
    }

    fun identifyUser(userId: String) {
        viewModelScope.launch {
            Subfay.identify(externalUserId = userId)
            checkPremiumStatus()
        }
    }

    private fun observeEntitlements() {
        viewModelScope.launch {
            Subfay.entitlementsFlow.collect { entitlements ->
                _hasPremium.value = entitlements.contains("premium_access")
            }
        }
    }

    private suspend fun checkPremiumStatus() {
        _hasPremium.value = Subfay.hasEntitlement("premium_access")
    }
}
```

## Error Handling

```kotlin
import com.subfay.sdk.SubfayException

try {
    val entitlements = Subfay.getEntitlements()
} catch (e: SubfayException.NetworkError) {
    println("Network error: ${e.message}")
} catch (e: SubfayException.AuthenticationError) {
    println("Auth error: ${e.message}")
} catch (e: SubfayException.ServerError) {
    println("Server error ${e.statusCode}: ${e.message}")
} catch (e: Exception) {
    println("Unknown error: ${e.message}")
}
```

## Testing

### Unit Tests

```kotlin
import com.subfay.sdk.Subfay
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.Assert.*

class EntitlementTest {

    @Test
    fun testHasPremiumAccess() = runTest {
        // Identify test user
        Subfay.identify(externalUserId = "test_user")

        // Check entitlement
        val hasPremium = Subfay.hasEntitlement("premium_access")
        assertTrue(hasPremium)
    }
}
```

### Instrumented Tests

```kotlin
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.subfay.sdk.Subfay
import com.subfay.sdk.Environment
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SubfayInstrumentedTest {

    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        Subfay.configure(
            context = context,
            apiKey = "test_key",
            environment = Environment.SANDBOX
        )
    }

    @Test
    fun testIdentifyUser() = runBlocking {
        val customer = Subfay.identify("test_user")
        assertEquals("test_user", customer.externalId)
    }
}
```

## Best Practices

1. **Configure Once**: Call `configure()` only once in `Application.onCreate()`
2. **Identify After Login**: Call `identify()` after user authenticates
3. **Logout on Sign Out**: Call `logout()` when user signs out
4. **Use Coroutines**: All suspend functions should be called from coroutine scope
5. **Handle Errors**: Always handle exceptions with try/catch
6. **Cache**: SDK automatically caches entitlements for 1 hour
7. **Background Sync**: SDK syncs on app launch if cache is stale
8. **Offline Support**: Cached entitlements work offline

## Permissions

Add to `AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

## ProGuard Rules

If using ProGuard/R8, add these rules:

```proguard
# Subfay SDK
-keep class com.subfay.sdk.** { *; }
-keepclassmembers class com.subfay.sdk.** { *; }

# OkHttp
-dontwarn okhttp3.**
-keep class okhttp3.** { *; }

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
```

## Migration from RevenueCat

### Before (RevenueCat)

```kotlin
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration

Purchases.configure(
    PurchasesConfiguration.Builder(context, "rc_api_key").build()
)

Purchases.sharedInstance.getCustomerInfo { customerInfo, error ->
    if (customerInfo?.entitlements?.get("premium")?.isActive == true) {
        // Show premium
    }
}
```

### After (Subfay)

```kotlin
import com.subfay.sdk.Subfay
import com.subfay.sdk.Environment

Subfay.configure(
    context = this,
    apiKey = "your_api_key",
    environment = Environment.PRODUCTION
)

lifecycleScope.launch {
    Subfay.identify(externalUserId = "user_123")
    val hasPremium = Subfay.hasEntitlement("premium_access")
    // Show premium
}
```

## Troubleshooting

### SDK Not Configured Error

Make sure you call `Subfay.configure()` in `Application.onCreate()`.

### Authentication Error

Call `Subfay.identify()` before checking entitlements.

### Network Errors

Check internet connectivity and API key validity.

### Coroutine Scope Issues

Always call suspend functions from a proper coroutine scope:
- `lifecycleScope` in Activity/Fragment
- `viewModelScope` in ViewModel
- `rememberCoroutineScope()` in Compose

### Cache Issues

Clear cache manually:
```kotlin
Subfay.logout()  // Clears all cached data
```

## Performance

- **Initial Load**: < 100ms (from cache)
- **Network Request**: < 500ms (typical)
- **Memory Usage**: < 5MB
- **Cache Size**: < 1MB

## Support

- **Documentation**: https://docs.subfay.com
- **API Reference**: https://docs.subfay.com/android
- **GitHub Issues**: https://github.com/yourusername/subfay-android-sdk/issues
- **Email**: support@subfay.com

## License

MIT License - see LICENSE file for details

---

**Version**: 1.0.0
**Last Updated**: January 2026
