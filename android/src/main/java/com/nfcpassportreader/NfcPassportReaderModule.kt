package com.nfcpassportreader

import android.annotation.SuppressLint
import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.nfcpassportreader.utils.*
import com.facebook.react.bridge.ActivityEventListener
import com.facebook.react.bridge.LifecycleEventListener
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.modules.core.DeviceEventManagerModule
import com.nfcpassportreader.utils.JsonToReactMap
import com.nfcpassportreader.utils.serializeToMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jmrtd.BACKey
import org.jmrtd.BACKeySpec
import org.jmrtd.lds.icao.MRZInfo
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale
import com.nfcpassportreader.dto.*
import net.sf.scuba.smartcards.CardService
import org.jmrtd.PassportService
import org.jmrtd.lds.CardSecurityFile
import org.jmrtd.lds.PACEInfo
import org.jmrtd.lds.icao.DG11File
import org.jmrtd.lds.icao.COMFile
import org.jmrtd.lds.icao.DG1File
import org.jmrtd.lds.icao.DG2File
import org.jmrtd.lds.icao.DG7File
import org.jmrtd.lds.SODFile
import org.jmrtd.lds.iso19794.FaceImageInfo
import org.jmrtd.lds.LDSFile
import java.io.ByteArrayInputStream

val DOCUMENT_READING_PROGRESS = "onDocumentReadingProgress"
val NFC_STATE_CHANGED = "onNfcStateChanged"

class NfcPassportReaderModule(reactContext: ReactApplicationContext) :
  ReactContextBaseJavaModule(reactContext), LifecycleEventListener, ActivityEventListener {

  private val bitmapUtil = BitmapUtil(reactContext)
  private val dateUtil = DateUtil()
  private var adapter: NfcAdapter? = NfcAdapter.getDefaultAdapter(reactContext)
  private var bacKey: BACKeySpec? = null
  private var includeImages = false
  private var isReading = false
  private val jsonToReactMap = JsonToReactMap()
  private var _promise: Promise? = null
  private val inputDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
  private val outputDateFormat = SimpleDateFormat("yyMMdd", Locale.getDefault())

  init {
    reactApplicationContext.addLifecycleEventListener(this)
    reactApplicationContext.addActivityEventListener(this)

    val filter = IntentFilter(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED)
    reactApplicationContext.registerReceiver(NfcStatusReceiver(), filter)
  }

  inner class NfcStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
      if (NfcAdapter.ACTION_ADAPTER_STATE_CHANGED == intent?.action) {
        val state = intent.getIntExtra(NfcAdapter.EXTRA_ADAPTER_STATE, NfcAdapter.STATE_OFF)
        when (state) {
          NfcAdapter.STATE_OFF -> {
            sendEvent(NFC_STATE_CHANGED, "off")
          }

          NfcAdapter.STATE_ON -> {
            sendEvent(NFC_STATE_CHANGED, "on")
          }

          NfcAdapter.STATE_TURNING_OFF -> {
            // NFC kapanıyor
          }

          NfcAdapter.STATE_TURNING_ON -> {
            // NFC açılıyor
          }
        }
      }
    }
  }

  override fun getName(): String {
    return NAME
  }

  override fun onHostResume() {
    try {
      adapter?.let {
        currentActivity?.let { activity ->
          val intent = Intent(activity, activity.javaClass).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
          }

          val pendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent
              .getActivity(
                activity, 0,
                intent,
                PendingIntent.FLAG_MUTABLE
              )
          } else {
            PendingIntent
              .getActivity(
                activity, 0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT
              )
          }

          val filter = arrayOf(arrayOf("android.nfc.tech.IsoDep"))

          it.enableForegroundDispatch(
            activity,
            pendingIntent,
            null,
            filter
          )
        } ?: run {
          Log.e("NfcPassportReader", "CurrentActivity is null")
        }
      } ?: run {
        Log.e("NfcPassportReader", "NfcAdapter is null")
      }
    } catch (e: Exception) {
      Log.e("NfcPassportReader", e.message ?: "Unknown Error")
    }
  }

  override fun onHostPause() {
  }

  override fun onHostDestroy() {
    adapter?.disableForegroundDispatch(currentActivity)
  }

  override fun onActivityResult(p0: Activity?, p1: Int, p2: Int, p3: Intent?) {
  }

  override fun onNewIntent(p0: Intent?) {
    p0?.let { intent ->
      if (!isReading) return

      if (NfcAdapter.ACTION_TECH_DISCOVERED == intent.action) {
        val tag = intent.extras!!.getParcelable<Tag>(NfcAdapter.EXTRA_TAG)

        if (listOf(*tag!!.techList).contains("android.nfc.tech.IsoDep")) {
          CoroutineScope(Dispatchers.IO).launch {
            try {
              val result = readPassport(IsoDep.get(tag), bacKey!!)

              val map = result.serializeToMap()
              val reactMap = jsonToReactMap.convertJsonToMap(JSONObject(map))

              sendEvent(DOCUMENT_READING_PROGRESS, "SUCCESS")
              _promise?.resolve(reactMap)
            } catch (e: Exception) {
              sendEvent(DOCUMENT_READING_PROGRESS, "ERROR")
              reject(e)
            }
          }
        } else {
          reject(Exception("Tag tech is not IsoDep"))
        }
      }
    }
  }

  private fun readPassport(isoDep: IsoDep, bacKey: BACKeySpec): NfcResult {
    isoDep.timeout = 10000

    sendEvent(DOCUMENT_READING_PROGRESS, "AUTH")

    val cardService = CardService.getInstance(isoDep)
    cardService.open()

    val service = PassportService(
      cardService,
      PassportService.NORMAL_MAX_TRANCEIVE_LENGTH,
      PassportService.DEFAULT_MAX_BLOCKSIZE,
      false,
      false
    )
    service.open()

    var paceSucceeded = false
    try {
      val cardSecurityFile =
        CardSecurityFile(service.getInputStream(PassportService.EF_CARD_SECURITY))
      val securityInfoCollection = cardSecurityFile.securityInfos

      for (securityInfo in securityInfoCollection) {
        if (securityInfo is PACEInfo) {
          service.doPACE(
            bacKey,
            securityInfo.objectIdentifier,
            PACEInfo.toParameterSpec(securityInfo.parameterId),
            null
          )
          paceSucceeded = true
        }
      }
    } catch (e: Exception) {
      e.printStackTrace()
    }

    service.sendSelectApplet(paceSucceeded)

    if (!paceSucceeded) {
      try {
        service.getInputStream(PassportService.EF_COM).read()
      } catch (e: Exception) {
        e.printStackTrace()

        service.doBAC(bacKey)
      }
    }


    val nfcResult = NfcResult()
    val dgs = DgsData()
    // val comIn = service.getInputStream(PassportService.EF_COM)
    // if (comIn != null) {
    // val comFile = COMFile(comIn)
    // }

    sendEvent(DOCUMENT_READING_PROGRESS, "SOD")
    val sodIn = service.getInputStream(PassportService.EF_SOD)
    val sodFile = SODFile(sodIn)
    if (sodIn != null) {
      nfcResult.sod = byteArrayToHexRep(sodFile.getEncoded())
    }

    sendEvent(DOCUMENT_READING_PROGRESS, "DG1")
    val dg1In = service.getInputStream(PassportService.EF_DG1)
    val dg1File = DG1File(dg1In)
    if (dg1In != null) {
      dgs.DG1 = byteArrayToHexRep(dg1File.getEncoded())
    }

    sendEvent(DOCUMENT_READING_PROGRESS, "DG2")
    val dg2In = service.getInputStream(PassportService.EF_DG2)
    if (dg2In != null) {
      val dg2Data = ByteArray(dg2In.getLength())
      dg2In.read(dg2Data)
      val byteArrayInputStream = ByteArrayInputStream(dg2Data)
      val dg2File = DG2File(byteArrayInputStream)
      dgs.DG2 = byteArrayToHexRep(dg2File.getEncoded())
    }
    // send success event

    sendEvent(DOCUMENT_READING_PROGRESS, "DG7")
    // val dg7In = service.getInputStream(PassportService.EF_DG7)
    // val dg7File = DG7File(dg7In)
    // if (dg7In != null) {
      // nfcResult.dgs = DgsData(
        // DG7 = byteArrayToHexRep(dg7File.getEncoded())
      // )
    // }

    sendEvent(DOCUMENT_READING_PROGRESS, "DG11")
    // val dg11In = service.getInputStream(PassportService.EF_DG11)
    // val dg11File = DG11File(dg11In)
    // if (dg11In != null) {
      // nfcResult.dgs = DgsData(
        // DG11 = byteArrayToHexRep(dg11File.getEncoded())
      // )
    // }

    nfcResult.dgs = dgs
    return nfcResult;
  }

  private fun intToHexRep(v: Int): String {
    return String.format("%02X", v)
  }

  private fun byteArrayToHexRep(byteArray: ByteArray): String {
    val hexString = StringBuilder()
    for (b in byteArray) {
        hexString.append(intToHexRep(b.toInt() and 0xFF))
    }
    return hexString.toString()
  }

  private fun sendEvent(eventName: String, params: Any?) {
    reactApplicationContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
      .emit(eventName, params)
  }

  private fun reject(e: Exception) {
    isReading = false
    bacKey = null
    _promise?.reject(e)
  }

  @ReactMethod
  fun startReading(mrzKey: String, promise: Promise) {
    if (mrzKey.isNotEmpty()) {
      _promise = promise
      val length = mrzKey.length
      val doe = mrzKey.substring(length - 7, length - 1)
      val dob = mrzKey.substring(length - 14, length - 8)
      val docNumber = mrzKey.substring(0, length - 15)
      this.bacKey = BACKey(docNumber, dob, doe)
      isReading = true
    } else {
      throw Exception("MRZ key is empty")
    }
  }

  @ReactMethod
  fun stopReading() {
    isReading = false
    bacKey = null
  }

  @ReactMethod
  fun isNfcEnabled(promise: Promise) {
    promise.resolve(NfcAdapter.getDefaultAdapter(reactApplicationContext)?.isEnabled ?: false)
  }

  @ReactMethod
  fun isNfcSupported(promise: Promise) {
    promise.resolve(reactApplicationContext.packageManager.hasSystemFeature(PackageManager.FEATURE_NFC))
  }

  @SuppressLint("QueryPermissionsNeeded")
  @ReactMethod
  fun openNfcSettings(promise: Promise) {
    val intent = Intent(Settings.ACTION_NFC_SETTINGS)
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (intent.resolveActivity(reactApplicationContext.packageManager) != null) {
      reactApplicationContext.startActivity(intent)
      promise.resolve(true)
    } else {
      promise.reject(Exception("Activity not found"))
    }
  }

  companion object {
    const val NAME = "NfcPassportReader"
  }
}
