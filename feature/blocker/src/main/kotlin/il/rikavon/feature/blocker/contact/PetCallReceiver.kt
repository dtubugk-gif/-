package il.rikavon.feature.blocker.contact

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** "Decline" on the call notification: the pet hangs up. */
@AndroidEntryPoint
class PetCallReceiver : BroadcastReceiver() {
    @Inject lateinit var notifier: PetContactNotifier

    override fun onReceive(context: Context, intent: Intent) {
        notifier.cancelCall()
    }
}
