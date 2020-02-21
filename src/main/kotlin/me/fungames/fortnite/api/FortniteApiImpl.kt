package me.fungames.fortnite.api

import me.fungames.fortnite.api.exceptions.EpicErrorException
import me.fungames.fortnite.api.model.LoginResponse
import java.io.IOException
import me.fungames.fortnite.api.model.EpicError
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.Retrofit
import okhttp3.OkHttpClient
import com.google.gson.GsonBuilder
import me.fungames.fortnite.api.events.Event
import me.fungames.fortnite.api.model.notification.ProfileNotification
import me.fungames.fortnite.api.network.DefaultInterceptor
import me.fungames.fortnite.api.network.services.*
import okhttp3.Cache
import okhttp3.JavaNetCookieJar
import retrofit2.Response
import java.net.CookieManager
import java.net.CookiePolicy
import java.util.*
import java.util.concurrent.TimeUnit


class FortniteApiImpl internal constructor(): FortniteApi {

    var clientLauncherToken: String = Utils.CLIENT_LAUNCHER_TOKEN

    override var isLoggedIn = false
        private set

    override var language: String = "en"

    private val gson = GsonBuilder()
        .registerTypeAdapter(ProfileNotification::class.java, ProfileNotification.Serializer())
        .create()!!
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(JavaNetCookieJar(CookieManager(null, CookiePolicy.ACCEPT_ALL)))
        .cache(Cache(Utils.cacheDirFile, 4 * 1024 * 1024))
        .addInterceptor(DefaultInterceptor(this))
        .build()

    override val accountPublicService: AccountPublicService
    override val affiliatePublicService : AffiliatePublicService
        get() {
            verifyToken()
            return field
        }
    override val catalogPublicService : CatalogPublicService
        get() {
            verifyToken()
            return field
        }
    override val epicGamesService : EpicGamesService
    override val eventsPublicService : EventsPublicService
        get() {
            verifyToken()
            return field
        }
    override val fortniteContentWebsiteService : FortniteContentWebsiteService
        get() {
            verifyToken()
            return field
        }
    override val fortnitePublicService : FortnitePublicService
        get() {
            verifyToken()
            return field
        }
    override val friendsPublicService : FriendsPublicService
        get() {
            verifyToken()
            return field
        }
    override val launcherPublicService: LauncherPublicService
        get() {
            verifyToken()
            return field
        }
    override val partyService : PartyService
        get() {
            verifyToken()
            return field
        }
    override val personaPublicService : PersonaPublicService
        get() {
            verifyToken()
            return field
        }



    override val accountTokenType: String
        get() {
            checkNotNull(epicAccountTokenType) { "Api is not logged in" }
            return epicAccountTokenType!!
        }

    override val accountToken: String
        get() {
            checkNotNull(epicAccountAccessToken) { "Api is not logged in" }
            return epicAccountAccessToken!!
        }

    override val accountExpiresAtMillis: Long
        get() {
            checkNotNull(epicAccountExpiresAtMillis) { "Api is not logged in" }
            return epicAccountExpiresAtMillis!!
        }

    internal var email : String? = null
    internal var password : String? = null

    private var epicAccountTokenType : String? = null
    private var accountExpiresAt: Date? = null
    private var epicAccountExpiresAtMillis: Long? = null
    private var accountRefreshToken: String? = null
    private var epicAccountAccessToken: String? = null
    private var accountId: String? = null

    init {
        val retrofitBuilder = Retrofit.Builder().client(httpClient).addConverterFactory(GsonConverterFactory.create(gson))
        accountPublicService =
        retrofitBuilder.baseUrl(AccountPublicService.BASE_URL).build().create(
            AccountPublicService::class.java)
        affiliatePublicService =
            retrofitBuilder.baseUrl(AffiliatePublicService.BASE_URL).build().create(
                AffiliatePublicService::class.java)
        catalogPublicService =
            retrofitBuilder.baseUrl(CatalogPublicService.BASE_URL).build().create(
                CatalogPublicService::class.java)
        epicGamesService =
            retrofitBuilder.baseUrl(EpicGamesService.BASE_URL).build().create(
                EpicGamesService::class.java)
        eventsPublicService =
            retrofitBuilder.baseUrl(EventsPublicService.BASE_URL).build().create(
                EventsPublicService::class.java)
        fortniteContentWebsiteService =
            retrofitBuilder.baseUrl(FortniteContentWebsiteService.BASE_URL).build().create(
                FortniteContentWebsiteService::class.java)
        fortnitePublicService =
            retrofitBuilder.baseUrl(FortnitePublicService.BASE_URL).build().create(
                FortnitePublicService::class.java)
        friendsPublicService =
            retrofitBuilder.baseUrl(FriendsPublicService.BASE_URL).build().create(
                FriendsPublicService::class.java)
        launcherPublicService =
            retrofitBuilder.baseUrl(LauncherPublicService.BASE_URL).build().create(
                LauncherPublicService::class.java)
        partyService =
            retrofitBuilder.baseUrl(PartyService.BASE_URL).build().create(
                PartyService::class.java)
        personaPublicService =
            retrofitBuilder.baseUrl(PersonaPublicService.BASE_URL).build().create(
                PersonaPublicService::class.java)
    }

    @Throws(EpicErrorException::class)
    override fun loginClientCredentials() {
        val loginRequest =
            this.accountPublicService.grantToken("basic $clientLauncherToken", "client_credentials", emptyMap(), false)
        try {
            val response = loginRequest.execute()
            if (response.isSuccessful)
                loginSucceeded(response)
            else
                throw EpicErrorException(EpicError.parse(response))

        } catch (e: IOException) {
            throw EpicErrorException(e)
        }
    }

    override fun login(rememberMe: Boolean) {
        val loginEmail = email
        val loginPassword = password
        if (loginEmail == null || loginPassword == null) {
            return loginClientCredentials()
        }
        val csrf = this.epicGamesService.csrfToken().execute()
        if (!csrf.isSuccessful)
            throw EpicErrorException(EpicError.parse(csrf))
        val xsrfToken = csrf.headers().toMultimap()["Set-Cookie"]?.first { it.startsWith("XSRF-TOKEN=") }?.substringAfter("XSRF-TOKEN=")?.substringBefore(';')
            ?: throw EpicErrorException("Failed to obtain xsrf token")
        val login = this.epicGamesService.login(mapOf("email" to loginEmail, "password" to loginPassword, "rememberMe" to rememberMe.toString()), xsrfToken).execute()
        if (login.code() == 409)
            return login(rememberMe)
        if (!login.isSuccessful)
            throw EpicErrorException(EpicError.parse(login))
        val exchange = this.epicGamesService.exchange().execute()
        if (!exchange.isSuccessful)
            throw EpicErrorException(EpicError.parse(exchange))
        val exchangeCode = exchange.body()!!.code
        val auth = this.accountPublicService.grantToken("basic ${Utils.CLIENT_LAUNCHER_TOKEN}", "exchange_code", mapOf("exchange_code" to exchangeCode, "token_type" to "eg1"), false).execute()
        if (!auth.isSuccessful)
            throw EpicErrorException(EpicError.parse(auth))
        loginSucceeded(auth)
    }

    private fun verifyToken() {
        require(isLoggedIn) { "Api is not logged in" }
        if (System.currentTimeMillis() >= this.epicAccountExpiresAtMillis!!) {
            if (this.accountRefreshToken == null) {
                login()
                return
            }
            val refresh = this.accountPublicService.grantToken("basic ${Utils.CLIENT_LAUNCHER_TOKEN}", "refresh_token", mapOf("refresh_token" to this.accountRefreshToken!!), null).execute()
            if (!refresh.isSuccessful) {
                System.err.println("Failed to use refresh token")
                System.err.println(EpicError.parse(refresh).errorMessage)
                System.err.println("Attempting to relogin")
                login()
                return
            }
            loginSucceeded(refresh)
        }
    }

    private fun loginSucceeded(response: Response<LoginResponse>) {
        val data = response.body()!!
        this.epicAccountAccessToken = data.access_token
        this.accountExpiresAt = data.expires_at
        this.epicAccountExpiresAtMillis = System.currentTimeMillis() + TimeUnit.MILLISECONDS.convert(data.expires_in.toLong(), TimeUnit.SECONDS)
        this.accountId = data.account_id
        this.accountRefreshToken = data.refresh_token
        this.epicAccountTokenType = data.token_type
        this.isLoggedIn = true
    }

    @Throws(EpicErrorException::class)
    override fun logout() {
    }

    override fun fireEvent(event: Event) {
        println("Received " + event::class.java.simpleName)
    }
}