package github.vrih.xsub.provider

import android.content.Context
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionProvider
import com.google.android.gms.cast.framework.media.CastMediaOptions
import com.google.android.gms.cast.framework.media.MediaIntentReceiver
import com.google.android.gms.cast.framework.media.NotificationOptions
import java.util.*

class CastOptionsProvider : OptionsProvider {

    override fun getCastOptions(context: Context): CastOptions {
        val notificationOptions = NotificationOptions.Builder()
                .setActions(Arrays.asList(MediaIntentReceiver.ACTION_SKIP_NEXT,
                        MediaIntentReceiver.ACTION_TOGGLE_PLAYBACK,
                        MediaIntentReceiver.ACTION_STOP_CASTING), intArrayOf(1, 2))
                //.setTargetActivityClassName(ExpandedControlsActivity.class.getName())
                .build()
        val mediaOptions = CastMediaOptions.Builder()
                //       .setImagePicker(new ImagePickerImpl())
                .setNotificationOptions(notificationOptions)
                //     .setExpandedControllerActivityClassName(ExpandedControlsActivity.class.getName())
                .build()
        return CastOptions.Builder()
                .setCastMediaOptions(mediaOptions)
                .setReceiverApplicationId(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID)
                .build()
    }

    override fun getAdditionalSessionProviders(context: Context): List<SessionProvider>? {
        return null
    }
}
