package net.typeblog.socks;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.VpnService;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.util.Log;

import net.typeblog.socks.util.Constants;
import net.typeblog.socks.util.Profile;
import net.typeblog.socks.util.ProfileManager;
import net.typeblog.socks.util.Utility;

import androidx.annotation.RequiresApi;

import static net.typeblog.socks.util.Constants.INTENT_CONNECTED;
import static net.typeblog.socks.util.Constants.INTENT_DISCONNECTED;

@RequiresApi(api = Build.VERSION_CODES.N)
public class QSLastProfile extends TileService {

    public QSLastProfile(){
        super();
    }

    private BroadcastReceiver bReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(INTENT_DISCONNECTED))
                setActive(false);
            else if (intent.getAction().equals(INTENT_CONNECTED))
                setActive(true);
        }
    };
    @Override
    public void onStartListening() {
        //binding to service here is messy
        boolean running = false;
        ActivityManager manager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (SocksVpnService.class.getName().equals(service.service.getClassName())) {
                running = true;
            }
        }
        setActive(running);

        IntentFilter filter = new IntentFilter();
        filter.addAction(INTENT_DISCONNECTED);
        filter.addAction(INTENT_CONNECTED);
        registerReceiver(bReceiver, filter);
    }

    @Override
    public void onClick() {
        boolean isActive = getQsTile().getState() == Tile.STATE_ACTIVE;
        if (isActive){
            sendBroadcast(new Intent(Constants.INTENT_DISCONNECT));
            setActive(false);
        } else {
            Profile profile = new ProfileManager(getApplicationContext()).getDefault();
            Intent i = VpnService.prepare(getApplicationContext());
            if (i == null) {
                Utility.startVpn(getApplicationContext(), profile);
                setActive(true);
            } else {
                getQsTile().setLabel(getString(R.string.no_vpn_perm));
                getQsTile().updateTile();
            }
        }
    }

    @Override
    public void onStopListening() {
        try {
            unregisterReceiver(bReceiver);
        } catch (IllegalArgumentException e) {
        }
    }

    private void setActive(boolean isActive){
        Tile qsTile = getQsTile();
        if (qsTile == null)
            return;
        if (isActive){
            qsTile.setState(Tile.STATE_ACTIVE);
            qsTile.setLabel(getString(R.string.disconnect));
        } else {
            qsTile.setState(Tile.STATE_INACTIVE);
            Profile profile = new ProfileManager(getApplicationContext()).getDefault();
            qsTile.setLabel(profile.getName());
        }
        qsTile.updateTile();
    }
}
