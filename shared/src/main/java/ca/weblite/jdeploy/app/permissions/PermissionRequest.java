package ca.weblite.jdeploy.app.permissions;

public enum PermissionRequest {
    CAMERA("NSCameraUsageDescription"),
    MICROPHONE("NSMicrophoneUsageDescription"),
    LOCATION("NSLocationUsageDescription"),
    LOCATION_WHEN_IN_USE("NSLocationWhenInUseUsageDescription"),
    LOCATION_ALWAYS("NSLocationAlwaysUsageDescription"),
    CONTACTS("NSContactsUsageDescription"),
    CALENDARS("NSCalendarsUsageDescription"),
    REMINDERS("NSRemindersUsageDescription"),
    PHOTOS("NSPhotoLibraryUsageDescription"),
    PHOTOS_ADD("NSPhotoLibraryAddUsageDescription"),
    MOTION("NSMotionUsageDescription"),
    HEALTH_SHARE("NSHealthShareUsageDescription"),
    HEALTH_UPDATE("NSHealthUpdateUsageDescription"),
    BLUETOOTH("NSBluetoothPeripheralUsageDescription"),
    BLUETOOTH_ALWAYS("NSBluetoothAlwaysUsageDescription"),
    SPEECH_RECOGNITION("NSSpeechRecognitionUsageDescription"),
    HOMEKIT("NSHomeKitUsageDescription"),
    MUSIC("NSAppleMusicUsageDescription"),
    TV_PROVIDER("NSTVProviderUsageDescription"),
    VIDEO_SUBSCRIBER("NSVideoSubscriberAccountUsageDescription"),
    SIRI("NSSiriUsageDescription"),
    FACE_ID("NSFaceIDUsageDescription"),
    DESKTOP_FOLDER("NSDesktopFolderUsageDescription"),
    DOCUMENTS_FOLDER("NSDocumentsFolderUsageDescription"),
    DOWNLOADS_FOLDER("NSDownloadsFolderUsageDescription"),
    NETWORK_VOLUMES("NSNetworkVolumesUsageDescription"),
    REMOVABLE_VOLUMES("NSRemovableVolumesUsageDescription"),
    FILE_PROVIDER_PRESENCE("NSFileProviderPresenceUsageDescription"),
    SYSTEM_ADMINISTRATION("NSSystemAdministrationUsageDescription"),
    APPLE_EVENTS("NSAppleEventsUsageDescription"),
    LOCAL_NETWORK("NSLocalNetworkUsageDescription"),
    MEDIA_LIBRARY("NSMediaLibraryUsageDescription"),
    CALENDARS_WRITE("NSCalendarsWriteUsageDescription"),
    REMINDERS_WRITE("NSRemindersWriteUsageDescription"),
    FOCUS_STATUS("NSFocusStatusUsageDescription"),
    USER_TRACKING("NSUserTrackingUsageDescription"),
    USER_NOTIFICATIONS("NSUserNotificationsUsageDescription"),
    NFC_READER("NSNearFieldCommunicationReaderUsageDescription"),
    SCREEN_CAPTURE("NSScreenCaptureUsageDescription"),
    CONTACTS_FULL_ACCESS("NSContactsFullAccessUsageDescription"),
    GAMEKIT_FRIEND_REQUEST("NSGKFriendRequestUsageDescription"),
    IDENTITY("NSIdentityUsageDescription"),
    WILLINGNESS_TO_RATE("NSWillingnessToRateUsageDescription"),
    LOOK_AROUND("NSLookAroundUsageDescription");

    private final String macOSKey;

    PermissionRequest(String macOSKey) {
        this.macOSKey = macOSKey;
    }

    public String getMacOSKey() {
        return macOSKey;
    }
}