package com.tapledger.parser

import com.google.common.truth.Truth.assertThat
import com.tapledger.core.model.TransactionKind
import org.junit.Test

class FinancialNotificationParserTest {

    @Test
    fun parse_hdfcBankDebit_extractsAllFields() {
        val event = NotificationEvent(
            packageName = "com.google.android.apps.messaging",
            title = "HDFC Bank",
            text = "Sent Rs. 850.00 from HDFC Bank A/C **1234 to SWIGGY on 08-10-26 via UPI Ref 428192837482. Avl Bal: Rs 49,150.00",
            postTime = 1728374000000L
        )

        val result = FinancialNotificationParser.parse(event)

        assertThat(result).isNotNull()
        result?.let {
            assertThat(it.amountPaise).isEqualTo(85000L)
            assertThat(it.accountLast4).isEqualTo("1234")
            assertThat(it.merchantRaw).isEqualTo("SWIGGY")
            assertThat(it.upiReference).isEqualTo("428192837482")
            assertThat(it.kind).isEqualTo(TransactionKind.EXPENSE)
            assertThat(it.reportedAvailableBalancePaise).isEqualTo(4915000L)
        }
    }

    @Test
    fun parse_sbiDebit_extractsAmountAndLast4() {
        val event = NotificationEvent(
            packageName = "com.google.android.apps.messaging",
            title = "SBI",
            text = "Dear UPI user A/C 5678 debited by 1,200.00 on 08Oct26 tran id 429182374928",
            postTime = 1728374000000L
        )

        val result = FinancialNotificationParser.parse(event)

        assertThat(result).isNotNull()
        result?.let {
            assertThat(it.amountPaise).isEqualTo(120000L)
            assertThat(it.accountLast4).isEqualTo("5678")
            assertThat(it.upiReference).isEqualTo("429182374928")
            assertThat(it.kind).isEqualTo(TransactionKind.EXPENSE)
        }
    }

    @Test
    fun parse_hdfcCreditCardPurchase_extractsCardDetails() {
        val event = NotificationEvent(
            packageName = "com.google.android.apps.messaging",
            title = "HDFC Bank Alert",
            text = "Alert: Rs. 2,000.00 spent on your HDFC Bank Card ending 9012 at AMAZON on 08-OCT-26",
            postTime = 1728374000000L
        )

        val result = FinancialNotificationParser.parse(event)

        assertThat(result).isNotNull()
        result?.let {
            assertThat(it.amountPaise).isEqualTo(200000L)
            assertThat(it.accountLast4).isEqualTo("9012")
            assertThat(it.merchantRaw).isEqualTo("AMAZON")
            assertThat(it.kind).isEqualTo(TransactionKind.EXPENSE)
        }
    }

    @Test
    fun parse_gpayPayment_extractsAmountAndMerchant() {
        val event = NotificationEvent(
            packageName = "com.google.android.apps.nbu.paisa.user",
            title = "Google Pay",
            text = "Paid ₹850 to Swiggy",
            postTime = 1728374000000L
        )

        val result = FinancialNotificationParser.parse(event)

        assertThat(result).isNotNull()
        result?.let {
            assertThat(it.amountPaise).isEqualTo(85000L)
            assertThat(it.merchantRaw).isEqualTo("Swiggy")
            assertThat(it.kind).isEqualTo(TransactionKind.EXPENSE)
        }
    }

    @Test
    fun parse_upiReversal_marksAsRefund() {
        val event = NotificationEvent(
            packageName = "com.google.android.apps.messaging",
            title = "HDFC Bank",
            text = "Rs. 500.00 credited back to your A/C 1234. UPI transaction failed/reversed on 08-10-26",
            postTime = 1728374000000L
        )

        val result = FinancialNotificationParser.parse(event)

        assertThat(result).isNotNull()
        result?.let {
            assertThat(it.amountPaise).isEqualTo(50000L)
            assertThat(it.kind).isEqualTo(TransactionKind.REFUND)
            assertThat(it.isReversalOrRefund).isTrue()
        }
    }

    @Test
    fun parse_filtersOtpAndPromotionalAlerts() {
        val otpEvent = NotificationEvent(
            packageName = "com.google.android.apps.messaging",
            title = "HDFC Bank",
            text = "123456 is your secret OTP for transaction of Rs. 500.00. Do not share.",
            postTime = 1728374000000L
        )
        assertThat(FinancialNotificationParser.parse(otpEvent)).isNull()

        val promoEvent = NotificationEvent(
            packageName = "com.google.android.apps.messaging",
            title = "Offers",
            text = "Special discount! Get cashback up to Rs. 200 on your next grocery order.",
            postTime = 1728374000000L
        )
        assertThat(FinancialNotificationParser.parse(promoEvent)).isNull()

        val billDueEvent = NotificationEvent(
            packageName = "com.google.android.apps.messaging",
            title = "Credit Card",
            text = "Your HDFC Credit Card bill due is Rs. 15,400. Minimum due is Rs. 1,000.",
            postTime = 1728374000000L
        )
        assertThat(FinancialNotificationParser.parse(billDueEvent)).isNull()
    }
}
