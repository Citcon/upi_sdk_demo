package upidemo.view

import android.app.Application
import android.widget.RadioGroup
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import citcon.cpay.R
import com.citconpay.sdk.data.model.*
import com.citconpay.sdk.data.model.CPayRequest.BillingAdressBuilder
import com.citconpay.sdk.data.model.CPayRequest.ConsumerBuilder
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
    var mReference = ""
    internal var mConsumerID: String = MainActivity.DEFAULT_CONSUMER_ID
    private lateinit var mChosePaymentMethod: CPayMethodType
    internal var mIs3DS: Boolean = false
    var mAmount = "8"
    var mTimeout = "60000"
    lateinit var mCallback : String
    val mResultString by lazy { MutableLiveData<String>() }
    val mCurrencyIndex = MutableLiveData(0)
    val mCountryIndex = MutableLiveData(0)
    val mErrorMessage by lazy { MutableLiveData<ErrorMessage>() }
    var mCurrency = application.resources.getStringArray(R.array.currency_list)[0]!!
    var mCountry = application.resources.getStringArray(R.array.country_list)[0]!!
    var isUPI = false

    object RetrofitClient {

//        private const val BASE_URL = "https://api.qa01.citconpay.com/v1/";
//        private const val BASE_URL = "https://api-eks.qa01.citconpay.com/v1/";
        private const val BASE_URL = "https://api.sandbox.citconpay.com/v1/";

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
            mChosePaymentMethod = if(it == R.id.radiogroup_payment_upi) {
                isUPI = true
                when (id) {
                    R.id.radioButton_upop -> CPayMethodType.UNIONPAY
                    R.id.radioButton_wechat -> CPayMethodType.WECHAT
                    R.id.radioButton_alipay -> CPayMethodType.ALI

                    R.id.radioButton_cashapp -> CPayMethodType.CASHAPP
                    R.id.radioButton_upipaypal -> CPayMethodType.PAYPAL
                    R.id.radioButton_upivenmo -> CPayMethodType.PAY_WITH_VENMO

                    else -> CPayMethodType.WECHAT
                }
            } else {
                isUPI = false
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
        if (isUPI) {
            if (type == CPayMethodType.CASHAPP) {
                return CPayRequest.UPIOrderBuilder()
                    .accessToken(mAccessToken.value!!)
                    .chargeToken(mChargeToken.value!!)
                    .reference(mReference)
                    .country(Locale.US)
                    .currency("USD")
                    .amount(mAmount)
                    .enableAutoCapture(true)
                    .paymentMethod(type)
                    .ipnURL("https://www.merchant.com/ipn")
                    .callbackURL(mCallback)
                    .mobileURL("https://exampe.com/mobile")
                    .cancelURL("citcon://cpay.sdk")
                    .failURL("citcon://cpay.sdk")
                    .build(CPayENVMode.UAT)


                /*
                //// detail create

                // create blling
                val billingAddr = CPayBillingAddr()
                billingAddr.country = "US"
                billingAddr.city = "Columbus"
                billingAddr.state = "OH"
                billingAddr.street = "2425 Example Rd"
                billingAddr.street2 = ""
                billingAddr.zip = "43221"

                // create consumer
                val consumer = ConsumerBuilder.INSTANCE
                    .reference(mReference)
                    .firstName("first")
                    .lastName("last")
                    .email("test@citcon.cn")
                    .phone("+8615167186161")
                    .billingAddress(billingAddr)
                    .build()

                val order = CPayRequest.UPIOrderBuilder()
                    .accessToken(mAccessToken.value!!)
                    .chargeToken(mChargeToken.value!!)
                    .reference(mReference)
                    .country(Locale.US)
                    .currency("USD")
                    .amount("8")
                    .enableAutoCapture(true)
                    .paymentMethod(CPayMethodType.CASHAPP)
                    .ipnURL("https://www.merchant.com/ipn")
                    .callbackURL("citcon://cpay.sdk")
                    .cancelURL("citcon://cpay.sdk")
                    .failURL("citcon://cpay.sdk")
                    .consumer(consumer)
                    .build(CPayENVMode.UAT)


                 */
            }

            if (type == CPayMethodType.PAYPAL) {
                // create goods
                var goodsData = CPayGoodsData();
                goodsData.name = "shoes"
                goodsData.sku = "shoes"
                goodsData.url = "https://www.ttshop.com"
                goodsData.quantity = 4
                goodsData.unitAmount = 1
                goodsData.unitTaxAmount = 1
                goodsData.totalDiscountAmount = 1
                goodsData.productType = "physical"

                // create shipping
                var shipping = CPayShipping();

                shipping.firstName = "first"
                shipping.lastName = "last"
                shipping.phone = "1-888-254-4887"
                shipping.email = "test@citcon.cn"
                shipping.street = "3 Main St"
                shipping.street2 = ""
                shipping.city = "CA"
                shipping.state = "San Jose"
                shipping.zip = "95134"
                shipping.country = "US"
                shipping.type = "shipping"
                shipping.amount = 1

                // create blling
                var billingAddr = CPayBillingAddr();
                billingAddr.country = "US"
                billingAddr.city = "Columbus"
                billingAddr.state = "OH"
                billingAddr.street = "2425 Example Rd"
                billingAddr.street2 = ""
                billingAddr.zip = "43221"

                // create consumer
                var consumer = ConsumerBuilder()
                    .reference(mReference)
                    .firstName("first")
                    .lastName("last")
                    .email("test@citcon.cn")
                    .phone("+8615167186161")
                    .firstInteractionTime(1663311480)
                    .firstInteractionTime(1663312480)
                    .registrationIp("23.12.32.21")
                    .riskLevel("medium")
                    .totalTransactionCount(1)
                    .billingAddress(billingAddr)
                    .build();

                // build order object
                var order = CPayRequest.UPIOrderBuilder()
                    .accessToken(mAccessToken.value!!)
                    .chargeToken(mChargeToken.value!!)
                    .reference(mReference)
                    .currency("USD")
                    .amount("8")
                    .vertical("Household goods, shoes, clothing, tickets")
                    .ipnURL("https://www.merchant.com/ipn")
                    .callbackURL("citcon://cpay.sdk")
                    .cancelURL("citcon://cpay.sdk")
                    .failURL("citcon://cpay.sdk")
                    .mobileURL("citcon://cpay.sdk")
                    .note("test order")
                    .paymentMethod(CPayMethodType.PAYPAL)
                    .country(Locale.US)
                    .goods(arrayOf(goodsData))
                    .consumer(consumer)
                    .billingAddr(billingAddr)
                    .enableAutoCapture(true)
                    .shipping(shipping)
                    .build(CPayENVMode.UAT);
                    return order
            }

            return CPayRequest.UPIOrderBuilder()
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
        } else {
            return CPayRequest.PaymentBuilder()
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

    private fun demo3DSsetup(): CPayConsumer {
        val billingAddr = BillingAdressBuilder().city("Chicago")
            .state("IL")
            .street("555 Smith St")
            .postCode("12345")
            .country("US")
            .build()
        return ConsumerBuilder().firstName("Alex")
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