package com.example.monetra.ui.components

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import com.example.monetra.core.money.Money
import com.example.monetra.core.money.MoneyFormat
import com.example.monetra.core.money.SignStyle
import com.example.monetra.data.db.TxType
import com.example.monetra.ui.theme.MonetraTheme

/** Signed amount colored by direction: expense −, income/refund +, loans signed in ink, transfer unsigned. */
@Composable
fun RecordAmount(type: TxType, amount: Long, format: MoneyFormat, style: TextStyle, modifier: Modifier = Modifier) {
    val money = Money(amount)
    val (text, color) = when (type) {
        TxType.EXPENSE -> format.format(-money) to MonetraTheme.colors.expense
        TxType.INCOME -> format.format(money, SignStyle.ALWAYS) to MonetraTheme.colors.income
        TxType.TRANSFER -> format.format(money, SignStyle.NONE) to LocalContentColor.current
        // A refund lowers spending, so it reads as money back in the income colour.
        TxType.REFUND -> format.format(money, SignStyle.ALWAYS) to MonetraTheme.colors.income
        // Loans move cash without being income or spending: signed, in ink.
        TxType.DEBT_IN -> format.format(money, SignStyle.ALWAYS) to LocalContentColor.current
        TxType.DEBT_OUT -> format.format(-money) to LocalContentColor.current
    }
    Text(text, style = style, color = color, modifier = modifier, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip)
}

/** A balance or net: negative in the expense color, otherwise [positive]. */
@Composable
fun SignedAmount(
    money: Money,
    format: MoneyFormat,
    style: TextStyle,
    modifier: Modifier = Modifier,
    sign: SignStyle = SignStyle.NEGATIVE,
    positive: Color = LocalContentColor.current,
    negative: Color = MonetraTheme.colors.expense,
) {
    Text(
        format.format(money, sign), style = style, modifier = modifier, maxLines = 1, softWrap = false,
        color = if (money.isNegative) negative else positive,
    )
}
