package com.mntechnique.otpmobileauth.auth

import android.accounts.*
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.util.Log

import org.json.JSONObject

import android.accounts.AccountManager.KEY_BOOLEAN_RESULT

/**
 * Created with IntelliJ IDEA.
 * User: Frappe
 * Date: 19/03/13
 * Time: 18:58
 */
class FrappeAuthenticator(private val mContext: Context) : AbstractAccountAuthenticator(mContext) {

    private val TAG = "OAuth2Authenticator"
    internal var authToken: String? = null

    @Throws(NetworkErrorException::class)
    override fun addAccount(response: AccountAuthenticatorResponse,
                            accountType: String,
                            authTokenType: String?,
                            requiredFeatures: Array<String>?,
                            options: Bundle?): Bundle? {
        Log.d(TAG, "> addAccount")

        val intent = Intent(mContext, AuthenticatorActivity::class.java)
        intent.putExtra(AuthenticatorActivity.ARG_ACCOUNT_TYPE, accountType)
        intent.putExtra(AuthenticatorActivity.ARG_AUTH_TYPE, authTokenType)
        intent.putExtra(AuthenticatorActivity.ARG_IS_ADDING_NEW_ACCOUNT, true)
        intent.putExtra(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE, response)

        val bundle = Bundle()
        bundle.putParcelable(AccountManager.KEY_INTENT, intent)
        return bundle
    }

    @Throws(NetworkErrorException::class)
    override fun getAuthToken(response: AccountAuthenticatorResponse, account: Account, authTokenType: String, options: Bundle): Bundle {
        Log.d(TAG, "> getAuthToken")

        // If the caller requested an authToken type we don't support, then
        // return an error
        if (authTokenType != AccountGeneral.AUTHTOKEN_TYPE_READ_ONLY && authTokenType != AccountGeneral.AUTHTOKEN_TYPE_FULL_ACCESS) {
            var result = Bundle()
            result = getBundle("invalid_token_type", AccountGeneral.AUTHTOKEN_TYPE_FULL_ACCESS, account, response)
            return result
        }

        // Extract the username and password from the Account Manager, and ask
        // the server for an appropriate AuthToken.
        val am = AccountManager.get(mContext)

        authToken = am.getUserData(account, "authtoken")
        val accessToken = am.getUserData(account, "accessToken")
        val refreshToken = am.getUserData(account, "refreshToken")

        val serverURL = am.getUserData(account, "serverURL")
        val CLIENT_ID = am.getUserData(account, "clientId")
        val REDIRECT_URI = am.getUserData(account, "redirectURI")
        val authEndpoint = am.getUserData(account, "authEndpoint")
        val tokenEndpoint = am.getUserData(account, "tokenEndpoint")
        val openIDEndpoint = am.getUserData(account, "openIDEndpoint")
        val clientSecret = am.getUserData(account, "clientSecret")
        val oauth2Scope = am.getUserData(account, "oauth2Scope")
        val expiresIn = am.getUserData(account, "expiresIn")
        val tokenExpiryTime = am.getUserData(account, "tokenExpiryTime")

        Log.d("OAuth2Authenticator", TAG + "> at isnull - " + accessToken)
        Log.d("OAuth2Authenticator", TAG + "> expiryTime - " + tokenExpiryTime)

        //Initiate Scribe Java Auth Service
        val accountGeneral = AccountGeneral(
                oauth2Scope, CLIENT_ID, clientSecret, serverURL,
                REDIRECT_URI, authEndpoint, tokenEndpoint
        )

        val openIDProfile = AccountGeneral.sServerAuthenticate.getOpenIDProfile(accessToken, serverURL, openIDEndpoint)
        Log.d("OAuth2Authenticator", accountGeneral.toString())

        // Lets give another try to authenticate the user
        if (TextUtils.isEmpty(accessToken) || openIDProfile.length() == 0) {
            try {
                Log.d(TAG, "> re-authenticating with the refresh token")
                val authMethod = JSONObject()
                authMethod.put("type", "refresh")
                authMethod.put("refresh_token", refreshToken)
                authToken = AccountGeneral.sServerAuthenticate.userSignIn(authMethod, CLIENT_ID, REDIRECT_URI)
                val bearerToken = JSONObject(authToken)
                am.setAuthToken(account, AccountGeneral.AUTHTOKEN_TYPE_FULL_ACCESS, authToken)
                am.setUserData(account, "authtoken", authToken)
                am.setUserData(account, "refreshToken", bearerToken.getString("refresh_token"))
                am.setUserData(account, "accessToken", bearerToken.getString("access_token"))
            } catch (e: Exception) {
                Log.d(TAG, e.message)

                //Clearing Auth Token due to error while refreshing
                authToken = null
            }

        }
        // If we get an authToken - we return it
        if (!TextUtils.isEmpty(authToken)) {
            var result = Bundle()
            result = getBundle("valid", AccountGeneral.AUTHTOKEN_TYPE_FULL_ACCESS, account, response)
            result.putString(AccountManager.KEY_AUTHTOKEN, authToken)
            return result
        }
        // If we get here, then we couldn't access the user's password - so we
        // need to re-prompt them for their credentials. We do that by creating
        // an intent to display our AuthenticatorActivity.
        val result = getBundle("new_intent", AccountGeneral.AUTHTOKEN_TYPE_FULL_ACCESS, account, response)
        return result

    }

    fun getBundle(bundleType: String, authTokenType: String, account: Account, response: AccountAuthenticatorResponse): Bundle {
        // bundleType - invalid_token_type, new_intent, valid
        val result = Bundle()
        if (bundleType === "invalid_token_type") {
            result.putString(AccountManager.KEY_ERROR_MESSAGE, "invalid authTokenType")
        } else if (bundleType === "new_intent") {
            val intent = Intent(mContext, AuthenticatorActivity::class.java)
            intent.putExtra(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE, response)
            intent.putExtra(AuthenticatorActivity.ARG_ACCOUNT_TYPE, account.type)
            intent.putExtra(AuthenticatorActivity.ARG_AUTH_TYPE, authTokenType)
            intent.putExtra(AuthenticatorActivity.ARG_ACCOUNT_NAME, account.name)
            result.putParcelable(AccountManager.KEY_INTENT, intent)
        } else if (bundleType === "valid") {
            result.putString(AccountManager.KEY_ACCOUNT_NAME, account.name)
            result.putString(AccountManager.KEY_ACCOUNT_TYPE, account.type)
        }
        return result
    }

    override fun getAuthTokenLabel(authTokenType: String): String {
        if (AccountGeneral.AUTHTOKEN_TYPE_FULL_ACCESS == authTokenType)
            return AccountGeneral.AUTHTOKEN_TYPE_FULL_ACCESS_LABEL
        else if (AccountGeneral.AUTHTOKEN_TYPE_READ_ONLY == authTokenType)
            return AccountGeneral.AUTHTOKEN_TYPE_READ_ONLY_LABEL
        else
            return authTokenType + " (Label)"
    }

    @Throws(NetworkErrorException::class)
    override fun hasFeatures(response: AccountAuthenticatorResponse, account: Account, features: Array<String>): Bundle {
        val result = Bundle()
        result.putBoolean(KEY_BOOLEAN_RESULT, false)
        return result
    }

    override fun editProperties(response: AccountAuthenticatorResponse, accountType: String): Bundle? {
        return null
    }

    @Throws(NetworkErrorException::class)
    override fun confirmCredentials(response: AccountAuthenticatorResponse, account: Account, options: Bundle): Bundle? {
        return null
    }

    @Throws(NetworkErrorException::class)
    override fun updateCredentials(response: AccountAuthenticatorResponse, account: Account, authTokenType: String, options: Bundle): Bundle? {
        return null
    }
}
