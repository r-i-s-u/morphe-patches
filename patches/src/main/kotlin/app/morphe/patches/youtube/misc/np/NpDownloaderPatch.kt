package app.morphe.patches.youtube.misc.np

import app.morphe.patcher.patch.resourcePatch
import app.morphe.patches.youtube.misc.extension.sharedExtensionPatch
import app.morphe.patches.youtube.shared.Constants.COMPATIBILITY_YOUTUBE
import org.w3c.dom.Element

@Suppress("unused")
val npDownloaderPatch = resourcePatch(
    name = "NewPipe downloader",
    description = "Adds the NewPipe downloader activity to YouTube."
) {
    dependsOn(sharedExtensionPatch)

    compatibleWith(COMPATIBILITY_YOUTUBE)

    execute {
        document("AndroidManifest.xml").use { document ->
            val applicationNode = document
                .getElementsByTagName("application")
                .item(0) as Element

            val activity = document.createElement("activity")
            activity.setAttribute("android:name", "app.morphe.extension.youtube.np.NpMainActivity")
            activity.setAttribute("android:exported", "true")
            applicationNode.appendChild(activity)
        }
    }
}
