package uz.robutpit.znatoki.di

import android.content.Context
import androidx.room.Room
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.Authenticator
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import uz.robutpit.znatoki.BuildConfig
import uz.robutpit.znatoki.data.local.AppDatabase
import uz.robutpit.znatoki.data.local.DebugPrefs
import uz.robutpit.znatoki.data.mock.FakeGameServer
import uz.robutpit.znatoki.data.remote.ApiService
import uz.robutpit.znatoki.data.remote.TokenRefresher
import uz.robutpit.znatoki.data.remote.RemoteGameGateway
import uz.robutpit.znatoki.data.remote.TokenStore
import uz.robutpit.znatoki.domain.GameGateway
import uz.robutpit.znatoki.push.NoopPushProvider
import uz.robutpit.znatoki.push.PushProvider
import uz.robutpit.znatoki.voice.LiveKitVoiceManager
import uz.robutpit.znatoki.voice.MockVoiceManager
import uz.robutpit.znatoki.voice.VoiceManager
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @Singleton
    fun json(): Json = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true; coerceInputValues = true }

    @Provides @Singleton
    fun db(@ApplicationContext c: Context): AppDatabase = Room.databaseBuilder(c, AppDatabase::class.java, "znatoki.db").fallbackToDestructiveMigration(true).build()

    @Provides @Singleton @Named("useMock")
    fun useMock(@ApplicationContext c: Context): Boolean = DebugPrefs.useMock(c, BuildConfig.USE_MOCK)

    @Provides @Singleton
    fun tokenRefresher(tokens: TokenStore, json: Json): TokenRefresher {
        val base = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build()
        val api by lazy {
            Retrofit.Builder().baseUrl(BuildConfig.SERVER_BASE_URL + "/api/v1/").client(base)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType())).build().create(ApiService::class.java)
        }
        return TokenRefresher(tokens) { api }
    }

    @Provides @Singleton
    fun okHttp(tokens: TokenStore, refresher: TokenRefresher): OkHttpClient {
        val base = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS)
            .pingInterval(20, TimeUnit.SECONDS)
            .build()
        // Обновление access-токена по 401 — по одному, без логирования токенов
        val authenticator = Authenticator { _, response ->
            if (response.priorResponse != null) return@Authenticator null
            if (response.request.url.encodedPath.contains("/auth/")) return@Authenticator null
            val failed = response.request.header("Authorization")?.removePrefix("Bearer ")
            when (val r = refresher.refresh(failed)) {
                is TokenRefresher.Result.Ok -> response.request.newBuilder().header("Authorization", "Bearer ${r.tokens.accessToken}").build()
                else -> null
            }
        }
        return base.newBuilder()
            .addInterceptor { chain ->
                val t = tokens.tokens?.accessToken
                val req = chain.request().newBuilder().apply { if (t != null) header("Authorization", "Bearer $t") }
                    .header("X-Client-Version", BuildConfig.VERSION_CODE.toString())
                    .build()
                chain.proceed(req)
            }
            .authenticator(authenticator)
            .apply {
                if (BuildConfig.DEBUG) addInterceptor(HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BASIC
                    redactHeader("Authorization")
                })
            }
            .build()
    }

    @Provides @Singleton
    fun api(client: OkHttpClient, json: Json): ApiService = Retrofit.Builder()
        .baseUrl(BuildConfig.SERVER_BASE_URL + "/api/v1/")
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build().create(ApiService::class.java)

    @Provides @Singleton
    fun gateway(
        @ApplicationContext c: Context, @Named("useMock") mock: Boolean,
        api: dagger.Lazy<ApiService>, tokens: TokenStore, client: dagger.Lazy<OkHttpClient>, json: Json, refresher: TokenRefresher,
    ): GameGateway = if (mock) FakeGameServer(c) else RemoteGameGateway(api.get(), tokens, BuildConfig.SERVER_BASE_URL, client.get(), json, refresher)

    @Provides @Singleton
    fun voice(@ApplicationContext c: Context, @Named("useMock") mock: Boolean, gateway: GameGateway): VoiceManager =
        if (mock) MockVoiceManager() else LiveKitVoiceManager(c, gateway)

    @Provides @Singleton
    fun push(): PushProvider = NoopPushProvider()
}
