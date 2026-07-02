package top.niunaijun.blackboxa.view.account

import android.accounts.Account
import android.accounts.AccountManager
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import top.niunaijun.blackbox.fake.frameworks.BAccountManager
import top.niunaijun.blackboxa.R

/**
 * Sandbox replacement for Android's system ChooseTypeAndAccountActivity.
 *
 * The system picker runs inside system_server and reads the host device's
 * AccountManager database, so sandboxed apps end up seeing the host owner's
 * Google accounts. IActivityManagerProxy / ActivityManagerCommonProxy
 * intercepts the picker intent and substitutes this Activity, which reads
 * the sandbox-only BAccountManagerService instead.
 *
 * Incoming extras follow the same contract as ChooseTypeAndAccountActivity
 * so callers built with AccountManager.newChooseAccountIntent() keep working.
 * The result Intent uses AccountManager.KEY_ACCOUNT_NAME / KEY_ACCOUNT_TYPE
 * as documented.
 */
class SandboxAccountPickerActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "SandboxAccountPicker"

        // Extras injected by ActivityManagerCommonProxy.
        const val EXTRA_SANDBOX_USER_ID = "openva.sandbox.userId"
        const val EXTRA_SANDBOX_CALLING_PKG = "openva.sandbox.callingPkg"

        // ChooseTypeAndAccountActivity public extras (kept as raw strings so
        // we don't pull in @hide APIs).
        private const val EXTRA_ALLOWABLE_ACCOUNT_TYPES = "allowableAccountTypes"
        private const val EXTRA_DESCRIPTION_TEXT_OVERRIDE = "descriptionTextOverride"
    }

    private val displayedAccounts = mutableListOf<Account>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sandbox_account_picker)

        // Default result if the user dismisses.
        setResult(RESULT_CANCELED)

        val userId = intent.getIntExtra(EXTRA_SANDBOX_USER_ID, -1)
        val callingPkg = intent.getStringExtra(EXTRA_SANDBOX_CALLING_PKG) ?: ""
        val allowableTypes = intent.getStringArrayExtra(EXTRA_ALLOWABLE_ACCOUNT_TYPES)
        val descriptionOverride = intent.getStringExtra(EXTRA_DESCRIPTION_TEXT_OVERRIDE)

        if (userId < 0) {
            Log.w(TAG, "Missing sandbox userId, finishing.")
            finish()
            return
        }

        val titleView = findViewById<TextView>(R.id.sandbox_picker_title)
        val descriptionView = findViewById<TextView>(R.id.sandbox_picker_description)
        val emptyView = findViewById<TextView>(R.id.sandbox_picker_empty)
        val listView = findViewById<ListView>(R.id.sandbox_picker_list)
        val cancelButton = findViewById<View>(R.id.sandbox_picker_cancel)

        titleView.text = getString(R.string.sandbox_picker_title)
        descriptionView.text = descriptionOverride
            ?: getString(R.string.sandbox_picker_description, callingPkg)

        displayedAccounts.clear()
        displayedAccounts.addAll(loadAccounts(userId, allowableTypes))

        if (displayedAccounts.isEmpty()) {
            listView.visibility = View.GONE
            emptyView.visibility = View.VISIBLE
            emptyView.text = getString(R.string.sandbox_picker_empty)
        } else {
            listView.visibility = View.VISIBLE
            emptyView.visibility = View.GONE
            val rows = displayedAccounts.map { "${it.name}\n${it.type}" }
            listView.adapter = ArrayAdapter(
                this,
                android.R.layout.simple_list_item_2,
                android.R.id.text1,
                rows
            )
            listView.onItemClickListener =
                AdapterView.OnItemClickListener { _, _, position, _ ->
                    if (position in displayedAccounts.indices) {
                        deliverResult(displayedAccounts[position])
                    }
                }
        }

        cancelButton.setOnClickListener {
            setResult(RESULT_CANCELED)
            finish()
        }
    }

    private fun loadAccounts(userId: Int, allowableTypes: Array<String>?): List<Account> {
        val service = try {
            BAccountManager.get().service
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to obtain BAccountManagerService: ${t.message}")
            null
        } ?: return emptyList()

        val types: List<String?> = if (allowableTypes != null && allowableTypes.isNotEmpty()) {
            allowableTypes.toList()
        } else {
            // null == any type, matching BUserAccounts.getAccountsByType(null)
            listOf(null)
        }

        val result = mutableListOf<Account>()
        for (type in types) {
            try {
                val accounts = service.getAccountsAsUser(type, userId) ?: continue
                for (account in accounts) {
                    if (result.none { it.name == account.name && it.type == account.type }) {
                        result.add(account)
                    }
                }
            } catch (t: Throwable) {
                Log.w(TAG, "getAccountsAsUser(type=$type, user=$userId) failed: ${t.message}")
            }
        }
        return result
    }

    private fun deliverResult(account: Account) {
        val data = Intent().apply {
            putExtra(AccountManager.KEY_ACCOUNT_NAME, account.name)
            putExtra(AccountManager.KEY_ACCOUNT_TYPE, account.type)
        }
        setResult(RESULT_OK, data)
        finish()
    }
}
