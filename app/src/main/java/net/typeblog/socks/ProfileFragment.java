package net.typeblog.socks;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.net.VpnService;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Toast;

import net.typeblog.socks.util.Profile;
import net.typeblog.socks.util.ProfileManager;
import net.typeblog.socks.util.Utility;

import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.SwitchCompat;
import androidx.preference.CheckBoxPreference;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreference;

import static net.typeblog.socks.util.Constants.*;

public class ProfileFragment extends PreferenceFragmentCompat implements Preference.OnPreferenceClickListener, Preference.OnPreferenceChangeListener,
        CompoundButton.OnCheckedChangeListener {
    private ProfileManager mManager;
    private Profile mProfile;

    private SwitchCompat mSwitch;
    private boolean mRunning = false;
    private boolean mStarting = false, mStopping = false;
    private final ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName p1, IBinder binder) {
            mBinder = IVpnService.Stub.asInterface(binder);

            try {
                mRunning = mBinder.isRunning();
            } catch (Exception e) {
                e.printStackTrace();
            }

            if (mRunning) {
                updateState();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName p1) {
            mBinder = null;
        }
    };

    private BroadcastReceiver bReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(INTENT_DISCONNECTED)){
                mSwitch.setOnCheckedChangeListener(null);
                mRunning = false;
                mStarting = false;
                mStopping = false;
                updateState();
            } else if (intent.getAction().equals(INTENT_CONNECTED)){
                mSwitch.setOnCheckedChangeListener(null);
                mRunning = true;
                mStarting = false;
                mStopping = false;
                updateState();
            }
        }
    };

    private final Runnable mStateRunnable = new Runnable() {
        @Override
        public void run() {
            updateState();
            mSwitch.postDelayed(this, 1000);
        }
    };
    private IVpnService mBinder;

    private ListPreference mPrefProfile, mPrefRoutes;
    private EditTextPreference mPrefServer, mPrefPort, mPrefUsername, mPrefPassword,
            mPrefDns, mPrefDnsPort, mPrefAppList, mPrefUDPGW,
            mPrefChiselServer, mPrefChiselAdditionalRemotes, mPrefChiselUsername, mPrefChiselPassword,
            mPrefChiselHeaders, mPrefChiselFingerprint, mPrefChiselMaxRetryCount, mPrefChiselMaxRetryInterval;
    private CheckBoxPreference mPrefUserpw, mPrefPerApp, mPrefAppBypass, mPrefIPv6, mPrefUDP, mPrefAuto;
    private SwitchPreference mPrefChiselEnabled;
    private PreferenceCategory mPrefChiselCategory;
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.settings);
        setHasOptionsMenu(true);
        mManager = new ProfileManager(getActivity().getApplicationContext());
        initPreferences();
        reload();
    }

    @Override
    public void onStart() {
        super.onStart();
        if (mSwitch != null)
            checkState();

        IntentFilter filter = new IntentFilter();
        filter.addAction(INTENT_DISCONNECTED);
        filter.addAction(INTENT_CONNECTED);
        getContext().registerReceiver(bReceiver, filter);
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {

    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.main, menu);

        MenuItem s = menu.findItem(R.id.switch_main);

        mSwitch = (SwitchCompat) s.getActionView();
        mSwitch.setOnCheckedChangeListener(this);
        checkState();
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.prof_add:
                addProfile();
                return true;
            case R.id.prof_del:
                removeProfile();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public boolean onPreferenceClick(Preference p) {
        // TODO: Implement this method
        return false;
    }

    @Override
    public boolean onPreferenceChange(Preference p, Object newValue) {
        if (p == mPrefProfile) {
            String name = newValue.toString();
            mProfile = mManager.getProfile(name);
            mManager.switchDefault(name);
            reload();
            return true;
        } else if (p == mPrefServer) {
            mProfile.setServer(newValue.toString());
            resetTextN(mPrefServer, newValue);
            return true;
        } else if (p == mPrefPort) {
            if (TextUtils.isEmpty(newValue.toString()))
                return false;

            mProfile.setPort(Integer.parseInt(newValue.toString()));
            resetTextN(mPrefPort, newValue);
            return true;
        } else if (p == mPrefUserpw) {
            mProfile.setIsUserpw(Boolean.parseBoolean(newValue.toString()));
            return true;
        } else if (p == mPrefUsername) {
            mProfile.setUsername(newValue.toString());
            resetTextN(mPrefUsername, newValue);
            return true;
        } else if (p == mPrefPassword) {
            mProfile.setPassword(newValue.toString());
            resetTextN(mPrefPassword, newValue);
            return true;
        } else if (p == mPrefRoutes) {
            mProfile.setRoute(newValue.toString());
            resetListN(mPrefRoutes, newValue);
            return true;
        } else if (p == mPrefDns) {
            mProfile.setDns(newValue.toString());
            resetTextN(mPrefDns, newValue);
            return true;
        } else if (p == mPrefDnsPort) {
            if (TextUtils.isEmpty(newValue.toString()))
                return false;

            mProfile.setDnsPort(Integer.valueOf(newValue.toString()));
            resetTextN(mPrefDnsPort, newValue);
            return true;
        } else if (p == mPrefPerApp) {
            mProfile.setIsPerApp(Boolean.parseBoolean(newValue.toString()));
            return true;
        } else if (p == mPrefAppBypass) {
            mProfile.setIsBypassApp(Boolean.parseBoolean(newValue.toString()));
            return true;
        } else if (p == mPrefAppList) {
            mProfile.setAppList(newValue.toString());
            return true;
        } else if (p == mPrefIPv6) {
            mProfile.setHasIPv6(Boolean.parseBoolean(newValue.toString()));
            return true;
        } else if (p == mPrefUDP) {
            mProfile.setHasUDP(Boolean.parseBoolean(newValue.toString()));
            return true;
        } else if (p == mPrefUDPGW) {
            mProfile.setUDPGW(newValue.toString());
            resetTextN(mPrefUDPGW, newValue);
            return true;
        } else if (p == mPrefAuto) {
            mProfile.setAutoConnect(Boolean.parseBoolean(newValue.toString()));
            return true;
        }

        else if (p == mPrefChiselEnabled) {
            boolean enabled = (Boolean)newValue;
            mProfile.setChiselEnabled(enabled);
            toggleChiselSettings(enabled);
            return true;
        } else if (p == mPrefChiselServer) {
            mProfile.setChiselServer(newValue.toString());
            resetTextN(mPrefChiselServer, newValue);
            return true;
        } else if (p == mPrefChiselAdditionalRemotes) {
            mProfile.setChiselAdditionalRemotes(newValue.toString());
            resetTextN(mPrefChiselAdditionalRemotes, newValue);
            return true;
        } else if (p == mPrefChiselUsername) {
            mProfile.setChiselUsername(newValue.toString());
            resetTextN(mPrefChiselUsername, newValue);
            return true;
        } else if (p == mPrefChiselPassword) {
            mProfile.setChiselPassword(newValue.toString());
            resetTextN(mPrefChiselPassword, newValue);
            return true;
        } else if (p == mPrefChiselFingerprint) {
            mProfile.setChiselFingerprint(newValue.toString());
            resetTextN(mPrefChiselFingerprint, newValue);
            return true;
        } else if (p == mPrefChiselHeaders) {
            mProfile.setChiselHeaders(newValue.toString());
            resetTextN(mPrefChiselHeaders, newValue);
            return true;
        } else if (p == mPrefChiselMaxRetryCount) {
            if (TextUtils.isEmpty(newValue.toString()))
                return false;

            mProfile.setChiselMaxRetryCount(Integer.parseInt(newValue.toString()));
            resetTextN(mPrefChiselMaxRetryCount, newValue);
            return true;
        } else if (p == mPrefChiselMaxRetryInterval) {
            if (TextUtils.isEmpty(newValue.toString()))
                return false;

            mProfile.setChiselMaxRetryInterval(Integer.parseInt(newValue.toString()));
            resetTextN(mPrefChiselMaxRetryInterval, newValue);
            return true;
        } else {
            return false;
        }
    }

    private void toggleChiselSettings(boolean enabled){
        if (enabled){
            mPrefChiselCategory.setVisible(true);
            mPrefUserpw.setChecked(false);
            mPrefUserpw.setEnabled(false);
            mPrefUDP.setChecked(false);
            mPrefUDP.setEnabled(false);
            mPrefServer.setText("127.0.0.1");
            mPrefServer.setEnabled(false);
            if (mPrefPort.getText().isEmpty())
                mPrefPort.setText("1080");
        } else {
            mPrefChiselCategory.setVisible(false);
            mPrefUserpw.setEnabled(true);
            mPrefUDP.setEnabled(true);
            mPrefServer.setEnabled(true);
        }
    }

    @Override
    public void onCheckedChanged(CompoundButton p1, boolean checked) {
        if (checked) {
            startVpn();
        } else {
            stopVpn();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == Activity.RESULT_OK) {
            Utility.startVpn(getActivity(), mProfile);
            checkState();
        }
    }

    private void initPreferences() {
        mPrefProfile = findPreference(PREF_PROFILE);
        mPrefServer = findPreference(PREF_SERVER_IP);
        mPrefPort = findPreference(PREF_SERVER_PORT);
        mPrefUserpw = findPreference(PREF_AUTH_USERPW);
        mPrefUsername = findPreference(PREF_AUTH_USERNAME);
        mPrefPassword = findPreference(PREF_AUTH_PASSWORD);
        mPrefRoutes = findPreference(PREF_ADV_ROUTE);
        mPrefDns = findPreference(PREF_ADV_DNS);
        mPrefDnsPort = findPreference(PREF_ADV_DNS_PORT);
        mPrefPerApp = findPreference(PREF_ADV_PER_APP);
        mPrefAppBypass = findPreference(PREF_ADV_APP_BYPASS);
        mPrefAppList = findPreference(PREF_ADV_APP_LIST);
        mPrefIPv6 = findPreference(PREF_IPV6_PROXY);
        mPrefUDP = findPreference(PREF_UDP_PROXY);
        mPrefUDPGW = findPreference(PREF_UDP_GW);
        mPrefAuto = findPreference(PREF_ADV_AUTO_CONNECT);

        mPrefChiselCategory = findPreference(PREF_CHISEL_CATEGORY);
        mPrefChiselEnabled = findPreference(PREF_CHISEL_ENABLED);
        mPrefChiselServer = findPreference(PREF_CHISEL_SERVER);
        mPrefChiselAdditionalRemotes = findPreference(PREF_CHISEL_ADDITIONAL_REMOTES);
        mPrefChiselUsername = findPreference(PREF_CHISEL_USERNAME);
        mPrefChiselPassword = findPreference(PREF_CHISEL_PASSWORD);
        mPrefChiselFingerprint = findPreference(PREF_CHISEL_FINGERPRINT);
        mPrefChiselHeaders = findPreference(PREF_CHISEL_HEADERS);
        mPrefChiselMaxRetryCount = findPreference(PREF_CHISEL_MAX_RETRY_COUNT);
        mPrefChiselMaxRetryInterval = findPreference(PREF_CHISEL_MAX_RETRY_INTERVAL);

        mPrefProfile.setOnPreferenceChangeListener(this);
        mPrefServer.setOnPreferenceChangeListener(this);
        mPrefPort.setOnPreferenceChangeListener(this);
        mPrefUserpw.setOnPreferenceChangeListener(this);
        mPrefUsername.setOnPreferenceChangeListener(this);
        mPrefPassword.setOnPreferenceChangeListener(this);
        mPrefRoutes.setOnPreferenceChangeListener(this);
        mPrefDns.setOnPreferenceChangeListener(this);
        mPrefDnsPort.setOnPreferenceChangeListener(this);
        mPrefPerApp.setOnPreferenceChangeListener(this);
        mPrefAppBypass.setOnPreferenceChangeListener(this);
        mPrefAppList.setOnPreferenceChangeListener(this);
        mPrefIPv6.setOnPreferenceChangeListener(this);
        mPrefUDP.setOnPreferenceChangeListener(this);
        mPrefUDPGW.setOnPreferenceChangeListener(this);
        mPrefAuto.setOnPreferenceChangeListener(this);

        mPrefChiselEnabled.setOnPreferenceChangeListener(this);
        mPrefChiselServer.setOnPreferenceChangeListener(this);
        mPrefChiselAdditionalRemotes.setOnPreferenceChangeListener(this);
        mPrefChiselUsername.setOnPreferenceChangeListener(this);
        mPrefChiselPassword.setOnPreferenceChangeListener(this);
        mPrefChiselFingerprint.setOnPreferenceChangeListener(this);
        mPrefChiselHeaders.setOnPreferenceChangeListener(this);
        mPrefChiselMaxRetryCount.setOnPreferenceChangeListener(this);
        mPrefChiselMaxRetryInterval.setOnPreferenceChangeListener(this);
    }

    private void reload() {
        if (mProfile == null) {
            mProfile = mManager.getDefault();
        }

        mPrefProfile.setEntries(mManager.getProfiles());
        mPrefProfile.setEntryValues(mManager.getProfiles());
        mPrefProfile.setValue(mProfile.getName());
        mPrefRoutes.setValue(mProfile.getRoute());
        resetList(mPrefProfile, mPrefRoutes);

        mPrefUserpw.setChecked(mProfile.isUserPw());
        mPrefPerApp.setChecked(mProfile.isPerApp());
        mPrefAppBypass.setChecked(mProfile.isBypassApp());
        mPrefIPv6.setChecked(mProfile.hasIPv6());
        mPrefUDP.setChecked(mProfile.hasUDP());
        mPrefAuto.setChecked(mProfile.autoConnect());

        mPrefServer.setText(mProfile.getServer());
        mPrefPort.setText(String.valueOf(mProfile.getPort()));
        mPrefPort.setOnBindEditTextListener(new EditTextPreference.OnBindEditTextListener() {
            @Override
            public void onBindEditText(@NonNull EditText editText) {
                editText.setInputType(InputType.TYPE_NUMBER_FLAG_SIGNED);
            }
        });
        mPrefUsername.setText(mProfile.getUsername());
        mPrefPassword.setText(mProfile.getPassword());
        mPrefPassword.setOnBindEditTextListener(new EditTextPreference.OnBindEditTextListener() {
            @Override
            public void onBindEditText(@NonNull EditText editText) {
                editText.setInputType(InputType.TYPE_TEXT_VARIATION_PASSWORD);
            }
        });
        mPrefDns.setText(mProfile.getDns());
        mPrefDnsPort.setText(String.valueOf(mProfile.getDnsPort()));
        mPrefDnsPort.setOnBindEditTextListener(new EditTextPreference.OnBindEditTextListener() {
            @Override
            public void onBindEditText(@NonNull EditText editText) {
                editText.setInputType(InputType.TYPE_NUMBER_FLAG_SIGNED);
            }
        });
        mPrefUDPGW.setText(mProfile.getUDPGW());

        mPrefChiselPassword.setOnBindEditTextListener(new EditTextPreference.OnBindEditTextListener() {
            @Override
            public void onBindEditText(@NonNull EditText editText) {
                editText.setInputType(InputType.TYPE_TEXT_VARIATION_PASSWORD);
            }
        });

        mPrefChiselMaxRetryCount.setOnBindEditTextListener(new EditTextPreference.OnBindEditTextListener() {
            @Override
            public void onBindEditText(@NonNull EditText editText) {
                editText.setInputType(InputType.TYPE_NUMBER_FLAG_SIGNED);
            }
        });

        mPrefChiselMaxRetryInterval.setOnBindEditTextListener(new EditTextPreference.OnBindEditTextListener() {
            @Override
            public void onBindEditText(@NonNull EditText editText) {
                editText.setInputType(InputType.TYPE_NUMBER_FLAG_SIGNED);
            }
        });

        mPrefChiselEnabled.setOnPreferenceChangeListener(null);
        mPrefChiselEnabled.setChecked(mProfile.getChiselEnabled());
        toggleChiselSettings(mProfile.getChiselEnabled());
        mPrefChiselEnabled.setOnPreferenceChangeListener(this);

        mPrefChiselServer.setText(String.valueOf(mProfile.getChiselServer()));
        mPrefChiselAdditionalRemotes.setText(String.valueOf(mProfile.getChiselAdditionalRemotes()));
        mPrefChiselUsername.setText(String.valueOf(mProfile.getChiselUsername()));
        mPrefChiselPassword.setText(String.valueOf(mProfile.getChiselPassword()));
        mPrefChiselFingerprint.setText(String.valueOf(mProfile.getChiselFingerprint()));
        mPrefChiselHeaders.setText(String.valueOf(mProfile.getChiselHeaders()));
        mPrefChiselMaxRetryCount.setText(String.valueOf(mProfile.getChiselMaxRetryCount()));
        mPrefChiselMaxRetryInterval.setText(String.valueOf(mProfile.getChiselMaxRetryInterval()));

        resetText(mPrefServer, mPrefPort, mPrefUsername, mPrefPassword, mPrefDns, mPrefDnsPort, mPrefUDPGW,
                mPrefChiselServer, mPrefChiselAdditionalRemotes, mPrefChiselUsername, mPrefChiselPassword,
                mPrefChiselFingerprint, mPrefChiselHeaders, mPrefChiselMaxRetryCount, mPrefChiselMaxRetryInterval);

        mPrefAppList.setText(mProfile.getAppList());
    }

    private void resetList(ListPreference... pref) {
        for (ListPreference p : pref)
            p.setSummary(p.getEntry());
    }

    private void resetListN(ListPreference pref, Object newValue) {
        pref.setSummary(newValue.toString());
    }

    private void resetText(EditTextPreference... pref) {
        for (EditTextPreference p : pref) {
            if (!p.getKey().equals("auth_password") && !p.getKey().equals(PREF_CHISEL_PASSWORD)) {
                p.setSummary(p.getText());
            } else {
                if (p.getText() != null && p.getText().length() > 0)
                    p.setSummary(String.format(Locale.US,
                            String.format(Locale.US, "%%0%dd", p.getText().length()), 0)
                            .replace("0", "*"));
                else
                    p.setSummary("");
            }
        }
    }

    private void resetTextN(EditTextPreference pref, Object newValue) {
        if (!pref.getKey().equals("auth_password") && !pref.getKey().equals(PREF_CHISEL_PASSWORD)) {
            pref.setSummary(newValue.toString());
        } else {
            String text = newValue.toString();
            if (text.length() > 0)
                pref.setSummary(String.format(Locale.US,
                        String.format(Locale.US, "%%0%dd", text.length()), 0)
                        .replace("0", "*"));
            else
                pref.setSummary("");
        }
    }

    private void addProfile() {
        final EditText e = new EditText(getActivity());
        e.setSingleLine(true);

        new AlertDialog.Builder(getActivity())
                .setTitle(R.string.prof_add)
                .setView(e)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        String name = e.getText().toString().trim();

                        if (!TextUtils.isEmpty(name)) {
                            Profile p = mManager.addProfile(name);

                            if (p != null) {
                                mProfile = p;
                                reload();
                                return;
                            }
                        }

                        Toast.makeText(getActivity(),
                                String.format(getString(R.string.err_add_prof), name),
                                Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {

                    }
                })
                .create().show();
    }

    private void removeProfile() {
        new AlertDialog.Builder(getActivity())
                .setTitle(R.string.prof_del)
                .setMessage(String.format(getString(R.string.prof_del_confirm), mProfile.getName()))
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        if (!mManager.removeProfile(mProfile.getName())) {
                            Toast.makeText(getActivity(),
                                    getString(R.string.err_del_prof, mProfile.getName()),
                                    Toast.LENGTH_SHORT).show();
                        } else {
                            mProfile = mManager.getDefault();
                            reload();
                        }
                    }
                })
                .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {

                    }
                })
                .create().show();
    }

    private void checkState() {
        mRunning = false;
        mSwitch.setEnabled(false);
        mSwitch.setOnCheckedChangeListener(null);

        if (mBinder == null) {
            getActivity().bindService(new Intent(getActivity(), SocksVpnService.class), mConnection, 0);
            mSwitch.postDelayed(mStateRunnable, 1000);
        } else {
            try {
                if (mBinder.isRunning())
                    updateState();
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    private void updateState() {
        if (mBinder == null) {
            mRunning = false;
        } else {
            try {
                mRunning = mBinder.isRunning();
            } catch (Exception e) {
                mRunning = false;
            }
        }

        mSwitch.setChecked(mRunning);

        if ((!mStarting && !mStopping) || (mStarting && mRunning) || (mStopping && !mRunning)) {
            mSwitch.setEnabled(true);
        }

        if (mStarting && mRunning) {
            mStarting = false;
        }

        if (mStopping && !mRunning) {
            mStopping = false;
        }

        mSwitch.setOnCheckedChangeListener(ProfileFragment.this);
    }

    private void startVpn() {
        mStarting = true;
        Intent i = VpnService.prepare(getActivity());

        if (i != null) {
            startActivityForResult(i, 0);
        } else {
            onActivityResult(0, Activity.RESULT_OK, null);
        }
    }

    private void stopVpn() {
        if (mBinder == null)
            return;

        mStopping = true;

        try {
            mBinder.stop();
        } catch (Exception e) {
            e.printStackTrace();
        }

        mBinder = null;

        getActivity().unbindService(mConnection);
        checkState();
    }

    @Override
    public void onStop() {
        try {
            getContext().unregisterReceiver(bReceiver);
        } catch (IllegalArgumentException e) {
        }
        super.onStop();
    }
}
