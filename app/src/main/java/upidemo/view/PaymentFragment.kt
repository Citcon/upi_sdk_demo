package upidemo.view

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import citcon.cpay.R
import citcon.cpay.databinding.FragmentPaymentBinding
import com.citconpay.sdk.data.model.CPayMethodType


/**
 * A simple [Fragment] subclass as the default destination in the navigation.
 */
class PaymentFragment : Fragment() {

    private var _binding: FragmentPaymentBinding? = null

    private val mDemoViewModel by lazy { ViewModelProvider(requireActivity()).get(DemoViewModel::class.java) }

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!
    private lateinit var mPaymentGroup: String

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        _binding = FragmentPaymentBinding.inflate(inflater, container, false)
        binding.viewModel = mDemoViewModel

        return binding.root

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.editConsumerId.setText(MainActivity.DEFAULT_CONSUMER_ID)
        mDemoViewModel.mCallback = getString(R.string.default_callback_url)

        mDemoViewModel.mCurrencyIndex.observe(viewLifecycleOwner) {
            mDemoViewModel.mCurrency = resources.getStringArray(R.array.currency_list)[it]
        }

        mDemoViewModel.mCountryIndex.observe(viewLifecycleOwner) {
            mDemoViewModel.mCountry = resources.getStringArray(R.array.country_list)[it]
        }

        mDemoViewModel.mResultString.observe(viewLifecycleOwner) {
            binding.tvResult.text = mDemoViewModel.mResultString.value
        }

        binding.editConsumerId.addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence,
                    start: Int,
                    count: Int,
                    after: Int
                ) {
                }

                override fun onTextChanged(
                    s: CharSequence,
                    start: Int,
                    before: Int,
                    count: Int
                ) {
                }

                override fun afterTextChanged(s: Editable) {
                    mDemoViewModel.mConsumerID = s.toString()
                }
            }
        )

        binding.buttonNewPayment.setOnClickListener {
            mPaymentGroup = "cnpay"
            binding.progressBarLoading.visibility = View.VISIBLE
            binding.radiogroupPaymentBraintree.visibility = View.GONE
            binding.radiogroupPaymentCn.visibility = View.GONE
            mDemoViewModel.getAccessToken(binding.authEditText.text.toString())
            mDemoViewModel.setPaymentMethod(R.id.radiogroup_payment_cn,
                binding.radiogroupPaymentCn.checkedRadioButtonId)
        }

        binding.buttonNewBraintree.setOnClickListener {
            mPaymentGroup = "braintree"
            binding.progressBarLoading.visibility = View.VISIBLE
            binding.radiogroupPaymentBraintree.visibility = View.GONE
            binding.radiogroupPaymentCn.visibility = View.GONE
            mDemoViewModel.getAccessToken(binding.authEditText.text.toString())
            mDemoViewModel.setPaymentMethod(R.id.radiogroup_payment_braintree,
                binding.radiogroupPaymentBraintree.checkedRadioButtonId)
        }
//        binding.buttonFirst.setOnClickListener {
//            findNavController().navigate(R.id.action_FirstFragment_to_SecondFragment)
//        }

        binding.buttonInquire.setOnClickListener {

        }

        binding.checkBox3DS.setOnCheckedChangeListener {_, isChecked ->
            mDemoViewModel.mIs3DS = isChecked
        }

        binding.radiogroupPaymentBraintree.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.radioButton_paypal -> mDemoViewModel.setPaymentMethod(CPayMethodType.PAYPAL)
                R.id.radioButton_venmo -> mDemoViewModel.setPaymentMethod(CPayMethodType.PAY_WITH_VENMO)
                R.id.radioButton_credit -> mDemoViewModel.setPaymentMethod(CPayMethodType.UNKNOWN)
            }

        }

        binding.radiogroupPaymentCn.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.radioButton_alipay -> mDemoViewModel.setPaymentMethod(CPayMethodType.ALI)
                R.id.radioButton_wechat -> mDemoViewModel.setPaymentMethod(CPayMethodType.WECHAT)
                R.id.radioButton_upop -> mDemoViewModel.setPaymentMethod(CPayMethodType.UNIONPAY)
            }

        }

        mDemoViewModel.mAccessToken.observe(viewLifecycleOwner) {
            binding.progressBarLoading.visibility = View.GONE
            if (it.subSequence(0, 4) != "Erro") {
                //mDemoViewModel.getChargeToken(it)
                when (mPaymentGroup) {
                    "braintree" -> binding.radiogroupPaymentBraintree.visibility = View.VISIBLE
                    "cnpay" -> binding.radiogroupPaymentCn.visibility = View.VISIBLE
                }

                binding.tvReference.text = mDemoViewModel.getReference()
            }
            binding.tvAccessToken.text = it
        }

        /*mDemoViewModel.mChargeToken.observe(viewLifecycleOwner) {
            if (it.subSequence(0, 4) != "Error") {
                //enable payment elements
                binding.radiogroupPaymentMethod.visibility = View.VISIBLE
            }
            binding.progressBarLoading.visibility = View.GONE
            //binding.tvChargeToken.text = it
        }*/
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

}