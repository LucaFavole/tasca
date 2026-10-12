package app.tasca.ios

import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSDateComponents
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNAuthorizationStatusEphemeral
import platform.UserNotifications.UNAuthorizationStatusProvisional
import platform.UserNotifications.UNCalendarNotificationTrigger
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotification
import platform.UserNotifications.UNNotificationAction
import platform.UserNotifications.UNNotificationActionOptionForeground
import platform.UserNotifications.UNNotificationCategory
import platform.UserNotifications.UNNotificationCategoryOptionNone
import platform.UserNotifications.UNNotificationDefaultActionIdentifier
import platform.UserNotifications.UNNotificationPresentationOptionBanner
import platform.UserNotifications.UNNotificationPresentationOptionList
import platform.UserNotifications.UNNotificationPresentationOptionSound
import platform.UserNotifications.UNNotificationPresentationOptions
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationResponse
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNUserNotificationCenter
import platform.UserNotifications.UNUserNotificationCenterDelegateProtocol
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlin.coroutines.resume

/** The 23:00 reminder, with the texts and the "Add expense" action of Android's Reminder in Daily.kt. */
internal object Reminder {
    private const val ID = "reminder"
    const val ADD = "add"
    private val center get() = UNUserNotificationCenter.currentNotificationCenter()
    /** The center keeps only a weak reference to its delegate. */
    private var delegate: TapDelegate? = null

    // To verify on a Mac: a tap on the reminder opens Home, its "Add expense" action the keypad, also from a cold launch.
    fun install(onLink: (String) -> Unit) {
        val tap = TapDelegate(onLink)
        delegate = tap
        center.delegate = tap
        val add = UNNotificationAction.actionWithIdentifier(ADD, title = "Add expense", options = UNNotificationActionOptionForeground)
        center.setNotificationCategories(
            setOf(UNNotificationCategory.categoryWithIdentifier(ID, listOf(add), emptyList<String>(), UNNotificationCategoryOptionNone)),
        )
    }

    // To verify on a Mac: with the reminder on and notifications allowed, it arrives at 23:00 every day; off removes it.
    /** Adds or removes the repeating request. Asking again replaces it, so this can run on every launch. */
    fun schedule(on: Boolean) {
        if (!on) return center.removePendingNotificationRequestsWithIdentifiers(listOf(ID))
        val content = UNMutableNotificationContent().apply {
            setTitle("Have you recorded all of today's expenses?")
            setBody("Tap to check your day")
            setCategoryIdentifier(ID)
            setSound(UNNotificationSound.defaultSound)
        }
        val at = NSDateComponents().apply {
            hour = 23
            minute = 0
        }
        val trigger = UNCalendarNotificationTrigger.triggerWithDateMatchingComponents(at, repeats = true)
        center.addNotificationRequest(UNNotificationRequest.requestWithIdentifier(ID, content, trigger), withCompletionHandler = null)
    }

    // To verify on a Mac: the first time the reminder needs it, iOS asks; refusing turns the reminder off.
    suspend fun allowed(): Boolean = suspendCancellableCoroutine { done ->
        center.getNotificationSettingsWithCompletionHandler { settings ->
            val status = settings?.authorizationStatus
            done.resume(status == UNAuthorizationStatusAuthorized || status == UNAuthorizationStatusProvisional || status == UNAuthorizationStatusEphemeral)
        }
    }

    fun ask(onAnswer: (Boolean) -> Unit) {
        center.requestAuthorizationWithOptions(UNAuthorizationOptionAlert or UNAuthorizationOptionSound or UNAuthorizationOptionBadge) { granted, _ ->
            dispatch_async(dispatch_get_main_queue()) {
                if (granted) schedule(true)
                onAnswer(granted)
            }
        }
    }
}

private class TapDelegate(private val onLink: (String) -> Unit) : NSObject(), UNUserNotificationCenterDelegateProtocol {
    override fun userNotificationCenter(
        center: UNUserNotificationCenter,
        didReceiveNotificationResponse: UNNotificationResponse,
        withCompletionHandler: () -> Unit,
    ) {
        when (didReceiveNotificationResponse.actionIdentifier) {
            Reminder.ADD -> onLink("tasca://add")
            UNNotificationDefaultActionIdentifier -> onLink("tasca://open")
        }
        withCompletionHandler()
    }

    /** Android shows the reminder whether or not the app is open; iOS hides it in the foreground unless asked. */
    override fun userNotificationCenter(
        center: UNUserNotificationCenter,
        willPresentNotification: UNNotification,
        withCompletionHandler: (UNNotificationPresentationOptions) -> Unit,
    ) {
        withCompletionHandler(UNNotificationPresentationOptionBanner or UNNotificationPresentationOptionList or UNNotificationPresentationOptionSound)
    }
}
