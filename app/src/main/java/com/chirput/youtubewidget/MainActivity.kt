package com.chirput.youtubewidget

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.FrameLayout
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout

private const val APP_WIDGET_HOST_ID = 101

class MainActivity : AppCompatActivity() {

    private var appWidgetId: Int = 0
    private lateinit var appWidgetManager: AppWidgetManager
    private var interestedYoutubeWidgetProvider: String = "com.google.android.apps.youtube.app.widget.YtQuickActionsWidgetProvider"
    private lateinit var appWidgetHost: AppWidgetHost


    private val bindWidgetLauncher: ActivityResultLauncher<Input> = registerForActivityResult(BindWidgetContract()) {
        val info = appWidgetManager.getAppWidgetInfo(appWidgetId)
        proceed(info)
    }

    private val configureWidgetLauncher: ActivityResultLauncher<ConfigureWidgetContract.Input?> = registerForActivityResult(ConfigureWidgetContract()) { configured: Boolean ->
        onNewWidgetConfigured(configured)
    }

    private fun onNewWidgetConfigured(configured: Boolean) {
        if (configured) {
            val info = appWidgetManager.getAppWidgetInfo(appWidgetId)
            if (info == null) {
                cancelAddNewWidget(appWidgetId)
                return
            }
            addNewWidget(info)
        } else {
            cancelAddNewWidget(appWidgetId)
        }
    }


    private class BindWidgetContract :
        ActivityResultContract<Input, Any>() {
        override fun createIntent(
            context: Context,
            input: Input
        ): Intent {
            return Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, input.appWidgetId)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, input.info.provider)
                .putExtra(
                    AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE,
                    input.info.profile
                )
        }

        override fun parseResult(resultCode: Int, intent: Intent?): Any {
            return Any()
        }
    }


    internal class Input(val appWidgetId: Int, val info: AppWidgetProviderInfo)


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // get the instance of AppWidgetManager
        appWidgetManager = AppWidgetManager.getInstance(this)
        appWidgetHost = AppWidgetHost(this, APP_WIDGET_HOST_ID)

        // get package name of youtube
        val youtubePackageName: String = getYoutubePackageName()

        // get all widgets provided by youtube
        val installedProvidersForYoutube = appWidgetManager.getInstalledProvidersForPackage(youtubePackageName, null)

        installedProvidersForYoutube.forEach { appWidgetProviderInfo ->

            if(appWidgetProviderInfo.provider.className == interestedYoutubeWidgetProvider) {

                appWidgetId = appWidgetHost.allocateAppWidgetId()

                val result = appWidgetManager.bindAppWidgetIdIfAllowed(appWidgetId, appWidgetProviderInfo.provider)

                if(result) {
                    proceed(appWidgetProviderInfo)
                } else {
                    bindWidgetLauncher.launch(Input(appWidgetId, appWidgetProviderInfo))
                }
            }
        }
    }

    private fun proceed(appWidgetProviderInfo: AppWidgetProviderInfo?) {
        if(appWidgetProviderInfo == null) {
            cancelAddNewWidget(appWidgetId)
            return
        }

        configureWidget(appWidgetProviderInfo)
    }

    private fun configureWidget(info: AppWidgetProviderInfo) {
        if(needConfigure(info)) {
            if(isConfigureActivityExported(this, info)) {
                try {
                    configureWidgetLauncher.launch(ConfigureWidgetContract.Input(appWidgetId, info.configure))
                } catch (e: Exception) {
                    Log.e("SOHAIL", "ConfigureWidgetContract.launch: ${e.message}")
                }
            } // There is an else condition is source code
        } else {
            addNewWidget(info)
        }
    }

    private fun addNewWidget(info: AppWidgetProviderInfo) {
        addWidgetToScreen(appWidgetId, info)
    }

    private fun addWidgetToScreen(newAppWidgetId: Int, info: AppWidgetProviderInfo) {
        val options = Bundle(appWidgetManager.getAppWidgetOptions(appWidgetId))
        appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
        val constraintLayoutViewGroup = findViewById<ConstraintLayout>(R.id.constraint_layout)

        val widgetView: AppWidgetHostView = appWidgetHost.createView(this, appWidgetId, info)
        constraintLayoutViewGroup.addView(widgetView)
    }

    private fun needConfigure(info: AppWidgetProviderInfo): Boolean {

        if (info.configure == null) {
            return false
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val optional = info.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL != 0 && info.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_RECONFIGURABLE != 0
                return !optional
            }

            // configure reconfigurable widgets later
            return info.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_RECONFIGURABLE == 0
        }

        return false
    }

    private fun cancelAddNewWidget(appWidgetId: Int) {
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            return
        }
        appWidgetHost.deleteAppWidgetId(appWidgetId)
        this.appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    }

    private fun isConfigureActivityExported(context: Context, info: AppWidgetProviderInfo): Boolean {
        return try {
            val activityInfo = context.packageManager.getActivityInfo(info.configure, 0)
            activityInfo.exported
        } catch (e: PackageManager.NameNotFoundException) {
            Log.e("SOHAIL", "package not found:")
            false
        }
    }

    private fun getYoutubePackageName(): String = "com.google.android.youtube"


    private class ConfigureWidgetContract :
        ActivityResultContract<ConfigureWidgetContract.Input?, Boolean>() {
        override fun createIntent(context: Context, input: Input?): Intent {
            return Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
                .setComponent(input?.configure)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, input?.appWidgetId)
        }

        override fun parseResult(resultCode: Int, intent: Intent?): Boolean {
            return resultCode != RESULT_CANCELED
        }

        class Input(val appWidgetId: Int, val configure: ComponentName)
    }
}