package it.cammino.risuscito.ui.activity

import android.annotation.SuppressLint
import android.content.*
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Bundle
import android.os.RemoteException
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.preference.PreferenceManager
import com.google.android.gms.tasks.Tasks
import com.google.android.material.elevation.SurfaceColors
import com.google.android.play.core.splitcompat.SplitCompat
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.StorageReference
import com.google.firebase.storage.ktx.storage
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import it.cammino.risuscito.R
import it.cammino.risuscito.database.RisuscitoDatabase
import it.cammino.risuscito.database.dao.Backup
import it.cammino.risuscito.database.entities.*
import it.cammino.risuscito.database.serializer.DateTimeDeserializer
import it.cammino.risuscito.database.serializer.DateTimeSerializer
import it.cammino.risuscito.playback.MusicService
import it.cammino.risuscito.services.RisuscitoMessagingService
import it.cammino.risuscito.ui.RisuscitoApplication
import it.cammino.risuscito.ui.dialog.SimpleDialogFragment
import it.cammino.risuscito.utils.StringUtils
import it.cammino.risuscito.utils.Utility
import it.cammino.risuscito.utils.extension.*
import it.cammino.risuscito.viewmodels.MainActivityViewModel
import java.io.*
import java.sql.Date
import java.util.concurrent.ExecutionException

abstract class ThemeableActivity : AppCompatActivity() {

    protected val mViewModel: MainActivityViewModel by viewModels()

    @SuppressLint("NewApi")
    public override fun onCreate(savedInstanceState: Bundle?) {

        Log.d(TAG, "Configuration.UI_MODE_NIGHT_NO: ${Configuration.UI_MODE_NIGHT_NO}")
        Log.d(TAG, "Configuration.UI_MODE_NIGHT_YES: ${Configuration.UI_MODE_NIGHT_YES}")
        Log.d(
            TAG,
            "getResources().getConfiguration().uiMode: ${resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK}"
        )

        Log.d(TAG, "isDarkMode: $isDarkMode")
        convertIntPreferences()

        Log.d(TAG, "onCreate: isOnTablet = $isOnTablet")
        Log.d(TAG, "onCreate: isGridLayout = $isGridLayout")
        Log.d(TAG, "onCreate: isLandscape = $isLandscape")
        mViewModel.isTabletWithFixedDrawer = isOnTablet && isLandscape
        Log.d(TAG, "onCreate: hasFixedDrawer = ${mViewModel.isTabletWithFixedDrawer}")
        mViewModel.isTabletWithNoFixedDrawer = isOnTablet && !isLandscape
        Log.d(TAG, "onCreate: hasFixedDrawer = ${mViewModel.isTabletWithNoFixedDrawer}")

        setupNavBarColor()
        updateStatusBarLightMode(true)

        setTaskDescription(this.createTaskDescription(TAG))

        // Connect a media browser just to get the media session token. There are other ways
        // this can be done, for example by sharing the session token directly.
        mMediaBrowser = MediaBrowserCompat(
            this, ComponentName(
                this, MusicService::
                class.java
            ), mConnectionCallback, null
        )

        super.onCreate(savedInstanceState)
    }

    override fun onStart() {
        super.onStart()
        Log.d(TAG, "onStart: ")
        try {
            mMediaBrowser?.connect()
        } catch (e: IllegalStateException) {
            Log.e(TAG, "onStart: mMediaBrowser connecting")
        }
    }

    override fun onStop() {
        super.onStop()
        Log.d(TAG, "onStop: ")
        mMediaBrowser?.disconnect()
        val controller = MediaControllerCompat.getMediaController(this)
        controller?.unregisterCallback(mMediaControllerCallback)
    }

    override fun onResume() {
        super.onResume()

        updateStatusBarLightMode(true)
        LocalBroadcastManager.getInstance(applicationContext).registerReceiver(
            showInfoBroadcastReceiver,
            IntentFilter(RisuscitoMessagingService.MESSAGE_RECEIVED_TAG)
        )
        LocalBroadcastManager.getInstance(applicationContext).registerReceiver(
            catalogReadyBR,
            IntentFilter(MusicService.BROADCAST_RETRIEVE_ASYNC)
        )
        checkScreenAwake()
    }

    override fun onPause() {
        super.onPause()
        LocalBroadcastManager.getInstance(applicationContext)
            .unregisterReceiver(showInfoBroadcastReceiver)
        LocalBroadcastManager.getInstance(applicationContext).unregisterReceiver(catalogReadyBR)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy(): $isFinishing")
        if (isFinishing) stopMedia()
    }

    private fun updateStatusBarLightMode(auto: Boolean) {
        setLigthStatusBar(if (auto) !isDarkMode else false)
    }

    fun setTransparentStatusBar(trasparent: Boolean) {
        window.statusBarColor = if (trasparent) ContextCompat.getColor(
            this,
            android.R.color.transparent
        ) else SurfaceColors.SURFACE_2.getColor(this)
    }

    override fun attachBaseContext(newBase: Context) {
        Log.d(TAG, "attachBaseContext")
        super.attachBaseContext(newBase);
//        super.attachBaseContext(RisuscitoApplication.localeManager.useCustomConfig(newBase))
//        RisuscitoApplication.localeManager.useCustomConfig(this)
        SplitCompat.install(this)
    }

//    override fun applyOverrideConfiguration(overrideConfiguration: Configuration?) {
//        Log.d(TAG, "applyOverrideConfiguration")
//        super.applyOverrideConfiguration(
//            RisuscitoApplication.localeManager.updateConfigurationIfSupported(
//                this,
//                overrideConfiguration
//            )
//        )
//    }

    class NoBackupException internal constructor(val resources: Resources) :
        Exception(resources.getString(R.string.no_restore_found))

    class NoIdException internal constructor() : Exception("no ID linked to this Account")

    fun backupSharedPreferences(userId: String?, userEmail: String?) {
        Log.d(TAG, "backupSharedPreferences $userId")

        if (userId == null)
            throw NoIdException()

        val db = Firebase.firestore

        // Create a query against the collection.
        val query = db.collection(FIREBASE_COLLECTION_IMPOSTAZIONI)
            .whereEqualTo(FIREBASE_FIELD_USER_ID, userId)

        val querySnapshot = Tasks.await(query.get())

        Log.d(TAG, "querySnapshot.documents.size ${querySnapshot.documents.size}")

        val usersPreferences = HashMap<String, Any>()
        usersPreferences[FIREBASE_FIELD_USER_ID] = userId
        usersPreferences[FIREBASE_FIELD_EMAIL] = userEmail.orEmpty()
        usersPreferences[FIREBASE_FIELD_TIMESTAMP] = Date(System.currentTimeMillis())
        usersPreferences[FIREBASE_FIELD_PREFERENCE] =
            PreferenceManager.getDefaultSharedPreferences(this).all

        if (querySnapshot.documents.size > 0) {
            Tasks.await(
                db.collection(FIREBASE_COLLECTION_IMPOSTAZIONI)
                    .document(querySnapshot.documents[0].id).delete()
            )
            Log.d(TAG, "existing deleted")
        }

        // Add a new document with a generated ID
        val documentReference =
            Tasks.await(db.collection(FIREBASE_COLLECTION_IMPOSTAZIONI).add(usersPreferences))
        Log.d(TAG, "DocumentSnapshot added with ID: " + documentReference.id)

    }

    fun restoreSharedPreferences(userId: String?) {
        Log.d(TAG, "backupSharedPreferences $userId")

        if (userId == null)
            throw NoIdException()

        val db = Firebase.firestore

        // Create a query against the collection.
        val query = db.collection(FIREBASE_COLLECTION_IMPOSTAZIONI)
            .whereEqualTo(FIREBASE_FIELD_USER_ID, userId)

        val querySnapshot = Tasks.await(query.get())

        Log.d(TAG, "querySnapshot.documents.size ${querySnapshot.documents.size}")

        if (querySnapshot.documents.size == 0)
            throw NoBackupException(resources)

        val prefEdit = PreferenceManager.getDefaultSharedPreferences(this).edit()
        prefEdit.clear()

        val entries = querySnapshot.documents[0].get(FIREBASE_FIELD_PREFERENCE) as? HashMap<*, *>
        entries?.let {
            for ((key, v) in it) {
                Log.d(TAG, "preference : $key / $v")
                when (v) {
                    is Boolean -> prefEdit.putBoolean(key as? String, v)
                    is Float -> prefEdit.putFloat(key as? String, v)
                    is Int -> prefEdit.putInt(key as? String, v)
                    is Long -> prefEdit.putInt(key as? String, v.toInt())
                    is String -> prefEdit.putString(key as? String, v)
                }
            }
        }
        prefEdit.apply()
        if (PreferenceManager.getDefaultSharedPreferences(this)
                .getString(Utility.SYSTEM_LANGUAGE, StringUtils.EMPTY).isNullOrEmpty()
        )
            RisuscitoApplication.localeManager.setDefaultSystemLanguage(this)
    }

    fun backupDatabase(userId: String?) {
        Log.d(TAG, "backupDatabase $userId")

        if (userId == null)
            throw NoIdException()

        val storage = Firebase.storage
        val storageRef = storage.reference
        val risuscitoDb = RisuscitoDatabase.getInstance(this)

        //BACKUP CANTI
        val cantoRef = deleteExistingFile(storageRef, CANTO_FILE_NAME, userId)
        val backupList = risuscitoDb.cantoDao().backup
        Log.d(TAG, "canto backup size ${backupList.size}")
        putFileToFirebase(cantoRef, backupList, CANTO_FILE_NAME)

        //BACKUP CUSTOM LISTS
        val customListRef = deleteExistingFile(storageRef, CUSTOM_LIST_FILE_NAME, userId)
        val customLists = risuscitoDb.customListDao().all
        Log.d(TAG, "custom list size ${customLists.size}")
        putFileToFirebase(customListRef, customLists, CUSTOM_LIST_FILE_NAME)

        //BACKUP LISTE PERS
        val listePersRef = deleteExistingFile(storageRef, LISTA_PERS_FILE_NAME, userId)
        val listePers = risuscitoDb.listePersDao().all
        Log.d(TAG, "listePers size ${listePers.size}")
        putFileToFirebase(listePersRef, listePers, LISTA_PERS_FILE_NAME)

        //BACKUP LOCAL LINK
        val localLinkRef = deleteExistingFile(storageRef, LOCAL_LINK_FILE_NAME, userId)
        val localLink = risuscitoDb.localLinksDao().all
        Log.d(TAG, "localLink size ${localLink.size}")
        putFileToFirebase(localLinkRef, localLink, LOCAL_LINK_FILE_NAME)

        //BACKUP CONSEGNATI
        val consegnatiRef = deleteExistingFile(storageRef, CONSEGNATO_FILE_NAME, userId)
        val consegnati = risuscitoDb.consegnatiDao().all
        Log.d(TAG, "consegnati size ${consegnati.size}")
        putFileToFirebase(consegnatiRef, consegnati, CONSEGNATO_FILE_NAME)

        //BACKUP CRONOLOGIA
        val cronologiaRef = deleteExistingFile(storageRef, CRONOLOGIA_FILE_NAME, userId)
        val cronologia = risuscitoDb.cronologiaDao().all
        Log.d(TAG, "cronologia size ${cronologia.size}")
        putFileToFirebase(cronologiaRef, cronologia, CRONOLOGIA_FILE_NAME)

        Log.d(TAG, "BACKUP DB COMPLETATO")
    }

    private fun deleteExistingFile(
        storageRef: StorageReference,
        fileName: String,
        userId: String
    ): StorageReference {
        val fileRef = storageRef.child("database_$userId/$fileName.json")

        try {
            Tasks.await(fileRef.delete())
            Log.d(TAG, "Backup esistente cancellato!")
        } catch (e: ExecutionException) {
            if (e.cause is StorageException && (e.cause as? StorageException)?.errorCode == StorageException.ERROR_OBJECT_NOT_FOUND)
                Log.d(TAG, "Backup non trovato!")
            else
                throw e
        }
        return fileRef
    }

    private fun putFileToFirebase(fileRef: StorageReference, jsonObject: Any, fileName: String) {
        val gson =
            GsonBuilder().registerTypeAdapter(Date::class.java, DateTimeSerializer()).create()
        Log.d(TAG, "=== List to JSON ===")
        val jsonList: String = gson.toJson(jsonObject)
        Log.d(TAG, jsonList)

        val exportFile = File("${cacheDir.absolutePath}/$fileName.json")
        Log.d(TAG, "listToXML: exportFile = " + exportFile.absolutePath)
        val output = BufferedWriter(FileWriter(exportFile))
        output.write(jsonList)
        output.close()

        val saveFile = Tasks.await(fileRef.putFile(exportFile.toUri()))
        Log.d(TAG, "DocumentSnapshot added with path: ${saveFile.metadata?.path}")
    }

    fun restoreDatabase(userId: String?) {
        Log.d(TAG, "backupDatabase $userId")

        if (userId == null)
            throw NoIdException()

        val storage = FirebaseStorage.getInstance()
        val storageRef = storage.reference
        val gson =
            GsonBuilder().registerTypeAdapter(Date::class.java, DateTimeDeserializer()).create()
        val risuscitoDb = RisuscitoDatabase.getInstance(this)

        //RESTORE CANTI
        val backupCanti: List<Backup> = gson.fromJson(
            InputStreamReader(
                getFileFromFirebase(
                    storageRef,
                    CANTO_FILE_NAME,
                    userId
                )
            ), object : TypeToken<List<Backup>>() {}.type
        )

        val cantoDao = risuscitoDb.cantoDao()
        cantoDao.truncateTable()
        Log.d(TAG, "Canto Truncated!")
        cantoDao.insertCanto(Canto.defaultCantoData())
        Log.d(TAG, "Canto default data inserted")
        for (backup in backupCanti) {
            Log.d(
                TAG,
                "backupCanto.id + ${backup.id} / backupCanto.savedTab ${backup.savedTab} / backupCanto.savedBarre ${backup.savedBarre}"
            )
            cantoDao.setBackup(
                backup.id,
                backup.zoom,
                backup.scrollX,
                backup.scrollY,
                backup.favorite,
                backup.savedTab,
                backup.savedBarre,
                backup.savedSpeed
            )
        }


        //RESTORE CUSTOM LIST
        val backupCustomList: List<CustomList> = gson.fromJson(
            InputStreamReader(
                getFileFromFirebase(
                    storageRef,
                    CUSTOM_LIST_FILE_NAME,
                    userId
                )
            ), object : TypeToken<List<CustomList>>() {}.type
        )

        val customListDao = risuscitoDb.customListDao()

        customListDao.truncateTable()
        Log.d(TAG, "Custom List Truncated!")
        for (backup in backupCustomList) {
            Log.d(
                TAG,
                "backupCustomList.id + ${backup.id} / backupCustomList.position ${backup.position} / backupCustomList.idCanto ${backup.idCanto} / backupCustomList.timestamp ${backup.timestamp}"
            )
            if (cantoDao.getCantoById(backup.idCanto) != null)
                customListDao.insertPosition(backup)
        }


        //RESTORE LISTA PERS
        val backupListePers: List<ListaPers> = gson.fromJson(
            InputStreamReader(
                getFileFromFirebase(
                    storageRef,
                    LISTA_PERS_FILE_NAME,
                    userId
                )
            ), object : TypeToken<List<ListaPers>>() {}.type
        )

        val listePersDao = risuscitoDb.listePersDao()
        listePersDao.truncateTable()
        Log.d(TAG, "Liste Pers Truncated!")
        for (backup in backupListePers) {
            Log.d(
                TAG,
                "backupListePers.id + ${backup.id} / backupCustomList.position ${backup.titolo}"
            )
            backup.lista?.let {
                for (i in 0 until it.numPosizioni) {
                    if (it.getCantoPosizione(i).isNotEmpty() && cantoDao.getCantoById(
                            it.getCantoPosizione(i).toInt()
                        ) == null
                    )
                        it.removeCanto(i)
                }
            }
            listePersDao.insertLista(backup)
        }


        //RESTORE LOCAL LINK
        val backupLocalLink: List<LocalLink> = gson.fromJson(
            InputStreamReader(
                getFileFromFirebase(
                    storageRef,
                    LOCAL_LINK_FILE_NAME,
                    userId
                )
            ), object : TypeToken<List<LocalLink>>() {}.type
        )

        val localLinkDao = risuscitoDb.localLinksDao()
        localLinkDao.truncateTable()
        Log.d(TAG, "Liste Pers Truncated!")
        for (backup in backupLocalLink) {
            Log.d(
                TAG,
                "backupLocalLink.idCanto + ${backup.idCanto} / backupLocalLink.localPath ${backup.localPath}"
            )
            if (cantoDao.getCantoById(backup.idCanto) != null)
                localLinkDao.insertLocalLink(backup)
        }


        //RESTORE CONSEGNATI
        val backupConsegnati: List<Consegnato> = gson.fromJson(
            InputStreamReader(
                getFileFromFirebase(
                    storageRef,
                    CONSEGNATO_FILE_NAME,
                    userId
                )
            ), object : TypeToken<List<Consegnato>>() {}.type
        )

        val consegnatiDao = risuscitoDb.consegnatiDao()
        consegnatiDao.truncateTable()
        Log.d(TAG, "Liste Pers Truncated!")
        for (backup in backupConsegnati) {
            Log.d(
                TAG,
                "backupConsegnati.idConsegnato + ${backup.idConsegnato} / backupConsegnati.idCanto ${backup.idCanto}"
            )
            if (cantoDao.getCantoById(backup.idCanto) != null)
                consegnatiDao.insertConsegnati(backup)
        }


        //RESTORE CRONOLOGIA
        val backupCronologia: List<Cronologia> = gson.fromJson(
            InputStreamReader(
                getFileFromFirebase(
                    storageRef,
                    CRONOLOGIA_FILE_NAME,
                    userId
                )
            ), object : TypeToken<List<Cronologia>>() {}.type
        )

        val cronologiaDao = risuscitoDb.cronologiaDao()
        cronologiaDao.truncateTable()
        Log.d(TAG, "Liste Pers Truncated!")
        for (backup in backupCronologia) {
            Log.d(
                TAG,
                "backupCronologia.idCanto + ${backup.idCanto} / backupCronologia.ultimaVisita ${backup.ultimaVisita}"
            )
            if (cantoDao.getCantoById(backup.idCanto) != null)
                cronologiaDao.insertCronologia(backup)
        }

        Log.d(TAG, "RESTORE DB COMPLETATO")
    }

    private fun getFileFromFirebase(
        storageRef: StorageReference,
        fileName: String,
        userId: String
    ): InputStream {
        try {

            val cantoRef = storageRef.child("database_$userId/$fileName.json")
            val fileStream = Tasks.await(cantoRef.stream)
            return fileStream.stream

        } catch (e: ExecutionException) {
            Log.e(TAG, e.localizedMessage, e)
            if (e.cause is StorageException && (e.cause as? StorageException)?.errorCode == StorageException.ERROR_OBJECT_NOT_FOUND)
                throw NoBackupException(resources)
            else
                throw e
        }
    }

    private val showInfoBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // Implement UI change code here once notification is received
            val title = intent.getStringExtra(RisuscitoMessagingService.MESSAGE_TITLE) ?: getString(
                R.string.general_message
            )
            val body = intent.getStringExtra(RisuscitoMessagingService.MESSAGE_BODY).orEmpty()
            SimpleDialogFragment.show(
                SimpleDialogFragment.Builder(NOTIFICATION_DIALOG)
                    .title(title)
                    .icon(R.drawable.info_24px)
                    .content(body)
                    .positiveButton(R.string.ok),
                supportFragmentManager
            )
        }
    }

    private val catalogReadyBR = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // Implement UI change code here once notification is received
            try {
                Log.d(TAG, MusicService.MSG_RETRIEVE_DONE)
                val done = intent.getBooleanExtra(MusicService.MSG_RETRIEVE_DONE, false)
                Log.d(TAG, "MSG_RETRIEVE_DONE: $done")
                mViewModel.catalogRefreshReady.value = done
            } catch (e: IllegalArgumentException) {
                Log.e(TAG, e.localizedMessage, e)
            }

        }
    }

    // Callback that ensures that we are showing the controls
    private val mMediaControllerCallback = object : MediaControllerCompat.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackStateCompat) {
            Log.d(TAG, "onPlaybackStateChanged: ${state.state}")
            mViewModel.lastPlaybackState.value = state
        }

        override fun onMetadataChanged(metadata: MediaMetadataCompat?) {
            Log.d(TAG, "onMetadataChanged")
            if (metadata != null) {
                mViewModel.medatadaCompat.value = metadata
            }
        }
    }

    private var mMediaBrowser: MediaBrowserCompat? = null
    private val mConnectionCallback = object : MediaBrowserCompat.ConnectionCallback() {
        override fun onConnected() {
            Log.d(TAG, "onConnected")
            try {
                mMediaBrowser?.let {
                    val mediaController = MediaControllerCompat(
                        this@ThemeableActivity, it.sessionToken
                    )
                    MediaControllerCompat.setMediaController(
                        this@ThemeableActivity,
                        mediaController
                    )
                    mediaController.registerCallback(mMediaControllerCallback)
                    mViewModel.lastPlaybackState.value = mediaController.playbackState
                    mViewModel.playerConnected.value = true
                } ?: Log.e(TAG, "onConnected: mMediaBrowser is NULL")
            } catch (e: RemoteException) {
                Log.e(TAG, "onConnected: could not connect media controller", e)
            }

        }

        override fun onConnectionFailed() {
            Log.e(TAG, "onConnectionFailed")
        }

        override fun onConnectionSuspended() {
            Log.d(TAG, "onConnectionSuspended")
            val mediaController =
                MediaControllerCompat.getMediaController(this@ThemeableActivity)
            mediaController?.unregisterCallback(mMediaControllerCallback)
            MediaControllerCompat.setMediaController(this@ThemeableActivity, null)
        }
    }

    fun stopMedia() {
        Log.d(TAG, "stopMedia: ")
        if (mViewModel.lastPlaybackState.value?.state != PlaybackStateCompat.STATE_STOPPED) {
            val controller = MediaControllerCompat.getMediaController(this)
            controller?.transportControls?.stop()
        }
    }

    companion object {
        internal val TAG = ThemeableActivity::class.java.canonicalName

        internal const val FIREBASE_FIELD_USER_ID = "userId"
        internal const val FIREBASE_FIELD_PREFERENCE = "userPreferences"
        internal const val FIREBASE_FIELD_EMAIL = "userEmail"
        internal const val FIREBASE_FIELD_TIMESTAMP = "timestamp"
        internal const val FIREBASE_COLLECTION_IMPOSTAZIONI = "Impostazioni"
        internal const val CANTO_FILE_NAME = "Canto"
        internal const val NOTIFICATION_DIALOG = "NOTIFICATION_DIALOG"
        internal const val CUSTOM_LIST_FILE_NAME = "CustomList"
        internal const val LISTA_PERS_FILE_NAME = "ListaPers"
        internal const val LOCAL_LINK_FILE_NAME = "LocalLink"
        internal const val CONSEGNATO_FILE_NAME = "Consegnato"
        internal const val CRONOLOGIA_FILE_NAME = "Cronologia"

    }

}
