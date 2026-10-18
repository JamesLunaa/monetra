package com.example.monetra.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.AssignmentReturn
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Checkroom
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Handshake
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Paid
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.QuestionMark
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Work
import androidx.compose.ui.graphics.vector.ImageVector

/** Icon keys stored on categories and accounts, mapped to drawn icons. Order is the picker order. */
object AppIcons {
    val all: Map<String, ImageVector> = linkedMapOf(
        "food" to Icons.Rounded.Restaurant,
        "transport" to Icons.Rounded.DirectionsBus,
        "bills" to Icons.AutoMirrored.Rounded.ReceiptLong,
        "home" to Icons.Rounded.Home,
        "phone" to Icons.Rounded.PhoneAndroid,
        "social" to Icons.Rounded.Groups,
        "sport" to Icons.Rounded.FitnessCenter,
        "games" to Icons.Rounded.SportsEsports,
        "shopping" to Icons.Rounded.ShoppingBag,
        "clothing" to Icons.Rounded.Checkroom,
        "health" to Icons.Rounded.HealthAndSafety,
        "car" to Icons.Rounded.DirectionsCar,
        "fuel" to Icons.Rounded.LocalGasStation,
        "work" to Icons.Rounded.Work,
        "school" to Icons.Rounded.School,
        "cafe" to Icons.Rounded.LocalCafe,
        "movie" to Icons.Rounded.Movie,
        "gift" to Icons.Rounded.CardGiftcard,
        "pets" to Icons.Rounded.Pets,
        "travel" to Icons.Rounded.Flight,
        "repair" to Icons.Rounded.Build,
        "returnable" to Icons.AutoMirrored.Rounded.AssignmentReturn,
        "unrecorded" to Icons.Rounded.QuestionMark,
        "salary" to Icons.Rounded.Payments,
        "income" to Icons.Rounded.Paid,
        "refund" to Icons.Rounded.Replay,
        "debt" to Icons.Rounded.Handshake,
        "savings" to Icons.Rounded.Savings,
        "cash" to Icons.Rounded.Payments,
        "wallet" to Icons.Rounded.AccountBalanceWallet,
        "bank" to Icons.Rounded.AccountBalance,
        "card" to Icons.Rounded.CreditCard,
        "other" to Icons.Rounded.Category,
    )

    val accountKeys = listOf("cash", "wallet", "bank", "card", "savings", "debt")

    operator fun get(key: String?): ImageVector = all[key] ?: Icons.Rounded.Category
}
