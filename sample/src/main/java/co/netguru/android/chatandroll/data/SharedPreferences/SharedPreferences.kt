package co.netguru.android.chatandroll.data.SharedPreferences

import android.content.Context
import android.preference.PreferenceManager

/**
 * Created by yan-c_000 on 11.02.2018.
 */
class SharedPreferences {
    companion object {
        private const val PUSH_TOKEN_KEY = "token_push"
        private const val TOKEN_KEY = "token"
        private const val USER_ID = "id"

        fun saveOauthCredentials(context: Context, token: String, id: String) {
            saveToken(context, token)
            saveUserId(context, id)
        }

        fun removeOauthCredentials(context: Context) {
            PreferenceManager.getDefaultSharedPreferences(context)
                    .edit()
                    .remove(TOKEN_KEY)
                    .apply()

            PreferenceManager.getDefaultSharedPreferences(context)
                    .edit()
                    .remove(USER_ID)
                    .apply()
        }

        fun getToken(context: Context) : String {
            return PreferenceManager.getDefaultSharedPreferences(context)
                    .getString(TOKEN_KEY, "")
        }

        fun saveToken(context: Context, token: String) {
            PreferenceManager.getDefaultSharedPreferences(context)
                    .edit()
                    .putString(TOKEN_KEY, token)
                    .apply()
        }

        fun hasToken(context: Context): Boolean {
            return PreferenceManager.getDefaultSharedPreferences(context)
                    .contains(TOKEN_KEY)
        }

        private fun saveUserId(context: Context, id: String) {
            PreferenceManager.getDefaultSharedPreferences(context)
                    .edit()
                    .putString(USER_ID, id)
                    .apply()
        }

        fun getUserId(context: Context) : String {
            return PreferenceManager.getDefaultSharedPreferences(context)
                    .getString(USER_ID, "")
        }

        fun savePushToken(context: Context, token: String?) {
            PreferenceManager.getDefaultSharedPreferences(context)
                    .edit()
                    .putString(PUSH_TOKEN_KEY, token)
                    .apply()
        }

        fun hasPushToken(context: Context): Boolean {
            return PreferenceManager.getDefaultSharedPreferences(context)
                    .contains(PUSH_TOKEN_KEY)
        }

        fun getPushToken(context: Context): String {
            return PreferenceManager.getDefaultSharedPreferences(context)
                    .getString(PUSH_TOKEN_KEY, "")
        }
    }
}