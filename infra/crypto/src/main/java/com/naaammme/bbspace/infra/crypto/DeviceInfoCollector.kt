package com.naaammme.bbspace.infra.crypto

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import android.provider.Settings
import androidx.core.content.edit
import androidx.core.content.getSystemService
import com.naaammme.bbspace.core.common.BiliConstants
import datacenter.hakase.protobuf.AndroidDeviceInfoOuterClass
import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random
import java.util.UUID

class DeviceInfoCollector(context: Context, private val deviceIdentity: DeviceIdentity) {
    companion object {
        private const val BILI_FILES_PATH = "/data/user/0/tv.danmaku.bili/files"
        private const val DEFAULT_OAID = "00000000-0000-0000-0000-000000000000"
    }

    private data class Battery(
        val capacity: Int,
        val state: String,
        val present: Boolean,
        val technology: String,
        val temperature: Int,
        val voltage: Int,
        val plugged: Int,
        val health: Int
    )

    private data class Sensors(
        val names: String,
        val infoList: List<AndroidDeviceInfoOuterClass.SensorInfo>
    )

    private data class Snapshot(
        val battery: Battery,
        val light: Int,
        val totalMem: Long,
        val freeMem: Long,
        val totalSpace: Long,
        val freeStorage: Long,
        val sensorList: String,
        val sensorInfoList: List<AndroidDeviceInfoOuterClass.SensorInfo>,
        val cpuFreq: Long,
        val cpuModel: String,
        val cpuVendor: String,
        val roSerialNo: String,
        val lightIntensity: String,
        val deviceAngles: List<Float>,
        val uid: String,
        val lastDumpTs: Long
    )

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("device_info_cache", Context.MODE_PRIVATE)
    private val displayMetrics = appContext.resources.displayMetrics
    private val sensorManager = appContext.getSystemService<SensorManager>()!!

    fun collect(mid: Long = 0): AndroidDeviceInfoOuterClass.AndroidDeviceInfo {
        val snapshot = snapshot()
        return AndroidDeviceInfoOuterClass.AndroidDeviceInfo.newBuilder().apply {
            sdkver = "0.2.4"
            appId = BiliConstants.APP_ID.toString()
            appVersion = BiliConstants.VERSION
            appVersionCode = BiliConstants.BUILD_STR
            if (mid > 0) {
                this.mid = mid.toString()
            }
            chid = BiliConstants.CHANNEL
            fts = getStableLong("fts") { System.currentTimeMillis() / 1000 }
            buvidLocal = deviceIdentity.fp
            first = 0
            proc = "tv.danmaku.bili"
            net = ""
            band = Build.getRadioVersion() ?: ""

            osver = deviceIdentity.osVer
            t = System.currentTimeMillis()
            cpuCount = Runtime.getRuntime().availableProcessors()
            model = deviceIdentity.model
            brand = deviceIdentity.brand
            screen = "${displayMetrics.widthPixels},${displayMetrics.heightPixels},${displayMetrics.densityDpi}"
            cpuModel = snapshot.cpuModel
            btmac = ""
            boot = SystemClock.elapsedRealtime()
            emu = "000"
            oid = "46000"
            network = "WIFI"
            mem = snapshot.totalMem
            sensor = snapshot.sensorList
            cpuFreq = snapshot.cpuFreq
            cpuVendor = snapshot.cpuVendor
            sim = ""
            brightness = snapshot.light

            putProps("ro.build.date.utc", buildDateUtc())
            putProps("ro.product.device", deviceIdentity.device)
            putProps("ro.serialno", snapshot.roSerialNo)
            putProps("ro.build.fingerprint", deviceIdentity.buildFingerprint)
            putProps("ro.product.manufacturer", deviceIdentity.manufacturer)
            putProps("ro.build.display.id", deviceIdentity.buildId)

            wifimac = ""
            mac = deviceIdentity.mac
            adid = deviceIdentity.androidId
            os = BiliConstants.PLATFORM
            imei = ""
            cell = ""
            imsi = ""
            iccid = ""
            camcnt = 0
            campx = ""
            totalSpace = snapshot.totalSpace
            axposed = "false"
            maps = ""
            files = BILI_FILES_PATH
            virtual = "0"
            virtualproc = "[]"
            gadid = ""
            glimit = ""
            apps = "[]"
            guid = getStableString("guid") { UUID.randomUUID().toString() }
            uid = snapshot.uid
            root = 0
            camzoom = ""
            camlight = ""
            oaid = DEFAULT_OAID
            udid = deviceIdentity.androidId
            vaid = ""
            aaid = ""

            androidapp20 = "[]"
            androidappcnt = 0
            androidsysapp20 = "[]"
            this.battery = snapshot.battery.capacity
            batteryState = snapshot.battery.state
            bssid = ""
            buildId = deviceIdentity.buildId
            countryIso = "CN"
            freeMemory = snapshot.freeMem
            fstorage = snapshot.freeStorage.toString()
            kernelVersion = System.getProperty("os.version") ?: ""
            languages = "zh"
            ssid = ""
            systemvolume = 0
            wifimaclist = ""
            memory = snapshot.totalMem
            strBattery = snapshot.battery.capacity.toString()
            isRoot = false
            strBrightness = snapshot.light.toString()
            strAppId = BiliConstants.APP_ID.toString()
            ip = ""
            userAgent = ""
            lightIntensity = snapshot.lightIntensity

            snapshot.deviceAngles.forEach(::addDeviceAngle)

            gpsSensor = 1
            speedSensor = 1
            linearSpeedSensor = 1
            gyroscopeSensor = 1
            biometric = 1
            addBiometrics("touchid")
            lastDumpTs = snapshot.lastDumpTs
            location = ""
            country = ""
            city = ""
            dataActivityState = 0
            dataConnectState = 0
            dataNetworkType = 0
            voiceNetworkType = 0
            voiceServiceState = 0
            usbConnected = 0
            adbEnabled = readAdbEnabled()
            uiVersion = "14.0.0"
            addAllSensorsInfo(snapshot.sensorInfoList)

            drmid = DeviceIdentity.getDrmId()
            batteryPresent = snapshot.battery.present
            batteryTechnology = snapshot.battery.technology
            batteryTemperature = snapshot.battery.temperature
            batteryVoltage = snapshot.battery.voltage
            batteryPlugged = snapshot.battery.plugged
            batteryHealth = snapshot.battery.health
        }.build()
    }

    fun buildGuestDeviceInfoJson(): String {
        return JSONObject().apply {
            put("DeviceType", "Android")
            put("Buvid", deviceIdentity.buvid)
            put("fts", (System.currentTimeMillis() / 1000 - 30 * 24 * 3600).toString())
            put("BuildHost", "android-build")
            put("BuildDisplay", deviceIdentity.buildId)
            put("BuildFingerprint", deviceIdentity.buildFingerprint)
            put("BuildBrand", deviceIdentity.brand)
            if (deviceIdentity.mac.isNotEmpty()) put("MAC", deviceIdentity.mac)
            if (deviceIdentity.androidId.isNotEmpty()) put("AndroidID", deviceIdentity.androidId)
        }.toString()
    }

    fun buildLoginDeviceMetaJson(mid: Long = 0): String {
        val info = collect(mid)
        return JSONObject().apply {
            put("sdkver", info.sdkver)
            put("app_id", info.appId)
            put("app_version", info.appVersion)
            put("app_version_code", info.appVersionCode)
            if (info.mid.isNotEmpty()) put("mid", info.mid)
            put("chid", info.chid)
            put("fts", info.fts)
            put("buvid_local", info.buvidLocal)
            put("first", info.first)
            put("proc", info.proc)
            put("net", info.net)
            put("band", info.band)
            put("osver", info.osver)
            put("t", info.t)
            put("cpuCount", info.cpuCount)
            put("model", info.model)
            put("brand", info.brand)
            put("screen", info.screen)
            put("cpuModel", info.cpuModel)
            put("btmac", info.btmac)
            put("boot", info.boot)
            put("emu", info.emu)
            put("oid", info.oid)
            put("network", info.network)
            put("mem", info.mem)
            put("sensor", info.sensor)
            put("cpuFreq", info.cpuFreq)
            put("cpuVendor", info.cpuVendor)
            put("sim", info.sim)
            put("brightness", info.brightness)
            put("wifimac", info.wifimac)
            put("adid", info.adid)
            put("os", info.os)
            put("imei", info.imei)
            put("cell", info.cell)
            put("imsi", info.imsi)
            put("iccid", info.iccid)
            put("camcnt", info.camcnt)
            put("campx", info.campx)
            put("totalSpace", info.totalSpace)
            put("axposed", info.axposed)
            put("maps", info.maps)
            put("files", info.files)
            put("virtual", info.getVirtual())
            put("virtualproc", info.virtualproc)
            put("gadid", info.gadid)
            put("glimit", info.glimit)
            put("apps", info.apps)
            put("guid", info.guid)
            put("uid", info.uid)
            put("root", info.root)
            put("camzoom", info.camzoom)
            put("camlight", info.camlight)
            put("oaid", info.oaid)
            put("udid", info.udid)
            put("vaid", info.vaid)
            put("aaid", info.aaid)
            put("androidapp20", info.androidapp20)
            put("androidappcnt", info.androidappcnt)
            put("androidsysapp20", info.androidsysapp20)
            put("battery", info.battery)
            put("batteryState", info.batteryState)
            put("bssid", info.bssid)
            put("build_id", info.buildId)
            put("countryIso", info.countryIso)
            put("free_memory", info.freeMemory)
            put("fstorage", info.fstorage)
            put("kernel_version", info.kernelVersion)
            put("languages", info.languages)
            put("mac", info.mac)
            put("ssid", info.ssid)
            put("systemvolume", info.systemvolume)
            put("wifimaclist", info.wifimaclist)
            put("memory", info.memory)
            put("str_battery", info.strBattery)
            put("is_root", info.isRoot)
            put("str_brightness", info.strBrightness)
            put("str_app_id", info.strAppId)
            put("ip", info.ip)
            put("user_agent", info.userAgent)
            put("light_intensity", info.lightIntensity)
            put("device_angle", JSONArray().apply {
                info.deviceAngleList.forEach { put(it) }
            })
            put("gps_sensor", info.gpsSensor)
            put("speed_sensor", info.speedSensor)
            put("linear_speed_sensor", info.linearSpeedSensor)
            put("gyroscope_sensor", info.gyroscopeSensor)
            put("biometric", info.biometric)
            put("biometrics", JSONArray().apply {
                info.biometricsList.forEach { put(it) }
            })
            put("last_dump_ts", info.lastDumpTs)
            put("location", info.location)
            put("country", info.country)
            put("city", info.city)
            put("data_activity_state", info.dataActivityState)
            put("data_connect_state", info.dataConnectState)
            put("data_network_type", info.dataNetworkType)
            put("voice_network_type", info.voiceNetworkType)
            put("voice_service_state", info.voiceServiceState)
            put("usb_connected", info.usbConnected)
            put("adb_enabled", info.adbEnabled)
            put("ui_version", info.uiVersion)
            put("drmid", info.drmid)
            put("battery_present", info.batteryPresent)
            put("battery_technology", info.batteryTechnology)
            put("battery_temperature", info.batteryTemperature)
            put("battery_voltage", info.batteryVoltage)
            put("battery_plugged", info.batteryPlugged)
            put("battery_health", info.batteryHealth)
            put("props", JSONObject(info.propsMap))
            put("sys", JSONObject().apply {
                put("fingerprint", Build.FINGERPRINT)
                put("device", Build.DEVICE)
                put("display", Build.DISPLAY)
                put("manufacturer", Build.MANUFACTURER)
                put("hardware", Build.HARDWARE)
                put("product", Build.PRODUCT)
                put("cpu_model_name", info.cpuModel)
                put("cpu_abi_list", Build.SUPPORTED_ABIS.joinToString(","))
            })
        }.toString()
    }

    private fun snapshot(): Snapshot {
        val battery = readBattery()
        val light = getStableInt("light") { Random.nextInt(50, 200) }
        val sensors = readSensors()
        return Snapshot(
            battery = battery,
            light = light,
            totalMem = readTotalMem(),
            freeMem = readFreeMem(),
            totalSpace = readTotalSpace(),
            freeStorage = readFreeStorage(),
            sensorList = sensors.names,
            sensorInfoList = sensors.infoList,
            cpuFreq = readCpuMaxFreq(),
            cpuModel = readCpuModel(),
            cpuVendor = readCpuVendor(),
            roSerialNo = getStableString("ro.serialno") {
                (0..7).joinToString("") { Random.nextInt(16).toString(16) }
            },
            lightIntensity = "%.3f".format(getStableFloat("light_intensity") { Random.nextDouble(50.0, 600.0) }),
            deviceAngles = listOf(
                Random.nextDouble(-180.0, 180.0).toFloat(),
                Random.nextDouble(-180.0, 180.0).toFloat(),
                Random.nextDouble(-180.0, 180.0).toFloat()
            ),
            uid = getStableString("uid") { Random.nextInt(10000, 10053).toString() },
            lastDumpTs = getStableLong("last_dump_ts") { System.currentTimeMillis() }
        )
    }

    private fun buildDateUtc(): String {
        return (System.currentTimeMillis() / 1000 - Random.nextInt(86400 * 30, 86400 * 365)).toString()
    }

    private fun readBattery(): Battery {
        val intent = runCatching {
            appContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }.getOrNull()

        val capacity = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) ?: 0
        val rawTemp = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        val voltage = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
        val status = intent?.getIntExtra(
            BatteryManager.EXTRA_STATUS,
            BatteryManager.BATTERY_STATUS_DISCHARGING
        ) ?: BatteryManager.BATTERY_STATUS_DISCHARGING
        val state = when (status) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "BATTERY_STATUS_CHARGING"
            BatteryManager.BATTERY_STATUS_FULL -> "BATTERY_STATUS_FULL"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "BATTERY_STATUS_NOT_CHARGING"
            else -> "BATTERY_STATUS_DISCHARGING"
        }
        val plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val present = intent?.getBooleanExtra(BatteryManager.EXTRA_PRESENT, true) ?: true
        val technology = intent?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: "Li-ion"
        val health = intent?.getIntExtra(
            BatteryManager.EXTRA_HEALTH,
            BatteryManager.BATTERY_HEALTH_GOOD
        ) ?: BatteryManager.BATTERY_HEALTH_GOOD

        return Battery(
            capacity = capacity,
            state = state,
            present = present,
            technology = technology,
            temperature = rawTemp,
            voltage = voltage,
            plugged = plugged,
            health = health
        )
    }

    private fun readTotalSpace(): Long {
        return runCatching {
            StatFs(Environment.getDataDirectory().path).let {
                it.blockCountLong * it.blockSizeLong
            }
        }.getOrDefault(32_000_000_000L)
    }

    private fun readFreeStorage(): Long {
        return runCatching {
            StatFs(Environment.getDataDirectory().path).let {
                it.availableBlocksLong * it.blockSizeLong
            }
        }.getOrDefault(16_000_000_000L)
    }

    private fun readTotalMem(): Long {
        return runCatching {
            val memInfo = android.app.ActivityManager.MemoryInfo()
            (appContext.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager)
                .getMemoryInfo(memInfo)
            memInfo.totalMem
        }.getOrDefault(Runtime.getRuntime().maxMemory())
    }

    private fun readFreeMem(): Long {
        return runCatching {
            val memInfo = android.app.ActivityManager.MemoryInfo()
            (appContext.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager)
                .getMemoryInfo(memInfo)
            memInfo.availMem
        }.getOrDefault(Runtime.getRuntime().maxMemory() / 2)
    }

    private fun readSensors(): Sensors {
        return runCatching {
            val sensors = sensorManager.getSensorList(Sensor.TYPE_ALL)
            Sensors(
                names = sensors.map { sensor -> "\"${sensor.name}-${sensor.vendor}\"" }.toString(),
                infoList = sensors.map { sensor ->
                    AndroidDeviceInfoOuterClass.SensorInfo.newBuilder().apply {
                        name = sensor.name
                        vendor = sensor.vendor
                        version = sensor.version
                        type = sensor.type
                        maxRange = sensor.maximumRange
                        resolution = sensor.resolution
                        power = sensor.power
                        minDelay = sensor.minDelay
                    }.build()
                }
            )
        }.getOrDefault(Sensors("[\"accelerometer\", \"gyroscope\", \"magnetometer\"]", emptyList()))
    }

    private fun readCpuMaxFreq(): Long {
        return runCatching {
            java.io.File("/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq")
                .readText().trim().toLong()
        }.getOrDefault(2450000L)
    }

    private fun readCpuModel(): String {
        return runCatching {
            java.io.File("/proc/cpuinfo").useLines { lines ->
                lines.firstOrNull { "model name" in it }
                    ?.substringAfter(": ")
                    ?.trim()
                    ?: ""
            }
        }.getOrDefault("")
    }

    private fun readCpuVendor(): String {
        val abis = Build.SUPPORTED_ABIS.firstOrNull() ?: ""
        return when {
            abis.contains("arm64", ignoreCase = true) || abis.contains("aarch64", ignoreCase = true) -> "ARM"
            abis.contains("x86_64", ignoreCase = true) || abis.contains("x86", ignoreCase = true) -> "Intel"
            else -> "ARM"
        }
    }

    private fun readAdbEnabled(): Int {
        return if (Settings.Global.getInt(appContext.contentResolver, Settings.Global.ADB_ENABLED, 0) != 0) 1 else 0
    }

    private fun getStableString(key: String, generate: () -> String): String {
        val cached = prefs.getString(key, null)
        if (cached != null) return cached
        return generate().also { prefs.edit { putString(key, it) } }
    }

    private fun getStableInt(key: String, generate: () -> Int): Int {
        if (prefs.contains(key)) return prefs.getInt(key, 0)
        return generate().also { prefs.edit { putInt(key, it) } }
    }

    private fun getStableLong(key: String, generate: () -> Long): Long {
        if (prefs.contains(key)) return prefs.getLong(key, 0)
        return generate().also { prefs.edit { putLong(key, it) } }
    }

    private fun getStableFloat(key: String, generate: () -> Double): Double {
        if (prefs.contains(key)) return java.lang.Double.longBitsToDouble(prefs.getLong(key, 0))
        val value = generate()
        prefs.edit { putLong(key, java.lang.Double.doubleToRawLongBits(value)) }
        return value
    }
}
