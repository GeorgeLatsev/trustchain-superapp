package nl.tudelft.trustchain.musicdao.ui.navigation

sealed class Screen(val route: String) {
    object Home : Screen("home")

    object Release : Screen("release/{releaseId}") {
        fun createRoute(releaseId: String) = "release/$releaseId"
    }

    object Search : Screen("search")

    object Settings : Screen("settings")

    object Debug : Screen("debug")

    object FullPlayerScreen : Screen("fullPlayerScreen")

    object CreatorMenu : Screen("me")

    object MyProfile : Screen("me/profile")

    object EditProfile : Screen("me/edit")

    object BitcoinWallet : Screen("me/wallet")

    object DiscoverArtists : Screen("artists")

    object Profile : Screen("profile/{publicKey}") {
        fun createRoute(publicKey: String) = "profile/$publicKey"
    }

    object Donate : Screen("profile/{publicKey}/donate") {
        fun createRoute(publicKey: String) = "profile/$publicKey/donate"
    }

    object CreateRelease : Screen("release/create")
}
