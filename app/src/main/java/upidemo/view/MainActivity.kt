package upidemo.view

import android.app.AlertDialog
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import citcon.cpay.R
import citcon.cpay.databinding.ActivityMainBinding
import com.citconpay.sdk.data.model.CPayMethodType
import com.citconpay.sdk.data.model.CPayOrderResult
import com.citconpay.sdk.utils.Constant
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
    private val mDemoViewModel: DemoViewModel by lazy {
        ViewModelProvider(this, ViewModelProvider.AndroidViewModelFactory(application))
            .get(DemoViewModel::class.java)
    }

    private val mStartForResult =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->

            val alertdialog = AlertDialog.Builder(this)
                .setPositiveButton("Quit", null)

            if (result.resultCode == RESULT_OK) {
                result.data?.let {
                    val orderResult = it.getSerializableExtra(Constant.PAYMENT_RESULT)
                            as CPayOrderResult

                    alertdialog.setMessage(
                        String.format(
                            Locale.CANADA, "this is merchant demo APP\n paid %s %d",
                            orderResult.currency, orderResult.amount
                        )
                    ).create().show()
                }

            } else {
                val message: String
                if (result.data == null) {
                    message = "this is merchant demo APP\n payment cancelled by user"
                } else {
                    val error: CPayOrderResult =
                        result.data!!.getSerializableExtra(Constant.PAYMENT_RESULT) as CPayOrderResult
                    message = """this is merchant demo APP
                             payment cancelled :
                             ${error.message} - ${error.code}"""
                }
                alertdialog.setMessage(message).create().show()
            }
        }

    companion object {
        internal const val DEFAULT_CONSUMER_ID = "115646448"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        val navController = findNavController(R.id.nav_host_fragment_content_main)
        appBarConfiguration = AppBarConfiguration(navController.graph)
        setupActionBarWithNavController(navController, appBarConfiguration)

        binding.fab.setOnClickListener {
            launchDropin(mDemoViewModel.getPaymentMethod())
        }

        mDemoViewModel.mChargeToken.observe(this) {
            mStartForResult.launch(mDemoViewModel.buildDropInRequest(mDemoViewModel.getPaymentMethod()).getIntent(this))
        }
        mDemoViewModel.mErrorMessage.observe(this) {
            val message = """this is merchant demo APP
                             payment cancelled :
                             ${it.message} - ${it.code}"""
            AlertDialog.Builder(this)
                .setPositiveButton("Quit", null)
                .setMessage(message).create().show()
        }
    }

    private fun launchDropin(type: CPayMethodType) {
        if(type == CPayMethodType.UNKNOWN || type == CPayMethodType.PAYPAL || type == CPayMethodType.PAY_WITH_VENMO) {
            mDemoViewModel.mAccessToken.value?.let(mDemoViewModel::getChargeToken)
        } else {
            mStartForResult.launch(mDemoViewModel.buildDropInRequest(type).getIntent(this))
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.action_settings -> true
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration)
                || super.onSupportNavigateUp()
    }
}