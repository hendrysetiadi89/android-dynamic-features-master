/*
 * Copyright 2018 Google LLC.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.samples.dynamicfeatures

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.constraintlayout.widget.Group
import com.google.android.play.core.splitcompat.SplitCompat
import com.google.android.play.core.splitinstall.SplitInstallManager
import com.google.android.play.core.splitinstall.SplitInstallManagerFactory
import com.google.android.play.core.splitinstall.SplitInstallRequest
import com.google.android.play.core.splitinstall.SplitInstallSessionState
import com.google.android.play.core.splitinstall.SplitInstallStateUpdatedListener
import com.google.android.play.core.splitinstall.model.SplitInstallSessionStatus
import java.util.Locale
import android.content.pm.ResolveInfo
import android.content.pm.PackageManager



private const val PACKAGE_NAME = "com.google.android.samples.dynamicfeatures.ondemand"

private const val INSTANT_PACKAGE_NAME = "com.google.android.samples.instantdynamicfeatures"

private const val KOTLIN_SAMPLE_CLASSNAME = "$PACKAGE_NAME.KotlinSampleActivity"
private const val TEST_SAMPLE_CLASSNAME = "com.tokopedia.mylibrary.TestActivity"

private const val JAVA_SAMPLE_CLASSNAME = "$PACKAGE_NAME.JavaSampleActivity"

private const val NATIVE_SAMPLE_CLASSNAME = "$PACKAGE_NAME.NativeSampleActivity"

private const val INSTANT_SAMPLE_CLASSNAME = "$INSTANT_PACKAGE_NAME.SplitInstallInstantActivity"

private const val CONFIRMATION_REQUEST_CODE = 1

private const val TAG = "DynamicFeatures"

class MainActivity : BaseSplitActivity() {

    fun buildInternalUri(context: Context, deeplink: String): Intent {
        val uri = Uri.parse(deeplink)
        val intent = Intent(Intent.ACTION_VIEW)
        intent.data = uri
        intent.setPackage(context.packageName)
        intent.addCategory(Intent.CATEGORY_DEFAULT)
        intent.addCategory(Intent.CATEGORY_BROWSABLE)
        return intent
    }

    /** Listener used to handle changes in state for install requests. */
    private val listener = SplitInstallStateUpdatedListener { state ->
        val multiInstall = state.moduleNames().size > 1
        val langsInstall = !state.languages().isEmpty()

        val names = if (langsInstall) {
            // We always request the installation of a single language in this sample
            state.languages().first()
        }
        else state.moduleNames().joinToString(" - ")

        when (state.status()) {
            SplitInstallSessionStatus.DOWNLOADING -> {
                //  In order to see this, the application has to be uploaded to the Play Store.
                displayLoadingState(state, getString(R.string.downloading, names))
            }
            SplitInstallSessionStatus.REQUIRES_USER_CONFIRMATION -> {
                /*
                  This may occur when attempting to download a sufficiently large module.

                  In order to see this, the application has to be uploaded to the Play Store.
                  Then features can be requested until the confirmation path is triggered.
                 */
                manager.startConfirmationDialogForResult(state, this, CONFIRMATION_REQUEST_CODE)
            }
            SplitInstallSessionStatus.INSTALLED -> {
                if (langsInstall){
                    onSuccessfulLanguageLoad(names)
                } else {
                    onSuccessfulLoad(names, launch = !multiInstall)
                }
            }

            SplitInstallSessionStatus.INSTALLING -> displayLoadingState(
                    state,
                    getString(R.string.installing, names)
            )
            SplitInstallSessionStatus.FAILED -> {
                toastAndLog(getString(R.string.error_for_module, state.errorCode(),
                        state.moduleNames()))
            }
        }
    }

    /** This is needed to handle the result of the manager.startConfirmationDialogForResult
    request that can be made from SplitInstallSessionStatus.REQUIRES_USER_CONFIRMATION
    in the listener above. */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == CONFIRMATION_REQUEST_CODE) {
            // Handle the user's decision. For example, if the user selects "Cancel",
            // you may want to disable certain functionality that depends on the module.
            if (resultCode == Activity.RESULT_CANCELED) {
                toastAndLog(getString(R.string.user_cancelled))
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    private val moduleKotlin by lazy { getString(R.string.module_feature_kotlin) }
    private val moduleJava by lazy { getString(R.string.module_feature_java) }
    private val moduleNative by lazy { getString(R.string.module_native) }
    private val moduleAssets by lazy { getString(R.string.module_assets) }
    private val instantModule by lazy { getString(R.string.module_instant_feature_split_install) }
    private val instantModuleUrl by lazy { getString(R.string.instant_feature_url) }

    private val clickListener by lazy {
        View.OnClickListener {
            when (it.id) {
                R.id.btn_load_kotlin -> loadAndLaunchModule(moduleKotlin)
                R.id.btn_load_java -> loadAndLaunchModule(moduleJava)
                R.id.btn_load_assets -> loadAndLaunchModule(moduleAssets)
                R.id.btn_load_native -> loadAndLaunchModule(moduleNative)
                R.id.btn_install_all_now -> installAllFeaturesNow()
                R.id.btn_install_all_deferred -> installAllFeaturesDeferred()
                R.id.btn_request_uninstall -> requestUninstall()
                R.id.btn_instant_dynamic_feature_split_install -> loadAndLaunchModule(instantModule)
                R.id.btn_instant_dynamic_feature_url_load -> openUrl(instantModuleUrl)
                R.id.lang_en -> loadAndSwitchLanguage(LANG_EN)
                R.id.lang_pl -> loadAndSwitchLanguage(LANG_PL)
            }
        }
    }

    private lateinit var manager: SplitInstallManager

    private lateinit var progress: Group
    private lateinit var buttons: Group
    private lateinit var progressBar: ProgressBar
    private lateinit var progressText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTitle(R.string.app_name)
        setContentView(R.layout.activity_main)
        manager = SplitInstallManagerFactory.create(this)
        initializeViews()
    }

    override fun onResume() {
        // Listener can be registered even without directly triggering a download.
        manager.registerListener(listener)
        super.onResume()
    }

    override fun onPause() {
        // Make sure to dispose of the listener once it's no longer needed.
        manager.unregisterListener(listener)
        super.onPause()
    }

    /**
     * Load a feature by module name.
     * @param name The name of the feature module to load.
     */
    private fun loadAndLaunchModule(name: String) {
        updateProgressMessage(getString(R.string.loading_module, name))
        // Skip loading if the module already is installed. Perform success action directly.
        if (manager.installedModules.contains(name)) {
            updateProgressMessage(getString(R.string.already_installed))
            onSuccessfulLoad(name, launch = true)
            return
        }

        // Create request to install a feature module by name.
        val request = SplitInstallRequest.newBuilder()
                .addModule(name)
                .build()

        // Load and install the requested feature module.
        manager.startInstall(request)

        updateProgressMessage(getString(R.string.starting_install_for, name))
    }

    /**
     * Load language splits by language name.
     * @param lang The language code to load (without the region part, e.g. "en", "fr" or "pl").
     */
    private fun loadAndSwitchLanguage(lang: String) {
        updateProgressMessage(getString(R.string.loading_language, lang))
        // Skip loading if the language is already installed. Perform success action directly.
        if (manager.installedLanguages.contains(lang)) {
            updateProgressMessage(getString(R.string.already_installed))
            onSuccessfulLanguageLoad(lang)
            return
        }

        // Create request to install a language by name.
        val request = SplitInstallRequest.newBuilder()
                .addLanguage(Locale.forLanguageTag(lang))
                .build()

        // Load and install the requested language.
        manager.startInstall(request)

        updateProgressMessage(getString(R.string.starting_install_for, lang))
    }

    private fun openUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        intent.setPackage(BuildConfig.APPLICATION_ID)
        intent.addCategory(Intent.CATEGORY_BROWSABLE)
        startActivity(intent)
    }

    /** Display assets loaded from the assets feature module. */
    private fun displayAssets() {
        // Get the asset manager with a refreshed context, to access content of newly installed apk.
        val assetManager = createPackageContext(packageName, 0).also {
            SplitCompat.install(it)
        }.assets
        // Now treat it like any other asset file.
        val assetsStream = assetManager.open("assets.txt")
        val assetContent = assetsStream.bufferedReader()
                .use {
                    it.readText()
                }

        AlertDialog.Builder(this)
                .setTitle(getString(R.string.asset_content))
                .setMessage(assetContent)
                .show()
    }

    /** Install all features but do not launch any of them. */
    private fun installAllFeaturesNow() {
        // Request all known modules to be downloaded in a single session.
        val moduleNames = listOf(moduleKotlin, moduleJava, moduleNative, moduleAssets)
        val requestBuilder = SplitInstallRequest.newBuilder()

        moduleNames.forEach { name ->
            if (!manager.installedModules.contains(name)) {
                requestBuilder.addModule(name)
            }
        }

        val request = requestBuilder.build()

        manager.startInstall(request).addOnSuccessListener {
            toastAndLog("Loading ${request.moduleNames}")
        }.addOnFailureListener {
            toastAndLog("Failed loading ${request.moduleNames}")
        }
    }

    /** Install all features deferred. */
    private fun installAllFeaturesDeferred() {
        val modules = listOf(moduleKotlin, moduleJava, moduleAssets, moduleNative)

        manager.deferredInstall(modules).addOnSuccessListener {
            toastAndLog("Deferred installation of $modules")
        }
    }

    /** Request uninstall of all features. */
    private fun requestUninstall() {

        toastAndLog("Requesting uninstall of all modules." +
                "This will happen at some point in the future.")

        val installedModules = manager.installedModules.toList()
        manager.deferredUninstall(installedModules).addOnSuccessListener {
            toastAndLog("Uninstalling $installedModules")
        }.addOnFailureListener {
            toastAndLog("Failed installation of $installedModules")
        }
    }

    /**
     * Define what to do once a feature module is loaded successfully.
     * @param moduleName The name of the successfully loaded module.
     * @param launch `true` if the feature module should be launched, else `false`.
     */
    private fun onSuccessfulLoad(moduleName: String, launch: Boolean) {
        if (launch) {
            when (moduleName) {
                // moduleKotlin -> launchActivity(KOTLIN_SAMPLE_CLASSNAME)
                moduleKotlin ->
                {
                    val intent = buildInternalUri(this, "tokopedia://marketplace/test")
                    val infos = getPackageManager().queryIntentActivities(intent, 0)
                    if (infos.size > 0) {
                        startActivity(Intent().apply {
                            setClassName(infos[0].activityInfo.packageName, infos[0].activityInfo.name);
                        })
                    } else {
                        //No Application can handle your intent
                    }
                }
                moduleJava -> launchActivity(JAVA_SAMPLE_CLASSNAME)
                moduleNative -> launchActivity(NATIVE_SAMPLE_CLASSNAME)
                moduleAssets -> displayAssets()
                instantModule -> launchActivity(INSTANT_SAMPLE_CLASSNAME)
            }
        }

        displayButtons()
    }

    private fun onSuccessfulLanguageLoad(lang: String) {
        LanguageHelper.language = lang
        recreate()
    }

    /** Launch an activity by its class name. */
    private fun launchActivity(className: String) {
        Intent().setClassName(BuildConfig.APPLICATION_ID, className)
                .also {
                    startActivity(it)
                }
    }

    /** Display a loading state to the user. */
    private fun displayLoadingState(state: SplitInstallSessionState, message: String) {
        displayProgress()

        progressBar.max = state.totalBytesToDownload().toInt()
        progressBar.progress = state.bytesDownloaded().toInt()

        updateProgressMessage(message)
    }

    /** Set up all view variables. */
    private fun initializeViews() {
        buttons = findViewById(R.id.buttons)
        progress = findViewById(R.id.progress)
        progressBar = findViewById(R.id.progress_bar)
        progressText = findViewById(R.id.progress_text)
        setupClickListener()
    }

    /** Set all click listeners required for the buttons on the UI. */
    private fun setupClickListener() {
        setClickListener(R.id.btn_load_kotlin, clickListener)
        setClickListener(R.id.btn_load_java, clickListener)
        setClickListener(R.id.btn_load_assets, clickListener)
        setClickListener(R.id.btn_load_native, clickListener)
        setClickListener(R.id.btn_install_all_now, clickListener)
        setClickListener(R.id.btn_install_all_deferred, clickListener)
        setClickListener(R.id.btn_request_uninstall, clickListener)
        setClickListener(R.id.btn_instant_dynamic_feature_split_install, clickListener)
        setClickListener(R.id.btn_instant_dynamic_feature_url_load, clickListener)
        setClickListener(R.id.lang_en, clickListener)
        setClickListener(R.id.lang_pl, clickListener)
    }

    private fun setClickListener(id: Int, listener: View.OnClickListener) {
        findViewById<View>(id).setOnClickListener(listener)
    }

    private fun updateProgressMessage(message: String) {
        if (progress.visibility != View.VISIBLE) displayProgress()
        progressText.text = message
    }

    /** Display progress bar and text. */
    private fun displayProgress() {
        progress.visibility = View.VISIBLE
        buttons.visibility = View.GONE
    }

    /** Display buttons to accept user input. */
    private fun displayButtons() {
        progress.visibility = View.GONE
        buttons.visibility = View.VISIBLE
    }

}

fun MainActivity.toastAndLog(text: String) {
    Toast.makeText(this, text, Toast.LENGTH_LONG).show()
    Log.d(TAG, text)
}
