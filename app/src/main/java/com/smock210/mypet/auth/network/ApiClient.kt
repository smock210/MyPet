package com.smock210.mypet.auth.network

import android.content.Context
import android.util.Log
import com.smock210.mypet.BuildConfig
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Единая точка доступа к Auth API (ANDROID_CLIENT.md §6).
 *
 * - таймауты: connect 15s, read 20s (§5.6);
 * - логирование запросов — только в debug и уровень BASIC:
 *   в BODY уходят токены, это запрещено (§0.6/§5.5);
 * - `Bearer`-заголовок — AuthInterceptor, автообновление — TokenAuthenticator;
 * - при «сессии истекла» публикуется [sessionExpired] (UI уводит на Login).
 */
object ApiClient {

    private const val TAG = "AuthApi"

    private val _sessionExpired = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Сигнал окончания сессии: refresh отозван, токены очищены. */
    val sessionExpired: SharedFlow<Unit> = _sessionExpired.asSharedFlow()

    @Volatile
    private var initialized = false

    private lateinit var appContext: Context

    private lateinit var tokenStore: TokenStore

    private val jsonConverter: GsonConverterFactory
        get() = GsonConverterFactory.create()

    /** Клиент без authenticator — нужен для самого refresh (без рекурсии). */
    private val plainClient: OkHttpClient by lazy { newClient(authenticated = false) }

    private val plainApi: AuthApi by lazy { newRetrofit(plainClient).create(AuthApi::class.java) }

    private val authedClient: OkHttpClient by lazy { newClient(authenticated = true) }

    /** Основной клиент: Bearer + автообновление токенов. */
    val api: AuthApi by lazy { newRetrofit(authedClient).create(AuthApi::class.java) }

    /**
     * Инициализация хранилища токенов. Вызывается в
     * [com.smock210.mypet.VkApplication.onCreate] до первого обращения к [api].
     */
    @Synchronized
    fun init(context: Context) {
        if (initialized) return
        appContext = context.applicationContext
        tokenStore = TokenStore(appContext)
        initialized = true
    }

    /** Хранилище токенов (для AuthRepository); инициализирует при необходимости. */
    fun tokens(context: Context): TokenStore {
        if (!initialized) init(context)
        return tokenStore
    }

    private fun newClient(authenticated: Boolean): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .addInterceptor(AuthInterceptor { if (initialized) tokenStore.access else null })

        if (BuildConfig.DEBUG) {
            builder.addInterceptor(
                HttpLoggingInterceptor { message -> Log.d(TAG, message) }
                    .apply { level = HttpLoggingInterceptor.Level.BASIC }
            )
        }

        if (authenticated) {
            builder.authenticator(
                TokenAuthenticator(tokenStore, plainApi) {
                    // Сначала сбрасываем кэш шапки, потом сигналим UI —
                    // иначе главный экран успеет показать устаревший профиль
                    clearProfileCache()
                    _sessionExpired.tryEmit(Unit)
                }
            )
        }

        if (BuildConfig.DEBUG && BuildConfig.AUTH_INSECURE_DEBUG_TLS) {
            applyInsecureDebugTls(builder)
        }

        return builder.build()
    }

    /**
     * Сбрасывает кэш профиля в шапке главного экрана при окончании сессии.
     * Ключи учётных данных в том же хранилище не трогаются.
     */
    private fun clearProfileCache() {
        try {
            val prefs = com.smock210.mypet.security.SecurePreferencesManager(appContext)
            prefs.saveProfileName("")
            prefs.saveProfileProvider("")
            prefs.saveProfileEmail("")
            prefs.saveProfilePhoto("")
        } catch (t: Throwable) {
            Log.w(TAG, "Не удалось очистить кэш профиля", t)
        }
    }

    private fun newRetrofit(client: OkHttpClient): Retrofit {
        // BASE_URL обязана заканчиваться на "/" (ANDROID_CLIENT.md §6.1)
        val baseUrl = BuildConfig.AUTH_BASE_URL.let { if (it.endsWith("/")) it else "$it/" }
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(jsonConverter)
            .build()
    }

    /**
     * ВРЕМЕННЫЙ debug-обход: сервер отдаёт самоподписанный истёкший
     * сертификат, пока не выпущен валидный. Включается флагом
     * `AUTH_INSECURE_DEBUG_TLS` в local.properties и в release не попадает
     * (BuildConfig.DEBUG == false). Удалить после установки валидного серта.
     */
    private fun applyInsecureDebugTls(builder: OkHttpClient.Builder) {
        val trustAll = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        }
        val sslContext = SSLContext.getInstance("TLS").apply {
            init(null, arrayOf<TrustManager>(trustAll), SecureRandom())
        }
        builder.sslSocketFactory(sslContext.socketFactory, trustAll)
        builder.hostnameVerifier(HostnameVerifier { _, _ -> true })
        Log.w(TAG, "AUTH_INSECURE_DEBUG_TLS=true: проверка TLS-сертификата отключена (только debug!)")
    }
}
