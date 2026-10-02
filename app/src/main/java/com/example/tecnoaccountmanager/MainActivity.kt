package com.example.tecnoaccountmanager

import android.accounts.Account
import android.accounts.AccountManager
import android.accounts.AccountManagerCallback
import android.accounts.AccountManagerFuture
import android.accounts.AuthenticatorException
import android.accounts.OperationCanceledException
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private val targetAccountType = "com.TECNO.id.account.type"
    private val targetAccountName = "Tecno Hack"
    private val permissionRequestCode = 1001

    private lateinit var tvAccountType: TextView
    private lateinit var tvAccountCount: TextView
    private lateinit var tvAccountName: TextView
    private lateinit var btnRefresh: Button
    private lateinit var btnRemoveAccount: Button
    private lateinit var tvResult: TextView

    private var matchedAccount: Account? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvAccountType = findViewById(R.id.tvAccountType)
        tvAccountCount = findViewById(R.id.tvAccountCount)
        tvAccountName = findViewById(R.id.tvAccountName)
        btnRefresh = findViewById(R.id.btnRefresh)
        btnRemoveAccount = findViewById(R.id.btnRemoveAccount)
        tvResult = findViewById(R.id.tvResult)

        btnRefresh.setOnClickListener {
            checkPermissionsAndDetectAccount()
        }

        btnRemoveAccount.setOnClickListener {
            matchedAccount?.let { account ->
                requestAccountRemoval(account)
            } ?: run {
                tvResult.text = "Estado: No hay ninguna cuenta seleccionada para eliminar."
            }
        }

        checkPermissionsAndDetectAccount()
    }

    private fun checkPermissionsAndDetectAccount() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.GET_ACCOUNTS)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(android.Manifest.permission.GET_ACCOUNTS),
                    permissionRequestCode
                )
            } else {
                detectAccounts()
            }
        } else {
            detectAccounts()
        }
    }

    private fun detectAccounts() {
        val accountManager = AccountManager.get(this)
        tvResult.text = "Estado: Consultando AccountManager..."

        try {
            val accounts = accountManager.getAccountsByType(targetAccountType)
            val totalCount = accounts.size
            tvAccountType.text = "Tipo de cuenta buscado: $targetAccountType"
            tvAccountCount.text = "Cantidad de cuentas encontradas: $totalCount"

            matchedAccount = accounts.firstOrNull { it.name == targetAccountName }
                ?: accounts.firstOrNull()

            if (matchedAccount != null) {
                val found = matchedAccount!!
                tvAccountName.text = "Nombre encontrado: ${found.name}"
                
                if (found.name == targetAccountName) {
                    tvResult.text = "Estado: Cuenta '$targetAccountName' localizada. Lista para solicitar eliminación."
                } else {
                    tvResult.text = "Estado: Cuenta de tipo $targetAccountType localizada ('${found.name}')."
                }
                btnRemoveAccount.isEnabled = true
            } else {
                tvAccountName.text = "Nombre encontrado: Ninguna"
                btnRemoveAccount.isEnabled = false
                tvResult.text = "Estado: No se encontraron cuentas de tipo '$targetAccountType'."
            }
        } catch (e: Exception) {
            tvResult.text = "Error/Excepción al consultar cuentas: ${e.localizedMessage}"
            btnRemoveAccount.isEnabled = false
        }
    }

    private fun requestAccountRemoval(account: Account) {
        val accountManager = AccountManager.get(this)
        tvResult.text = "Estado: Solicitando eliminación de '${account.name}' mediante AccountManager.removeAccount..."
        btnRemoveAccount.isEnabled = false

        val callback = AccountManagerCallback<Bundle> { future ->
            handleRemovalFutureResult(future, account)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            accountManager.removeAccount(
                account,
                this,
                callback,
                null
            )
        } else {
            @Suppress("DEPRECATION")
            accountManager.removeAccount(account, { future ->
                try {
                    val success = future.result
                    verifyRemovalResult(success, account)
                } catch (e: OperationCanceledException) {
                    tvResult.text = "Estado: Operación cancelada por el usuario o por el sistema."
                } catch (e: AuthenticatorException) {
                    tvResult.text = "Estado: Rechazada por el autenticador / Autenticación requerida (${e.localizedMessage})."
                } catch (e: IOException) {
                    tvResult.text = "Error I/O durante la eliminación: ${e.localizedMessage}"
                } catch (e: Exception) {
                    tvResult.text = "Excepción/Error al eliminar: ${e.localizedMessage}"
                }
            }, null)
        }
    }

    private fun handleRemovalFutureResult(future: AccountManagerFuture<Bundle>, targetAccount: Account) {
        try {
            val bundle = future.result
            val intent = bundle.getParcelable<android.content.Intent>(AccountManager.KEY_INTENT)
            
            if (intent != null) {
                tvResult.text = "Estado: Autenticación / Confirmación requerida por el sistema o autenticador TECNO."
                return
            }

            val success = bundle.getBoolean(AccountManager.KEY_BOOLEAN_RESULT, false)
            verifyRemovalResult(success, targetAccount)

        } catch (e: OperationCanceledException) {
            tvResult.text = "Estado: Operación cancelada."
        } catch (e: AuthenticatorException) {
            tvResult.text = "Estado: Operación rechazada por el autenticador o autenticación requerida (${e.localizedMessage})."
        } catch (e: IOException) {
            tvResult.text = "Excepción/Error de red o I/O: ${e.localizedMessage}"
        } catch (e: Exception) {
            tvResult.text = "Excepción/Error: ${e.localizedMessage}"
        }
    }

    private fun verifyRemovalResult(success: Boolean, targetAccount: Account) {
        if (!success) {
            tvResult.text = "Estado: Operación rechazada o no completada por el autenticador/sistema."
            return
        }

        tvResult.text = "Estado: AccountManager devolvió éxito. Verificando si la cuenta realmente desapareció..."
        
        val accountManager = AccountManager.get(this)
        val remainingAccounts = accountManager.getAccountsByType(targetAccountType)
        val stillExists = remainingAccounts.any { it.name == targetAccount.name }

        if (!stillExists) {
            tvResult.text = "Resultado: Eliminación exitosa. La cuenta '${targetAccount.name}' ha desaparecido del sistema."
            detectAccounts()
        } else {
            tvResult.text = "Resultado: Operación no completada. La cuenta '${targetAccount.name}' sigue presente en AccountManager."
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == permissionRequestCode) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                detectAccounts()
            } else {
                tvResult.text = "Estado: Permiso GET_ACCOUNTS denegado."
            }
        }
    }
}
