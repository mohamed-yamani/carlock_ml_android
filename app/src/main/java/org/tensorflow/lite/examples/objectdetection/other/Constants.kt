package org.tensorflow.lite.examples.objectdetection.other

import java.util.*

object Constants {
    const val MATRICULES_LIST_URL = "https://carlock.icebergtech.net/api/v1/core/matricule/list"
    const val MATCH_CREATE_URL = "https://carlock.icebergtech.net/api/v1/core/match/create"
    const val PLATEFINDER_DOWNLOAD_URL = "https://carlock.icebergtech.net/api/v1/core/mlmodel/platefinder/download/"
    const val PLATEREADER_DOWNLOAD_URL =  "https://carlock.icebergtech.net/api/v1/core/mlmodel/platereader/download/"


    const val MATRICULE_STR = "matricule_str"
    const val LOCATION_STR = "location"
    const val COORDS_STR = "coords"
    const val PICTURE_STR = "picture"

    const val ACTION_START_OR_RESUME_SERVICE = "ACTION_START_OR_RESUME_SERVICE"
    const val ACTION_PAUSE_SERVICE = "ACTION_PAUSE_SERVICE"
    const val ACTION_STOP_SERVICE = "ACTION_STOP_SERVICE"
    const val ACTION_SHOW_C_FRAGMENT  = "ACTION_SHOW_C_FRAGMENT"

    const val TIMER_UPDATE_INTERVAL = 50L

    const val NOTIFICATION_CHANNEL_ID = "camera_channel"
    const val NOTIFICATION_CHANNEL_NAME = "camera"
    const val NOTIFICATION_ID = 1

    const val REQUEST_CODE_PERMISSIONS = 101

    var IS_FIRST_TIME = true
}