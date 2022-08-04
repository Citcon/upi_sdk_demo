package upidemo.view

import android.app.Application
import android.widget.RadioGroup
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import citcon.cpay.R
import com.citconpay.sdk.data.model.*
import com.citconpay.sdk.data.model.CPayRequest.BillingAdressBuilder.city
import com.citconpay.sdk.data.model.CPayRequest.ConsumerBuilder.firstName
import com.citconpay.sdk.data.repository.CPayENVMode
import com.google.gson.GsonBuilder
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.apache.commons.lang3.RandomStringUtils
import org.json.JSONObject
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import upidemo.model.*
import java.util.*
import java.util.concurrent.TimeUnit


class DemoViewModel(application: Application) : AndroidViewModel(application) {
    val mAccessToken by lazy { MutableLiveData<String>() }
    val mChargeToken by lazy { MutableLiveData<String>() }
    private lateinit var mReference: String
    internal var mConsumerID: String = MainActivity.DEFAULT_CONSUMER_ID
    private lateinit var mChosePaymentMethod: CPayMethodType
    internal var mIs3DS: Boolean = false
    var mAmount = "1"
    var mTimeout = "60000"
    lateinit var mCallback : String
    val mResultString by lazy { MutableLiveData<String>() }
    val mCurrencyIndex = MutableLiveData(0)
    val mCountryIndex = MutableLiveData(0)
    val mErrorMessage by lazy { MutableLiveData<ErrorMessage>() }
    var mCurrency = application.resources.getStringArray(R.array.currency_list)[0]!!
    var mCountry = application.resources.getStringArray(R.array.country_list)[0]!!

    object RetrofitClient {

        private const val BASE_URL = /*"https://api.qa01.citconpay.com/v1/"*/"https://api.sandbox.citconpay.com/v1/"

        private val okHttpClient = OkHttpClient.Builder()
            .callTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BODY))
            .build()

        private val retrofit: Retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .client(okHttpClient)
            .build()

        val apiService: CitconUPIAPIService = retrofit.create(CitconUPIAPIService::class.java)

    }

    fun getReference(): String {
        return mReference
    }

    internal fun setPaymentMethod(groupID: Int, id: Int) {
        groupID.let {
            mChosePaymentMethod = if(it == R.id.radiogroup_payment_cn) {
                when (id) {
                    R.id.radioButton_upop -> CPayMethodType.UNIONPAY
                    R.id.radioButton_wechat -> CPayMethodType.WECHAT
                    R.id.radioButton_alipay -> CPayMethodType.ALI
                    else -> CPayMethodType.WECHAT
                }
            } else {
                when (id) {
                    R.id.radioButton_paypal -> CPayMethodType.PAYPAL
                    R.id.radioButton_venmo -> CPayMethodType.PAY_WITH_VENMO
                    R.id.radioButton_credit -> CPayMethodType.UNKNOWN
                    else -> CPayMethodType.UNKNOWN
                }
            }
        }
    }

    fun setPaymentMethod(type: CPayMethodType) {
        mChosePaymentMethod = type
    }


    fun getPaymentMethod(): CPayMethodType {
        return mChosePaymentMethod
    }

    private fun handleErrorMsg(exception: HttpException): ErrorMessage {
        lateinit var errorMessage: ErrorMessage
        exception.response()?.let { response ->
            response.errorBody()?.let { errorMsg ->
                JSONObject(errorMsg.string()).let {
                    errorMessage = GsonBuilder().create().fromJson(
                        it.getJSONObject("data").toString(),
                        ErrorMessage::class.java
                    )
                }
            }
        }
        return errorMessage
    }

    fun getAccessToken(authType: String) {
        viewModelScope.launch {
            try {
                val responseAccessToken = RetrofitClient.apiService.getAccessToken(
                    "Bearer $authType", "application/json",
                    RequestAccessToken().setTokenType("client")
                )
                mReference = RandomStringUtils.randomAlphanumeric(10)
                mAccessToken.postValue(responseAccessToken.data.access_token)

            } catch (e: HttpException) {
                val errorMsg = handleErrorMsg(e)
                mAccessToken.postValue("Error: ${errorMsg.message} ( ${errorMsg.debug} )")
            }
        }
    }

    fun getChargeToken(accessToken: String) {
        viewModelScope.launch {
            try {
                val responseChargeToken = RetrofitClient.apiService.getChargeToken(
                    "Bearer $accessToken", "application/json",
                    RequestChargeToken(
                        Transaction(
                            mReference,
                            mAmount.toInt(),
                            mCurrency,
                            mCountry,
                            false,
                            "braintree test"
                        ), Urls(
                            "http://ipn.com",
                            mCallback,
                            "http://fail.com",
                            "http//mobile.com",
                            "http://cancel.com"
                        ), Ext(Device("", "172.0.0.1", ""))
                    )
                )
                mReference = responseChargeToken.data.reference
                mChargeToken.postValue(responseChargeToken.data.charge_token)
            } catch (e: HttpException) {
                mErrorMessage.postValue(handleErrorMsg(e))
            }
        }
    }

    /**
     * access token , charge token and consumer id are the mandatory parameters:
     * access token and charge token have to be downloaded from merchant Backend
     * consumer id is unique identity of this merchant for the consumer who are going to pay
     *
     * @param type is payment method type which was selected by user want to pay with
     */
    internal fun buildDropInRequest(type: CPayMethodType): CPayRequest {
        when (type) {
            CPayMethodType.ALI,CPayMethodType.UNIONPAY, CPayMethodType.WECHAT -> {
                return CPayRequest.UPIOrderBuilder
                    .accessToken(mAccessToken.value!!)
                    .reference(mReference)
                    .consumerID(mConsumerID)
                    .currency(mCurrency)
                    .amount(mAmount)
                    .callbackURL(mCallback)
                    .ipnURL("https://exampe.com/ipn")
                    .mobileURL("https://exampe.com/mobile")
                    .cancelURL("https://exampe.com/cancel")
                    .failURL("https://exampe.com/fail")
                    .setAllowDuplicate(true)
                    .paymentMethod(type)
                    .country(Locale(mCountry))
                    .setExpiry(System.currentTimeMillis()+mTimeout.toLong())
                    .build(CPayENVMode.UAT)
            }
            else -> {
                return CPayRequest.PaymentBuilder
                    .accessToken(mAccessToken.value!!)
                    .chargeToken(mChargeToken.value!!)
                    .reference(mReference)
                    .consumerID(mConsumerID)
                    .request3DSecureVerification(mIs3DS)
                    //.threeDSecureRequest(demoThreeDSecureRequest())
                    .consumer(demo3DSsetup())
                    .paymentMethod(type)
                    .build(CPayENVMode.UAT)
            }
        }
    }

    private fun demo3DSsetup(): CPayConsumer {
        val billingAddr = city("Chicago")
            .state("IL")
            .street("555 Smith St")
            .postCode("12345")
            .country("US")
            .build()
        return firstName("Alex")
            .lastName("Smith")
            .email("google@gmal.com")
            .phone("1112223344")
            .billingAddress(billingAddr)
            .build()
    }

    fun onPaymentTypeChanged(radioGroup: RadioGroup?, id: Int) {
        radioGroup?.let {
            setPaymentMethod(it.id, id)
        }
    }

}