package li.lingfeng.ltweaks.fragments;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.SwitchPreference;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import li.lingfeng.ltweaks.R;
import li.lingfeng.ltweaks.activities.ImageSearchActivity;
import li.lingfeng.ltweaks.activities.ListCheckActivity;
import li.lingfeng.ltweaks.activities.QrCodeActivity;
import li.lingfeng.ltweaks.activities.SelectableTextActivity;
import li.lingfeng.ltweaks.activities.SolidExplorerUrlReplacerSettings;
import li.lingfeng.ltweaks.activities.TrustAgentWifiSettings;
import li.lingfeng.ltweaks.fragments.sub.system.PreventListDataProvider;
import li.lingfeng.ltweaks.fragments.sub.system.TextActionDataProvider;
import li.lingfeng.ltweaks.lib.PreferenceChange;
import li.lingfeng.ltweaks.lib.PreferenceClick;
import li.lingfeng.ltweaks.lib.PreferenceLongClick;
import li.lingfeng.ltweaks.prefs.ActivityRequestCode;
import li.lingfeng.ltweaks.prefs.IntentActions;
import li.lingfeng.ltweaks.prefs.PackageNames;
import li.lingfeng.ltweaks.prefs.Prefs;
import li.lingfeng.ltweaks.utils.ComponentUtils;
import li.lingfeng.ltweaks.utils.ContextUtils;
import li.lingfeng.ltweaks.utils.Logger;
import li.lingfeng.ltweaks.utils.PackageUtils;
import li.lingfeng.ltweaks.utils.PermissionUtils;

/**
 * Created by smallville on 2017/1/4.
 */

public class SystemPrefFragment extends BasePrefFragment {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.pref_system);

        uncheckPreferenceByDisabledComponent(R.string.key_text_selectable_text, SelectableTextActivity.class);
        uncheckPreferenceByDisabledComponent(R.string.key_system_share_qrcode_scan, QrCodeActivity.class);
        uncheckPreferenceByDisabledComponent(R.string.key_system_share_image_search, ImageSearchActivity.class);
    }

    @PreferenceChange(prefs = R.string.key_text_aide_open_youdao)
    private void uninstallOldApp(Preference preference, boolean enabled) {
        if (enabled) {
            PackageUtils.tryUninstallPackage("li.lingfeng.textaide.youdao", "Text Aide with Youdao", getActivity());
        }
    }

    @PreferenceChange(prefs = R.string.key_text_selectable_text)
    private void enableSelectableText(Preference preference, boolean enabled) {
        ComponentUtils.enableComponent(SelectableTextActivity.class, enabled);
    }

    @PreferenceClick(prefs = R.string.key_text_actions)
    private void manageTextActions(Preference preference) {
        ListCheckActivity.create(getActivity(), TextActionDataProvider.class);
    }

    @PreferenceClick(prefs = R.string.key_youdao_quick_query_shortcut)
    private void youdaoQuckQueryShortcut(Preference preference) {
        Context context = ContextUtils.createPackageContext(PackageNames.YOUDAO_DICT);
        Intent intent = new Intent("com.android.launcher.action.INSTALL_SHORTCUT");
        intent.putExtra("duplicate", false);
        intent.putExtra(Intent.EXTRA_SHORTCUT_NAME, ContextUtils.getString("app_name", context));
        intent.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, Intent.ShortcutIconResource.fromContext(context,
                ContextUtils.getDrawableId("logo_dict_large", context)));

        Intent pending = new Intent();
        pending.setClassName(PackageNames.YOUDAO_DICT, "com.youdao.dict.activity.QuickDictQueryActivity");
        pending.putExtra("isEditable", true);
        pending.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        intent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, pending);
        getActivity().sendBroadcast(intent);

        Toast.makeText(getActivity(), R.string.app_shortcut_is_created, Toast.LENGTH_SHORT).show();
    }

    @PreferenceChange(prefs = R.string.key_system_share_qrcode_scan)
    private void systemShareQrcodeScan(final SwitchPreference preference, boolean enabled) {
        if (enabled) {
            PermissionUtils.requestPermissions(getActivity(), new PermissionUtils.ResultCallback() {
                @Override
                public void onResult(boolean ok) {
                    if (ok) {
                        ComponentUtils.enableComponent(QrCodeActivity.class, true);
                    } else {
                        preference.setChecked(false);
                    }
                }
            }, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        } else {
            ComponentUtils.enableComponent(QrCodeActivity.class, false);
        }
    }

    @PreferenceChange(prefs = R.string.key_system_share_image_search)
    private void systemShareImageSearch(final SwitchPreference preference, boolean enabled) {
        if (enabled) {
            PermissionUtils.requestPermissions(getActivity(), new PermissionUtils.ResultCallback() {
                @Override
                public void onResult(boolean ok) {
                    if (ok) {
                        ComponentUtils.enableComponent(ImageSearchActivity.class, true);
                    } else {
                        preference.setChecked(false);
                    }
                }
            }, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        } else {
            ComponentUtils.enableComponent(ImageSearchActivity.class, false);
        }
    }

    @PreferenceClick(prefs = R.string.key_system_share_filter)
    private void systemShareFilter(Preference preference) {
        ListCheckActivity.create(getActivity(), ShareFilterDataProvider.class);
    }

    public static class ShareFilterDataProvider extends ListCheckActivity.DataProvider {

        public class ActivityInfo {
            public ResolveInfo mInfo;
            public boolean mDisabled;

            public ActivityInfo(ResolveInfo info, boolean disabled) {
                mInfo = info;
                mDisabled = disabled;
            }
        }

        private Map<String, ActivityInfo> mMapAllInfos = new TreeMap<>();
        private Map<String, ActivityInfo> mMapDisabledInfos = new TreeMap<>();
        private Map<String, ActivityInfo> mMapEnabledInfos = new TreeMap<>();
        private List<ActivityInfo> mAllInfos;
        private List<ActivityInfo> mDisabledInfos;
        private List<ActivityInfo> mEnabledInfos;
        private Set<String> mDisabledActivities;
        private boolean mNeedReload = true;

        public ShareFilterDataProvider(ListCheckActivity activity) {
            super(activity);
            mDisabledActivities = new HashSet<>(
                    Prefs.instance().getStringSet(R.string.key_system_share_filter_activities, new HashSet<String>())
            );
            for (String action : IntentActions.sSendActions) {
                Intent intent = new Intent(action);
                intent.setType("*/*");
                List<ResolveInfo> infos = mActivity.getPackageManager().queryIntentActivities(intent, 0);
                for (ResolveInfo info : infos) {
                    String fullActivityName = info.activityInfo.applicationInfo.packageName + "/" + info.activityInfo.name;
                    ActivityInfo activityInfo = new ActivityInfo(info, mDisabledActivities.contains(fullActivityName));
                    mMapAllInfos.put(fullActivityName, activityInfo);
                    if (activityInfo.mDisabled) {
                        mMapDisabledInfos.put(fullActivityName, activityInfo);
                    } else {
                        mMapEnabledInfos.put(fullActivityName, activityInfo);
                    }
                }
            }
            reload();
        }

        @Override
        protected String getActivityTitle() {
            return mActivity.getString(R.string.pref_system_share_filter);
        }

        @Override
        protected String[] getTabTitles() {
            return new String[] {
                    mActivity.getString(R.string.all),
                    mActivity.getString(R.string.disabled),
                    mActivity.getString(R.string.enabled)
            };
        }

        @Override
        protected int getListItemCount(int tab) {
            if (tab == 0) {
                return mAllInfos.size();
            } else if (tab == 1) {
                return mDisabledInfos.size();
            } else if (tab == 2) {
                return mEnabledInfos.size();
            } else {
                throw new RuntimeException("Unknown tab " + tab);
            }
        }

        @Override
        protected ListItem getListItem(int tab, int position) {
            List<ActivityInfo> infos;
            if (tab == 0) {
                infos = mAllInfos;
            } else if (tab == 1) {
                infos = mDisabledInfos;
            } else if (tab == 2) {
                infos = mEnabledInfos;
            } else {
                throw new RuntimeException("Unknown tab " + tab);
            }

            ListItem item = new ListItem();
            final ActivityInfo activityInfo = infos.get(position);
            item.mData = activityInfo;
            item.mIcon = activityInfo.mInfo.loadIcon(mActivity.getPackageManager());
            item.mTitle = activityInfo.mInfo.activityInfo.applicationInfo.loadLabel(mActivity.getPackageManager());
            item.mDescription = activityInfo.mInfo.loadLabel(mActivity.getPackageManager());
            item.mChecked = activityInfo.mDisabled;
            return item;
        }

        @Override
        protected boolean reload() {
            if (!mNeedReload) {
                return false;
            }

            mNeedReload = false;
            mAllInfos = new ArrayList<>(mMapAllInfos.values());
            mDisabledInfos = new ArrayList<>(mMapDisabledInfos.values());
            mEnabledInfos = new ArrayList<>(mMapEnabledInfos.values());
            Logger.d("mAllInfos " + mAllInfos.size() + ", mDisabledInfos " + mDisabledInfos.size() + ", mEnabledInfos " + mEnabledInfos.size());
            return true;
        }

        @Override
        public void onCheckedChanged(ListItem item, Boolean isChecked) {
            ActivityInfo activityInfo = (ActivityInfo) item.mData;
            String fullActivityName = activityInfo.mInfo.activityInfo.applicationInfo.packageName + "/" + activityInfo.mInfo.activityInfo.name;
            Logger.i((isChecked ? "Disabled" : "Enabled") + " share activity " + fullActivityName);

            activityInfo.mDisabled = isChecked;
            if (isChecked) {
                mMapDisabledInfos.put(fullActivityName, activityInfo);
                mMapEnabledInfos.remove(fullActivityName);
                mDisabledActivities.add(fullActivityName);
            } else {
                mMapDisabledInfos.remove(fullActivityName);
                mMapEnabledInfos.put(fullActivityName, activityInfo);
                mDisabledActivities.remove(fullActivityName);
            }
            mNeedReload = true;

            Prefs.instance().edit()
                    .putStringSet(R.string.key_system_share_filter_activities, mDisabledActivities)
                    .commit();
        }
    }

    @PreferenceClick(prefs = R.string.key_prevent_running_set_list)
    private void setPreventList(Preference preference) {
        ListCheckActivity.create(getActivity(), PreventListDataProvider.class);
    }

    @PreferenceClick(prefs = R.string.key_trust_agent_wifi_aps)
    private void setSmartLockWifiList(Preference preference) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            KeyguardManager keyguardManager = (KeyguardManager) getActivity().getSystemService(Context.KEYGUARD_SERVICE);
            if (keyguardManager.isKeyguardSecure()) {
                Intent keyguardIntent = keyguardManager.createConfirmDeviceCredentialIntent(getString(R.string.pref_trust_agent_wifi), "");
                startActivityForResult(keyguardIntent, ActivityRequestCode.KEYGUARD);
            } else {
                Toast.makeText(getActivity(), R.string.secure_lock_screen_not_setup, Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(getActivity(), R.string.not_supported, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == ActivityRequestCode.KEYGUARD) {
            if (resultCode == Activity.RESULT_OK) {
                Intent intent = new Intent(getActivity(), TrustAgentWifiSettings.class);
                getActivity().startActivity(intent);
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    @PreferenceClick(prefs = R.string.key_solid_explorer_url_replacers)
    private void setupSolidExplorerUrlReplacer(Preference preference) {
        startActivity(new Intent(getActivity(), SolidExplorerUrlReplacerSettings.class));
    }

    @PreferenceChange(prefs = R.string.key_lineage_os_live_display_time, refreshAtStart = true)
    private void customizeLineageOSLiveDisplayTime(SwitchPreference preference, boolean enabled) {
        enablePreference(R.string.key_lineage_os_live_display_time_sunrise, enabled);
        enablePreference(R.string.key_lineage_os_live_display_time_sunset, enabled);
    }
}
