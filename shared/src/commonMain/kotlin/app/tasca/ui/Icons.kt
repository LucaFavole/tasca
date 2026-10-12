package app.tasca.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Celebration
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.outlined.ChildCare
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.DeliveryDining
import androidx.compose.material.icons.outlined.DirectionsBus
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.FamilyRestroom
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocalCafe
import androidx.compose.material.icons.outlined.LocalBar
import androidx.compose.material.icons.outlined.LocalGasStation
import androidx.compose.material.icons.outlined.LocalHospital
import androidx.compose.material.icons.outlined.MoneyOff
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material.icons.outlined.Work
import androidx.compose.ui.graphics.vector.ImageVector

/** Icon keys stored in the database, mapped to their drawings. Unknown keys fall back to a generic icon. */
object AppIcons {
    val all: Map<String, ImageVector> = linkedMapOf(
        "home" to Icons.Outlined.Home,
        "cart" to Icons.Outlined.ShoppingCart,
        "restaurant" to Icons.Outlined.Restaurant,
        "coffee" to Icons.Outlined.LocalCafe,
        "delivery" to Icons.Outlined.DeliveryDining,
        "bar" to Icons.Outlined.LocalBar,
        "celebration" to Icons.Outlined.Celebration,
        "movie" to Icons.Outlined.Movie,
        "bus" to Icons.Outlined.DirectionsBus,
        "car" to Icons.Outlined.DirectionsCar,
        "fuel" to Icons.Outlined.LocalGasStation,
        "flight" to Icons.Outlined.Flight,
        "health" to Icons.Outlined.LocalHospital,
        "fitness" to Icons.Outlined.FitnessCenter,
        "school" to Icons.Outlined.School,
        "gift" to Icons.Outlined.CardGiftcard,
        "family" to Icons.Outlined.FamilyRestroom,
        "child" to Icons.Outlined.ChildCare,
        "clothes" to Icons.Outlined.Checkroom,
        "shopping" to Icons.Outlined.ShoppingBag,
        "pets" to Icons.Outlined.Pets,
        "phone" to Icons.Outlined.Smartphone,
        "bolt" to Icons.Outlined.Bolt,
        "subscriptions" to Icons.Outlined.Subscriptions,
        "receipt" to Icons.Outlined.Receipt,
        "money_off" to Icons.Outlined.MoneyOff,
        "work" to Icons.Outlined.Work,
        "bank" to Icons.Outlined.AccountBalance,
        "savings" to Icons.Outlined.Savings,
        "invest" to Icons.Outlined.TrendingUp,
        "wallet" to Icons.Outlined.AccountBalanceWallet,
        "card" to Icons.Outlined.CreditCard,
        "cash" to Icons.Outlined.Payments,
        "category" to Icons.Outlined.Category,
        "other" to Icons.Outlined.HelpOutline,
    )

    operator fun get(key: String?): ImageVector = all[key] ?: Icons.Outlined.Category
}
