package ch.uepaa.quickstart;

import android.graphics.Color;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.text.format.DateFormat;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;

import java.util.UUID;

import ch.uepaa.p2pkit.ConnectionCallbacks;
import ch.uepaa.p2pkit.ConnectionResult;
import ch.uepaa.p2pkit.ConnectionResultHandling;
import ch.uepaa.p2pkit.KitClient;
import ch.uepaa.p2pkit.discovery.GeoListener;
import ch.uepaa.p2pkit.discovery.InfoTooLongException;
import ch.uepaa.p2pkit.discovery.P2pListener;
import ch.uepaa.p2pkit.discovery.Peer;
import ch.uepaa.p2pkit.messaging.MessageListener;

public class MainActivity extends AppCompatActivity implements ColorPickerDialog.ColorPickerListener{

    private static final String APP_KEY = "<YOUR PERSONAL APP KEY>";

    // Initialization (1/2) - Connect to the P2P Services
    private void enableKit() {

        final int statusCode = KitClient.isP2PServicesAvailable(this);
        if (statusCode == ConnectionResult.SUCCESS) {
            KitClient client = KitClient.getInstance(this);
            client.registerConnectionCallbacks(mConnectionCallbacks);

            if (client.isConnected()) {
                logToView("Client already connected");
            } else {
                logToView("Connecting P2PKit client");
                client.connect(APP_KEY);
            }
            mWantToConnect = false;
        } else {
            mWantToConnect = true;
            logToView("Cannot start P2PKit, status code: " + statusCode);
            ConnectionResultHandling.showAlertDialogForConnectionError(this, statusCode);
        }
    }

    // Initialization (2/2) - Handle the connection status callbacks with the P2P Services
    private final ConnectionCallbacks mConnectionCallbacks = new ConnectionCallbacks() {

        @Override
        public void onConnected() {
            logToView("Successfully connected to P2P Services, with node id: " + KitClient.getInstance(MainActivity.this).getNodeId().toString());

            mP2pSwitch.setEnabled(true);
            mGeoSwitch.setEnabled(true);

            if (mShouldStartServices) {
                mShouldStartServices = false;

                startP2pDiscovery();
                startGeoDiscovery();
            }
        }

        @Override
        public void onConnectionSuspended() {
            logToView("Connection to P2P Services suspended");

            mGeoSwitch.setEnabled(false);
            mP2pSwitch.setEnabled(false);
        }

        @Override
        public void onConnectionFailed(ConnectionResult connectionResult) {
            logToView("Connection to P2P Services failed with status: " + connectionResult.getStatusCode());
            ConnectionResultHandling.showAlertDialogForConnectionError(MainActivity.this, connectionResult.getStatusCode());
        }
    };

    private void disableKit() {
        KitClient.getInstance(this).disconnect();
    }

    private void startP2pDiscovery() {
        try {
            KitClient.getInstance(this).getDiscoveryServices().setP2pDiscoveryInfo(getColorBytes(mCurrentColor));
        } catch (InfoTooLongException e) {
            logToView("P2pListener | The discovery info is too long");
        }
        KitClient.getInstance(this).getDiscoveryServices().addListener(mP2pDiscoveryListener);
    }

    // Listener of P2P discovery events
    private final P2pListener mP2pDiscoveryListener = new P2pListener() {

        @Override
        public void onStateChanged(final int state) {
            logToView("P2pListener | State changed: " + state);
        }

        @Override
        public void onPeerDiscovered(final Peer peer) {
            byte[] colorBytes = peer.getDiscoveryInfo();
            if (colorBytes != null && colorBytes.length == 3) {
                logToView("P2pListener | Peer discovered: " + peer.getNodeId() + " with color: " + getHexRepresentation(colorBytes));
            } else {
                logToView("P2pListener | Peer discovered: " + peer.getNodeId() + " without color");
            }
        }

        @Override
        public void onPeerLost(final Peer peer) {
            logToView("P2pListener | Peer lost: " + peer.getNodeId());
        }

        @Override
        public void onPeerUpdatedDiscoveryInfo(Peer peer) {
            byte[] colorBytes = peer.getDiscoveryInfo();
            if (colorBytes != null && colorBytes.length == 3) {
                logToView("P2pListener | Peer updated: " + peer.getNodeId() + " with new color: " + getHexRepresentation(colorBytes));
            }
        }
    };

    private void stopP2pDiscovery() {
        KitClient.getInstance(this).getDiscoveryServices().removeListener(mP2pDiscoveryListener);
        logToView("P2pListener removed");
    }

    private void startGeoDiscovery() {
        KitClient.getInstance(this).getMessageServices().addListener(mMessageListener);

        KitClient.getInstance(this).getDiscoveryServices().addListener(mGeoDiscoveryListener);
    }

    private final GeoListener mGeoDiscoveryListener = new GeoListener() {

        @Override
        public void onStateChanged(final int state) {
            logToView("GeoListener | State changed: " + state);
        }

        @Override
        public void onPeerDiscovered(final UUID nodeId) {
            logToView("GeoListener | Peer discovered: " + nodeId);

            // sending a message to the peer
            KitClient.getInstance(MainActivity.this).getMessageServices().sendMessage(nodeId, "SimpleChatMessage", "From Android: Hello GEO!".getBytes());
        }

        @Override
        public void onPeerLost(final UUID nodeId) {
            logToView("GeoListener | Peer lost: " + nodeId);
        }
    };

    private final MessageListener mMessageListener = new MessageListener() {

        @Override
        public void onStateChanged(final int state) {
            logToView("MessageListener | State changed: " + state);
        }

        @Override
        public void onMessageReceived(final long timestamp, final UUID origin, final String type, final byte[] message) {
            logToView("MessageListener | Message received: From=" + origin + " type=" + type + " message=" + new String(message));
        }
    };

    private void stopGeoDiscovery() {
        KitClient.getInstance(this).getMessageServices().removeListener(mMessageListener);
        logToView("MessageListener removed");

        KitClient.getInstance(this).getDiscoveryServices().removeListener(mGeoDiscoveryListener);
        logToView("GeoListener removed");
    }

    private boolean mShouldStartServices;
    private boolean mWantToConnect = false;

    private int mCurrentColor;

    private TextView mLogView;
    private Switch mP2pSwitch;
    private Switch mGeoSwitch;

    @Override
    public void onColorPicked(int colorCode) {
        mCurrentColor = colorCode;

        if (mShouldStartServices) {
            enableKit();
        } else if (KitClient.getInstance(this).isConnected()) {
            try {
                byte[] colorBytes = getColorBytes(mCurrentColor);
                KitClient.getInstance(this).getDiscoveryServices().setP2pDiscoveryInfo(colorBytes);
            } catch (InfoTooLongException e) {
                logToView("P2pListener | The discovery info is too long");
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setupUI();

        mShouldStartServices = true;
        showColorPickerDialog();
    }

    @Override
    public void onResume() {
        super.onResume();

        // When to user comes back from playstore after installing p2p services, try to enable p2pkit again
        if(mWantToConnect && !KitClient.getInstance(this).isConnected()) {
            enableKit();
        }
    }

    private void setupUI() {
        mLogView = (TextView) findViewById(R.id.textView);

        findViewById(R.id.clearTextView).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clearLogs();
            }
        });

        findViewById(R.id.changeColorTextView).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showColorPickerDialog();
            }
        });

        Switch kitSwitch = (Switch) findViewById(R.id.kitSwitch);
        kitSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {

                if (isChecked) {
                    enableKit();
                } else {
                    mP2pSwitch.setChecked(false);
                    mGeoSwitch.setChecked(false);

                    mWantToConnect = false;
                    disableKit();
                }
            }
        });

        mP2pSwitch = (Switch) findViewById(R.id.p2pSwitch);
        mP2pSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    startP2pDiscovery();
                } else {
                    stopP2pDiscovery();
                }
            }
        });

        mGeoSwitch = (Switch) findViewById(R.id.geoSwitch);
        mGeoSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    startGeoDiscovery();
                } else {
                    stopGeoDiscovery();
                }
            }
        });
    }

    private void logToView(String message) {
        CharSequence currentTime = DateFormat.format("hh:mm:ss - ", System.currentTimeMillis());
        mLogView.setText(currentTime + message + "\n" + mLogView.getText());
    }

    private void clearLogs() {
        mLogView.setText("");
    }

    private String getHexRepresentation(byte[] colorBytes) {
        int colorCode = Color.rgb(colorBytes[0] & 0xFF, colorBytes[1] & 0xFF, colorBytes[2] & 0xFF);
        return String.format("#%06X", (0xFFFFFF & colorCode));
    }

    private byte[] getColorBytes(int color) {
        return new byte[] {(byte) Color.red(color), (byte) Color.green(color), (byte) Color.blue(color)};
    }

    private void showColorPickerDialog() {
        ColorPickerDialog dialog = ColorPickerDialog.newInstance(mCurrentColor);
        dialog.show(getFragmentManager(), "ColorPicker");
    }
}