package upidemo.view

import android.app.AlertDialog
import android.os.Bundle
import android.text.format.DateFormat
import android.view.Menu
import android.view.MenuItem
import android.view.View
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
import com.citconpay.sdk.data.model.CPayRequest
import com.citconpay.sdk.data.model.CPayResult
import com.citconpay.sdk.data.repository.CPayENVMode
import com.citconpay.sdk.utils.Constant
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    lateinit var binding: ActivityMainBinding
    private val mDemoViewModel: DemoViewModel by lazy {
        ViewModelProvider(this, ViewModelProvider.AndroidViewModelFactory(application))[DemoViewModel::class.java]
    }

    private val mStartForResult =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->

            val alertdialog = AlertDialog.Builder(this)
                .setPositiveButton("Quit", null)

            if (result.resultCode == RESULT_OK) {
                result.data?.let {
                    val orderResult = it.getSerializableExtra(Constant.PAYMENT_RESULT)
                            as CPayResult

                    mDemoViewModel.mResultString.postValue(orderResult.toString())

                    alertdialog.setMessage(
                        String.format(
                            Locale.CANADA, "this is merchant demo APP\n\n paid %s %d\n" +
                                    "transaction: %s\n created at %s",
                            orderResult.currency, orderResult.amount, orderResult.transactionId,
                            DateFormat.format("MM/dd/yyyy hh:mm:ss a", Date(orderResult.time)).toString()
                        )).create().show()

                }

            } else {
                val message: String = if (result.data == null) {
                    "this is merchant demo APP\n\n payment cancelled by user"
                } else {
                    val error: CPayResult =
                        result.data!!.getSerializableExtra(Constant.PAYMENT_RESULT) as CPayResult
                    """this is merchant demo APP
                                    
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

        binding.fab.visibility = View.GONE
        binding.fab.setOnClickListener {
            launchDropin(mDemoViewModel.getPaymentMethod())
        }

        mDemoViewModel.mChargeToken.observe(this) {
            mDemoViewModel.buildDropInRequest(mDemoViewModel.getPaymentMethod()).start(this, mStartForResult)
        }
        mDemoViewModel.mErrorMessage.observe(this) {
            val message = """this is merchant demo APP
                             payment cancelled :
                             ${it.message} - ${it.code}"""
            AlertDialog.Builder(this)
                .setPositiveButton("Quit", null)
                .setMessage(message).create().show()
        }


        /*

        // test alipay
        var reference = System.currentTimeMillis().toString();
        var accessToken = "UPI_eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpZCI6ImM4YTQ2ODgzZWJlODE0YWRmZjdmZTNhYWNlZGRmMTIxZTQ1NmQzYjlkN2I1MTk4ZjcwNjQxNTgzMjVjNjM0MWYiLCJpYXQiOjE2ODU5NTY3NDcsImV4cCI6MTY4NjA0MzE0N30.VZnc3SjXP8X28bblRUoqWS3PUsaZEvGeX91Z9FhzbRA"
        CPayRequest.UPIOrderBuilder
            .accessToken(accessToken)
            .reference(reference)
            .consumerID("8888")
            .currency("CNY")
//            .currency("USD")
            .amount("10")
            .callbackURL("https://exampe.com/mobile")
            .ipnURL("https://exampe.com/ipn")
            .mobileURL("https://exampe.com/mobile")
            .cancelURL("https://exampe.com/cancel")
            .failURL("https://exampe.com/fail")
            .setAllowDuplicate(true)
            .paymentMethod(CPayMethodType.ALI)
            .country(Locale.CHINA)
//            .country(Locale.US)
//            .setExpiry(System.currentTimeMillis()+mTimeout.toLong())
            .build(CPayENVMode.UAT).start(this, mStartForResult);
//        https://api.sandbox.citconpay.com/v1/

        */
    }

    private fun launchDropin(type: CPayMethodType) {
        if(type == CPayMethodType.UNKNOWN || type == CPayMethodType.PAYPAL || type == CPayMethodType.PAY_WITH_VENMO) {
            mDemoViewModel.mAccessToken.value?.let(mDemoViewModel::getChargeToken)
        } else {
            mDemoViewModel.buildDropInRequest(type).start(this, mStartForResult)
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