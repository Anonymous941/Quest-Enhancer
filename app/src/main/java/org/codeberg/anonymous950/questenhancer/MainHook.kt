package org.codeberg.anonymous950.questenhancer

import android.content.Context
import android.util.Log
import android.view.View
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import java.util.function.Function
import kotlin.reflect.KClass
import kotlin.reflect.full.companionObjectInstance

class MainHook : IXposedHookLoadPackage {
    override fun handleLoadPackage(lpparam: LoadPackageParam) {
        if (!TARGET_PACKAGES.contains(lpparam.packageName)) {
            return
        }

        val appStateUtil =
            XposedHelpers.findClass("com.oculus.vrshell.util.AppStateUtil", lpparam.classLoader)
        val appUtils = XposedHelpers.findClass(
            "com.oculus.panelapp.library.utils.LibraryAppUtils", lpparam.classLoader
        )
        val appBuilderClass =
            XposedHelpers.findClass($$"com.oculus.library.model.App$Builder", lpparam.classLoader)

        val menuEnum = XposedHelpers.findClass(
            "com.oculus.panelapp.library.models.LibraryAppTileContextMenuItem", lpparam.classLoader
        )
        val UNINSTALL = XposedHelpers.getStaticObjectField(menuEnum, "UNINSTALL")
        val LOCK = XposedHelpers.getStaticObjectField(menuEnum, "LOCK")
        val UNLOCK = XposedHelpers.getStaticObjectField(menuEnum, "UNLOCK")
        val PIN = XposedHelpers.getStaticObjectField(menuEnum, "PIN")
        val UNPIN = XposedHelpers.getStaticObjectField(menuEnum, "UNPIN")
        val REMOVE = XposedHelpers.getStaticObjectField(menuEnum, "REMOVE")

        val deviceConfigOverride = DeviceConfigOverride()
        deviceConfigOverride.putAll(SYSTEMUX_SETTINGS_PAGES.associateWith {
            DeviceConfigValue.Boolean(
                true
            )
        })
        deviceConfigOverride.putAll(SYSTEMUX_OPTIONS.mapValues { (_, value) ->
            DeviceConfigValue(
                value
            )
        })
        deviceConfigOverride.putAll(SYSTEMUX_OPTIONS_DOUBLE.mapValues { (_, value) ->
            DeviceConfigValue(
                value
            )
        })
        deviceConfigOverride.putAll(SYSTEMUX_OPTIONS_STRING.mapValues { (_, value) ->
            DeviceConfigValue(
                value
            )
        })

        // things only "trusted users" can do
        XposedHelpers.findAndHookMethod(
            "com.oculus.panelapp.quicksettings.QuickSettingsViewModel",
            lpparam.classLoader,
            "getCanChangeDateTime",
            XC_MethodReplacement.returnConstant(true)
        )
        XposedHelpers.findAndHookMethod(
            "com.oculus.panelapp.settings.ui.AndroidSettingsViewModel",
            lpparam.classLoader,
            "isInternal",
            XC_MethodReplacement.returnConstant(true)
        )

        //                    when (key) {
//                        "oculus_systemux:oculus_sysux_app_lock_settings_section", "oculus_systemux:aui_show_unknown_sources_in_dynamic_app_bar", "oculus_systemux:oculus_gls", "oculus_systemux:oculus_avalanche", "oculus_systemux:oculus_avalanche_gaming_infra_enabled", "oculus_casting:capture_settings_section", "oculus_systemux:oculus_sysux_aui_bar_customization", "oculus_systemux:oculus_sysux_aui_bar_customization_drag", "oculus_systemux:oculus_mobile_settings_app_library_section", "oculus_systemux:oculus_ax_setting_controller_settings_calibration", "oculus_systemux:oculus_ax_setting_controller_settings_up_angle", "oculus_shared_core:is_trusted_user", "oculus_systemux:oculus_mobile_settings_use_test_environments", "oculus_systemux:oculus_mobile_swap_controller_thumbsticks", "oculus_systemux:oc_timeedit", "oculus_systemux:quest_expanded_toast_buttons", "oculus_systemux:oculus_settings_idle_shutdown_enabled", "oculus_systemux:oculus_configurable_mtp_dialog", "oculus_systemux:oculus_mobile_hand2_override", "oculus_systemux:oculus_mobile_enable_hand_emulation_of_controllers", "oculus_systemux:oculus_mobile_boost_your_height", "oculus_systemux:oculus_mobile_double_tap_hand_autotransition", "oculus_systemux:oculus_guardian_room_capture", "oculus_systemux:oculus_intrusion_detection", "oculus_systemux:assistant_exclusive_mic_hack", "oculus_systemux:xros_audio_assist_desk_mode", "oculus_systemux:assistant_oculus_doubletap_setting", "oculus_systemux:assistant_oculus_response_setting" -> {
//                            param.setResult(true)
//                        }
//                    }
        fun deviceConfigMethodHook(
            type: KClass<out DeviceConfigValue>,
            getMethod: String,
            deviceGetMethod: String,
            defaultMethod: String
        ) {
            val expectedType =
                requireNotNull((type.companionObjectInstance as HasType).type) { "cannot get null" }
            val methodHook = object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val key = param.args[0] as String?
                    if (key == null) {
                        Log.w(TAG, "null key passed to device config")
                    } else {
                        deviceConfigOverride[key]?.let { override ->
                            val value = override.getType(type)
                            if (value == null) {
                                Log.w(
                                    TAG,
                                    "invalid type for $key: expected ${expectedType.simpleName}, got ${override.type!!.simpleName}"
                                )
                            } else {
                                if (value.value == null) {
                                    param.result = XposedHelpers.callMethod(
                                        param.thisObject, defaultMethod, key
                                    )
                                } else {
                                    param.result = value.value
                                }
                            }
                        }
                    }
                }
            }
            XposedHelpers.findAndHookMethod(
                "com.oculus.deviceconfigclient.DeviceConfigClient",
                lpparam.classLoader,
                getMethod,
                String::class.java,
                methodHook
            )
            XposedHelpers.findAndHookMethod(
                "com.oculus.deviceconfigclient.DeviceConfigClient",
                lpparam.classLoader,
                deviceGetMethod,
                String::class.java,
                methodHook
            )
        }
        deviceConfigMethodHook(
            DeviceConfigValue.Boolean::class, "getBoolean", "getDeviceBoolean", "getBooleanDefault"
        )
        deviceConfigMethodHook(
            DeviceConfigValue.Double::class, "getDouble", "getDeviceDouble", "getDoubleDefault"
        )
        deviceConfigMethodHook(
            DeviceConfigValue.Long::class, "getLong", "getDeviceLong", "getLongDefault"
        )
        deviceConfigMethodHook(
            DeviceConfigValue.String::class, "getString", "getDeviceString", "getStringDefault"
        )

        // add pin/unpin to unknown sources
        XposedHelpers.findAndHookMethod(
            "com.oculus.panelapp.library.adapters.LibraryContentAdapter",
            lpparam.classLoader,
            $$"lambda$maybeBindUninstallMenu$23$LibraryContentAdapter",
            "com.oculus.tablet.library.common.models.UnknownSource",
            Int::class.javaPrimitiveType,
            View::class.java,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val dropdown = XposedHelpers.getObjectField(param.thisObject, "mDropdown")
                    XposedHelpers.setAdditionalInstanceField(
                        dropdown, "unknownSource", param.args[0]
                    )
                    XposedHelpers.setAdditionalInstanceField(
                        dropdown, "adapter", param.thisObject
                    )
                }

                override fun afterHookedMethod(param: MethodHookParam) {
                    val unknownSource = param.args[0]
                    val position = param.args[1] as Int
                    val dropdown = XposedHelpers.getObjectField(param.thisObject, "mDropdown")
                    XposedHelpers.callMethod(
                        dropdown, "setOnItemClick", object : Function<Any?, Any?> {
                            override fun apply(item: Any?): Any? {
                                // wrapper around the non-unknown sources' click handler
                                val packageName = XposedHelpers.callMethod(
                                    unknownSource, "getPackageName"
                                ) as String?
                                val displayName = XposedHelpers.callMethod(
                                    unknownSource, "getApplicationName"
                                ) as String?

                                if (item === LOCK || item === UNLOCK) {
                                    // TODO: does this actually work?
                                    val panelApp = XposedHelpers.getObjectField(
                                        param.thisObject, "mPanelApp"
                                    )
                                    val viewModel = XposedHelpers.getObjectField(
                                        param.thisObject, "mViewModel"
                                    )
                                    val appLockHelper =
                                        XposedHelpers.callMethod(viewModel, "getAppLockHelper")

                                    XposedHelpers.callStaticMethod(
                                        appUtils,
                                        "showLockUnlockReAuthDialog",
                                        panelApp,
                                        appLockHelper,
                                        displayName,
                                        packageName,
                                        "",
                                        item
                                    )
                                } else if (item === UNINSTALL) {
                                    val context = XposedHelpers.getObjectField(
                                        param.thisObject, "mContext"
                                    ) as Context?
                                    val panelApp = XposedHelpers.getObjectField(
                                        param.thisObject, "mPanelApp"
                                    )
                                    val funnelLogger = XposedHelpers.getObjectField(
                                        param.thisObject, "mLibraryFunnelLogHelper"
                                    )

                                    XposedHelpers.callStaticMethod(
                                        appUtils,
                                        "showUninstallDialog",
                                        context,
                                        panelApp,
                                        displayName,
                                        packageName,
                                        false,
                                        funnelLogger,
                                        position
                                    )
                                } else if (item === PIN || item === UNPIN) {
                                    XposedHelpers.callStaticMethod(
                                        appStateUtil,
                                        "onPinnedAppStateShouldChange",
                                        packageName,
                                        item === PIN
                                    )
                                } else if (item === REMOVE) {
                                    XposedHelpers.callStaticMethod(
                                        appStateUtil,
                                        "onRecentAppStateShouldChange",
                                        packageName,
                                        false
                                    )
                                }
                                return null
                            }
                        })

                    XposedHelpers.removeAdditionalInstanceField(dropdown, "unknownSource")
                    XposedHelpers.removeAdditionalInstanceField(dropdown, "adapter")
                }
            })
        // add more options to the context menu of unknown sources, including pin/unpin
        XposedHelpers.findAndHookMethod(
            "com.oculus.ocui.OCDropdown",
            lpparam.classLoader,
            "setItems",
            MutableList::class.java,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val unknownSource = XposedHelpers.getAdditionalInstanceField(
                        param.thisObject, "unknownSource"
                    )
                    val adapter =
                        XposedHelpers.getAdditionalInstanceField(param.thisObject, "adapter")
                    if (unknownSource != null && adapter != null) {
                        val packageName =
                            XposedHelpers.callMethod(unknownSource, "getPackageName") as String?
                        val viewModel = XposedHelpers.getObjectField(adapter, "mViewModel")

                        @Suppress("UNCHECKED_CAST") val list = param.args[0] as MutableList<Any>
                        // normally it just has uninstall, so remove it, otherwise it would be at the top
                        list.clear()

                        val appLockHelper = XposedHelpers.callMethod(viewModel, "getAppLockHelper")
                        if (XposedHelpers.callMethod(
                                appLockHelper, "isAppLockFeatureEnabled"
                            ) as Boolean
                        ) {
                            val isLocked = XposedHelpers.callMethod(
                                appLockHelper, "isAppLocked", packageName
                            ) as Boolean
                            list.add(if (isLocked) UNLOCK else LOCK)
                        }

                        // addPinOptionToMenu() expects an App, so create a dummy
                        val builder = XposedHelpers.newInstance(appBuilderClass)
                        XposedHelpers.callMethod(builder, "withPackageName", packageName)
                        XposedHelpers.callMethod(builder, "withEntitlementHash", "00000000")
                        val dummyApp = XposedHelpers.callMethod(builder, "build")

                        list.add(UNINSTALL)

                        XposedHelpers.callStaticMethod(
                            appUtils, "addPinOptionToMenu", dummyApp, viewModel, param.args[0]
                        )
                    }
                }
            })

        // always allow unknown apps to stay pinned
        XposedHelpers.findAndHookMethod(
            "com.oculus.panelapp.anytimeui.bar.apps.DynamicAppsViewModel",
            lpparam.classLoader,
            "buildBarAppItemFromPackageName",
            String::class.java,
            String::class.java,
            Boolean::class.javaPrimitiveType,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    param.args[2] = true
                }
            })

        // make them fake apps so they don't have all the context menu options that can't be chosen (such as see details and permissions)
        XposedHelpers.findAndHookMethod(
            $$"com.oculus.vrshell.util.FakeAppUtil$Companion",
            lpparam.classLoader,
            "isFakeAppInLibrary",
            String::class.java,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val packageName = param.args[0] as String?
                    when (packageName) {
                        "com.oculus.explore", "com.oculus.store" -> {
                            param.result = true
                        }
                    }
                }
            })

        // TODO: why is this even necessary?
        // for some reason, I need this patch for explore and store to even have a context menu
        // but this should happen anyway with the other code
        XposedHelpers.findAndHookMethod(
            "com.oculus.panelapp.library.utils.LibraryAppUtils",
            lpparam.classLoader,
            "hasContextMenu",
            "com.oculus.library.model.App",
            "com.oculus.panelapp.library.LibraryViewModel",
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val app = param.args[0]
                    if (app != null) {
                        val packageName =
                            XposedHelpers.getObjectField(app, "packageName") as String?
                        if ("com.oculus.explore" == packageName || "com.oculus.store" == packageName) {
                            param.result = true
                        }
                    }
                }
            })

        // remove all pinned apps
        XposedHelpers.findAndHookMethod(
            "com.oculus.panelapp.anytimeui.bar.apps.DynamicAppsViewModel",
            lpparam.classLoader,
            "getPermanentPinnedApps",
            XC_MethodReplacement.returnConstant(LinkedHashMap<Any?, Any?>())
        )
        XposedHelpers.findAndHookMethod(
            "com.oculus.vrshell.util.AppStateUtil",
            lpparam.classLoader,
            "getPermanentUnremovableApps",
            XC_MethodReplacement.returnConstant(HashSet<String?>())
        )

        // XXX: this is a test to allow overriding them by manually editing the XML, remove this or let the user configure it!
        XposedHelpers.findAndHookMethod(
            "com.oculus.deviceconfigclient.DeviceConfigClientUtil",
            lpparam.classLoader,
            "shouldUpdateDeviceConfigCache",
            "com.facebook.mobileconfig.factory.MobileConfigValueSource",
            XC_MethodReplacement.returnConstant(false)
        )

        // remove the confirmation dialog when disabling the guardian because it's unnecessary
        XposedHelpers.findAndHookMethod(
            "com.oculus.panelapp.settings.sections.SettingsSystemDeveloperSection",
            lpparam.classLoader,
            "confirmGuardianChange",
            "com.oculus.panelapp.settings.SettingsPanelApp",
            Boolean::class.javaPrimitiveType,
            "com.oculus.panelapp.settings.ui.SettingsToggleActionType",
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val settingsManager =
                        XposedHelpers.getObjectField(param.thisObject, "settingsManager")
                    val previousValue = XposedHelpers.callMethod(
                        settingsManager, "getBoolean", "guardian_paused", false
                    ) as Boolean
                    XposedHelpers.callMethod(
                        settingsManager, "setBoolean", "guardian_paused", !previousValue
                    )
                    param.result = null
                }
            })

        val GUARDIAN_CONFIG_SHOW_DEBUG_UI_TOGGLE = 0x1d

        // enable secret guardian settings
        XposedHelpers.findAndHookMethod(
            "com.oculus.common.guardian.GuardianModule",
            lpparam.classLoader,
            "getGuardianConfigValue",
            Int::class.javaPrimitiveType,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val key = param.args[0] as Int
                    if (key == GUARDIAN_CONFIG_SHOW_DEBUG_UI_TOGGLE) {
                        param.result = 1.0f
                    }
                }
            })

        // hide the unknown sources warning
        val itemTypeClass = XposedHelpers.findClass(
            $$"com.oculus.panelapp.library.models.LibraryItem$ItemType", lpparam.classLoader
        )
        val headerUnknownSourcesEnum =
            XposedHelpers.getStaticObjectField(itemTypeClass, "HEADER_UNKNOWN_SOURCES")
        XposedHelpers.findAndHookMethod(
            "com.oculus.panelapp.library.LibraryViewModel",
            lpparam.classLoader,
            "addUnknownSources",
            MutableList::class.java,
            object : XC_MethodHook() {
                @Throws(Throwable::class)
                override fun afterHookedMethod(param: MethodHookParam) {
                    val resultList = param.result as MutableList<*>

                    val iterator: MutableIterator<*> = resultList.iterator()
                    while (iterator.hasNext()) {
                        val item = iterator.next()
                        if (item != null) {
                            val itemType = XposedHelpers.callMethod(item, "getItemType")
                            if (itemType == headerUnknownSourcesEnum) {
                                iterator.remove()
                            }
                        }
                    }
                }
            })

        // enable all shell features
        XposedHelpers.findAndHookMethod(
            "com.oculus.vrshell.panels.ShellFeatureSets",
            lpparam.classLoader,
            "hasFeature",
            String::class.java,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    param.result = true
                }
            })
    }

    companion object {
        private val TAG = "MainHook"

        private val TARGET_PACKAGES = setOf<String?>("com.oculus.systemux")

        // settings options to enable, including secret settings
        // you can always turn them off so just enable everything
        private val SYSTEMUX_SETTINGS_PAGES = setOf(
            "oculus_systemux:oculus_developer", // developer mode, important
            "oculus_systemux:oculus_mobile_settings_app_library_section",
            "oculus_systemux:oculus_settings_connected_services_section",
            "oculus_systemux:oculus_settings_discord_connection_section_item",
            "oculus_systemux:oculus_mobile_settings_use_test_environments",
            "oculus_systemux:oculus_settings_security_browser_password_manager_enabled",
            "oculus_systemux:oc_assistant_weather_enable_wea_1zg7",
            "oculus_systemux:oc_arcata_privacy_settings",
            "oculus_systemux:oc_timeedit",
            "vr_youth_platform:break_time_feature_enabled",
            "oculus_systemux:oculus_mobile_boost_your_height",
            "oculus_systemux:oculus_mobile_swap_controller_thumbsticks",
            "oculus_systemux:oculus_mobile_double_tap_hand_autotransition",
            "oculus_systemux:oculus_ax_setting_controller_settings_calibration",
            "oculus_systemux:oculus_ax_setting_controller_settings_up_angle",
            "oculus_systemux:vrs_rewind_enabled",
            "oculus_systemux:oculus_ax_setting_audio_balance",
            "oculus_systemux:oc_accessibility_closed_captions",
            "oculus_systemux:oculus_ax_setting_display_contrast",
            "oculus_systemux:oculus_ax_setting_mono_audio",
            "oculus_systemux:oculus_ax_text_to_speech",
            "oculus_systemux:oc_assistant_accessibil_voice_cont_0oaz",
            "oculus_systemux:oculus_ax_voice_controller_click_on_dwell",
            "oculus_systemux:accessibility_lying_down_mode",
            "oculus_systemux:oculus_sysux_app_lock_settings_section",
            "oculus_systemux:oculus_ocui_display_theme_setting_visible",
            "oculus_systemux:oc_assistant_keyboard_fl",
            "oculus_systemux:oc_assistant_keyboard_dictation_fl",
            "oculus_systemux:oc_assistant_keyboard_fl_new_setting_enabled",
            "oculus_systemux:oc_assistant_dictation_fl_new_setting_enabled",
            "oculus_shared_systemshell:oculus_vrshell_keyboard_qe_autocorrect",
            "oculus_systemux:assistant_exclusive_mic_hack",
            "oculus_systemux:xros_audio_assist_desk_mode",
            "oculus_systemux:assistant_oculus_doubletap_setting",
            "oculus_systemux:in_app_assistant_on_oculus",
            "oculus_systemux:assistant_oculus_response_setting",
            "oculus_systemux:oc_assistant_tts_selection",
            "oculus_systemux:assistant_view_commands_on_oculus",
            "oculus_systemux:oc_assistant_voice_transcripts_storage",
            "oculus_systemux:oc_assistant_wakeword_enabled",
            "oculus_casting:capture_settings_section",
            "oculus_systemux:oculus_configurable_mtp_dialog",
            "oculus_systemux:oculus_mobile_guardian_surface_creation_gk",
            "oculus_systemux:oculus_sysux_direct_touch_setting",
            "oculus_systemux:oculus_mobile_guardian_mr_desk_gk",
            "oculus_systemux:xros_audio_enable_background_audio_playback",
            "oculus_systemux:oculus_mobile_hand2_override",
            "oculus_systemux:oculus_mobile_enable_hand_emulation_of_controllers",
            "oculus_systemux:oculus_settings_idle_shutdown_enabled",
            "oculus_systemux:oculus_intrusion_detection",
            "oculus_casting:meta_quest_camera_panel_enabled",
            "oculus_casting:camera_panel_take_photo_icon_enabled",
            "oculus_systemux:oculus_controllers_key_mapping",
            "oculus_systemux:oculus_vrshell_remote_tracked_keyboard_setting",
            "oculus_systemux:oculus_guardian_room_capture",
            "oculus_systemux:oculus_settings_help_center",
            "oculus_systemux:oculus_quick_settings_oculus_move_toggle",
            "oculus_notifications:vr_auto_dnd",
            "oculus_notifications:vr_pwa_notif_settings",
            "oculus_casting:livestream_setting_dialog_enabled",
            "oculus_systemux:oculus_customizable_environments_gating",
            "oculus_systemshell:parsu_enable_horizon_vr_settings",
            "oculus_systemux:oculus_vrshell_direct_interaction_enabled",
            "oculus_systemux:oculus_quick_settings_display_mode_switch_button",
            "oculus_systemux:oculus_vrshell_physical_keyboard_layout_settings",
            "oculus_systemux_phone_notifications:feed_tab_enabled",
            "oculus_systemux:oculus_quest_profile_sharing",
            "oculus_guardian:oculus_guardian_reality_tuner_slider"
        )
        private val SYSTEMUX_OPTIONS = mapOf(
            "oculus_systemux:assistant_on_oculus" to true, // TODO: MAKE THIS CONFIGURABLE IN CASE PEOPLE DON'T WANT THE ASSISTANT
            "oculus_systemux:oculus_sysux_aui_bar_apps_history" to true, // TODO: maybe make this configurable in case they don't like history
            "oculus_systemux:oculus_sysux_back_button" to true, // TODO: make this configurable, enables back button for non-VR apps
            "oculus_systemux:oculus_gls" to true, // enable AR simulation mode
            "oculus_systemux:oculus_systemux_destination_ui_persistent_invite_button" to true,
            "oculus_systemux:oculus_sysux_aui_bar_3d_apps" to true, // allows 3D apps to appear on recents, not sure why this is configurable
            "oculus_systemux:oculus_sysux_aui_bar_customization_drag" to true, // allows customizing drag-and-drop
            "oculus_identity_local_account_mode:local_account_display_local_apps" to true, // shows all apps in local account mode
            "oculus_systemux:oculus_mobile_aui_v2_qp_killswitch" to true, // AD BLOCKER!  YAY!
            "oculus_systemux:oculus_social_party_fb_upsell" to false, // blocks some of it begging you to get a Facebook account
            "oculus_shared_social:simile_social_blocking_upsell" to false, // also blocks some of it begging you to get a Facebook account
            "oculus_systemux:aui_show_unknown_sources_in_dynamic_app_bar" to true, // allows unknown sources to be pinned, but doesn't actually work and gets cleared on boot, along with not showing up in the context menu.  this module fixes it and adds them to the context menu.
            "oculus_systemux:oculus_sysux_aui_bar_history_remove_from_recent" to true, // allows removing from recent
            "oculus_systemux:settings_security_section_twilight_reauth_enabled" to false, // prevents the companion app from requiring reauthentication
            "arvr_social_identity:vr_allow_follow_requests_to_follow_back" to true, // allows you to follow back someone who followed you
            "arvr_social_identity:vr_profile_follow_request_entry_point_enabled" to true, // allows you to view follow requests in the first place
            "arvr_social_identity:vr_follower_perform_optimistic_updates" to true, // causes following to immediately update instead of waiting for the server, optional
            "arvr_social_identity:vr_profile_mutual_context_follow_request" to true, // shows mutual connections in other people's profiles
            "oculus_shared_social:vr_travel_invites_v2_enabled" to true, // allows others to be invited inside the in-game pause menu, optional
            "oculus_shared_social:messaging_notif_badge_data_source" to true, // TODO: figure this out
            "oculus_shared_social:numeric_badge_for_unread_messages" to true, // Messenger shows a numeric count for unread messages instead of a dot, optional
            "oculus_systemux:oculus_sysux_active_app_indicator_improvements" to true, // improves the app indicator, no reason to disable
            "oculus_systemux:quest_expanded_toast_buttons" to true, // unused
            "oculus_systemux:oc_assistant_enable_ww_gen_two" to true, // TODO: investigate this more, it seems to prevent rebooting when switching wake words among other things
            "oculus_systemux:oculus_enable_ari" to true, // enables VR Air Bridge support
            "oculus_mobile_core_2022:enable_resumable_upload" to true,
            "oculus_mobile_core_2022:enable_camera_panel_status_update" to true,
            "oculus_shared_systemshell:oculus_vrshell_enable_test_keyboards" to true, // allows keyboards from apps marked as test to be selected
            "oculus_systemux:oculus_fitness_tracking_dogfooding" to true, // TODO: figure this out
            "healthtech_oculus:health_payer_project_enabled" to true, // TODO: figure this out
            "oculus_casting:vr_camera_ignore_allowlist" to true, // TODO: figure this out
            "oculus_notifications:show_vr_notif_inspector" to true, // TODO: make this configurable, it's a debug feature
            // TODO: figure these out
            "oculus_shared_reactvr_apps:oculus_pc_allow_mirroring_application_passthrough" to true,
            "oculus_shared_reactvr_apps:oculus_video_allow_screen_capture" to true,
            "oculus_shared_reactvr_apps:oculus_pc_link_allow_unknown_sources" to true,
            "oculus_systemux:oc_is_social_notif_inline_cta_enabled" to true, // allows doing certain actions directly from the notification
            "oculus_systemux:oculus_ocms_enable_force_uninstall" to true, // if an app gets stuck while installing, this lets you abort it
            "oculus_systemux:oculus_sysux_library_prioritize_downloads" to true, // allows manually prioritizing downloads
            "oculus_shared_reactvr_apps:oculus_living_room_enable_copresence" to true,
            "oculus_casting:meta_quest_camera_panel_enabled" to true,
            "oculus_casting:camera_panel_take_photo_icon_enabled" to true,
            "oculus_shared_social:oculus_parties_enable_aui_mic_switcher_controls" to true,
            "oculus_shared_social:oculus_parties_enable_auto_mic_mixing" to false,
            "oculus_systemux:oculus_systemux_library_show_unknown_sources_for_copresence" to true, // allows you to choose unknown sources for coprescense
            "oculus_shared_core:is_trusted_user" to true, // WARNING!  SENDS IS EMPLOYEE ON TELEMETRY!  also make configurable whether the internal thing shows up on the dock
            "oculus_systemux:oculus_party_per_person_mute" to true,
            "oculus_systemux:oculus_sysux_phone_notifs_iconography" to true, // shows custom icons for phone notifications
            "arvr_social_identity:simile_follow_request_banner_enabled" to true,
            "oculus_shared_social:simile_social_blocking_upsell" to false
            // misc
//            "oculus_social_platform_deviceconfig:oc_ask_to_join" to true,
//            "oculus_social_platform_deviceconfig:messenger_vr_disable_static_reply" to false,
//            "oculus_social_platform_deviceconfig:messenger_in_vr_audio_messages" to true,
//            "oculus_social_platform_deviceconfig:oculus_vr_messenger_calling_ignore_signature" to true,
//            "oculus_social_platform_deviceconfig:oculus_vr_messenger_calling_turn_on_ux_logging" to false,
//            "oculus_social_platform_deviceconfig:oc_assistant_enable_static_replies_on_message_older_than_1_day" to true,
//            "oculus_social_platform_deviceconfig:oc_assistant_enable_static_replies_on_new_thread_only" to false,
//            "oculus_social_platform_deviceconfig:messenger_vr_reply_features" to true,
//            "oculus_social_platform_deviceconfig:messenger_in_vr_send_reactions" to true,
//            "oculus_social_platform_deviceconfig:messenger_vr_should_show_chat_settings" to true,
//            "oculus_social_platform_deviceconfig:oculus_vr_messenger_calling_show_home_destination" to true,
//            "oculus_social_platform_deviceconfig:messenger_vr_start_party_entrypoint" to true,
//            "oculus_social_platform_deviceconfig:vr_messenger_unsend_message" to true,
//            "oculus_social_platform_deviceconfig:messenger_vr_reply_message" to true,
//            "oculus_social_platform_deviceconfig:mivr_app_split" to true,
//            "oculus_social_platform_deviceconfig:mivr_image_send" to true,
//            "oculus_social_platform_deviceconfig:mivr_oc_communicator" to true,
//            "oculus_social_platform_deviceconfig:mivr_oc_communicator_features" to true,
//            "oculus_social_platform_deviceconfig:mivr_oc_communicator_read" to true,
//            "oculus_social_platform_deviceconfig:mivr_oc_communicator_write" to true,
//            "oculus_social_platform_deviceconfig:mivr_oc_communicator_message_requests" to true,
//            "oculus_social_platform_deviceconfig:mivr_oc_communicator_thread_type_debug_only" to false,
//            "oculus_social_platform_deviceconfig:mivr_oc_communicator_image_send" to true,
//            "oculus_social_platform_deviceconfig:enable_mivr_oc_communicator_call" to true,
//            "oculus_social_platform_deviceconfig:mivr_tti_measurement" to false,
//            "oculus_social_platform_deviceconfig:oc_chat_add_friend_button" to true,
//            "oculus_social_platform_deviceconfig:oc_chat_navigate_to_profile_from_chat_icon" to true,
//            "oculus_shared_social:system_copresence_version_gates" to true,
//            "oculus_shared_reactvr_apps:oculus_living_room_enable_copresence" to true,
//            "oculus_social_platform_deviceconfig:oculus_parties_show_edit_avatar_shortcut_in_participant_list" to true,
//            "oculus_shared_social:oculus_parties_enable_aui_mic_switcher_controls" to true,
//            "oculus_social_platform_deviceconfig:oculus_parties_invite_from_fb_entrypoint" to true,
//            "oculus_social_platform_deviceconfig:oculus_party_debug_menu" to true,
//            "oculus_social_platform_deviceconfig:enabled_1to1_chat_entrypoint_button_for_invitees_in_party_view" to true,
//            "oculus_social_platform_deviceconfig:oculus_party_enable_profile_and_friend_actions" to true,
//            "oculus_social_platform_deviceconfig:rp_vr_is_call_transfer_enabled" to true,
//            "oculus_social_platform_deviceconfig:roster_invite_dialog_add_friend_button_state" to true
        )
        private val SYSTEMUX_OPTIONS_STRING = mapOf(
            // requires opt in before auto syncing local headset recordings and screenshots!
            // valid options are "opt-in", "opt-out" and "none" (which hides the setting and force enables it)
            "oculus_mobile_core_2022:vr_auto_sync_nux_option" to "opt-in"
        )
        private val SYSTEMUX_OPTIONS_DOUBLE = mapOf(
            "oculus_shared_systemshell:oculus_copresence_environment_loading_fake_progress_millis" to 0.0 // for some reason, they want there to be a fake loading bar when a copresence environment that's already installed is loaded, why?!
        )
    }
}