package android.com.github.michael79bxl.zbtprinter;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.os.Handler;
import android.os.Message;
import android.view.Menu;
import android.widget.TextView;
import android.widget.Toast;

import com.woosim.printer.WoosimBarcode;
import com.woosim.printer.WoosimCmd;
import com.woosim.printer.WoosimImage;
import com.woosim.printer.WoosimService;

public class MainActivity extends Activity {
    // Debugging
    private static final String TAG = "MainActivity";
    private static final boolean D = true;

    // Message types sent from the BluetoothPrintService Handler
    public static final int MESSAGE_DEVICE_NAME = 1;
    public static final int MESSAGE_TOAST = 2;
    public static final int MESSAGE_READ = 3;

    // Key names received from the BluetoothPrintService Handler
    public static final String DEVICE_NAME = "device_name";
    public static final String TOAST = "toast";

    // Intent request codes
    private static final int REQUEST_CONNECT_DEVICE_SECURE = 1;
    private static final int REQUEST_CONNECT_DEVICE_INSECURE = 2;
    private static final int REQUEST_ENABLE_BT = 3;

    // Name of the connected device
    private String mConnectedDeviceName = null;
    // Local Bluetooth adapter
    private BluetoothAdapter mBluetoothAdapter = null;
    // Member object for the print services
    public BluetoothPrintService mPrintService = null;
    private WoosimService mWoosim = null;

	public void setupPrint(String MacAddress) {
		// Initialize the BluetoothPrintService to perform bluetooth connections
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        mPrintService = new BluetoothPrintService(this,mHandler);
		mWoosim = new WoosimService(mHandler);
		connectDevice(MacAddress, true);
	}

    // The Handler that gets information back from the BluetoothPrintService
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case MESSAGE_DEVICE_NAME:
                // save the connected device's name
                mConnectedDeviceName = msg.getData().getString(DEVICE_NAME);
                Toast.makeText(getApplicationContext(), "Connected to " + mConnectedDeviceName, Toast.LENGTH_SHORT).show();
                //redrawMenu();
                break;
            case MESSAGE_TOAST:
                Toast.makeText(getApplicationContext(), msg.getData().getInt(TOAST), Toast.LENGTH_SHORT).show();
                break;
            case MESSAGE_READ:
                mWoosim.processRcvData((byte[])msg.obj, msg.arg1);
                break;
            case WoosimService.MESSAGE_PRINTER:
            	switch (msg.arg1) {
            	case WoosimService.MSR:
            		if (msg.arg2 == 0) {
            			Toast.makeText(getApplicationContext(), "MSR reading failure", Toast.LENGTH_SHORT).show();
            		} else {
                    	byte[][] track = (byte[][])msg.obj;
                    	if (track[0] != null) {
                    		String str = new String(track[0]);
                    	}
                    	if (track[1] != null) {
                    		String str = new String(track[1]);
                    	}
                    	if (track[2] != null) {
                    		String str = new String(track[2]);
                    	}
            		}
                	break;
            	}
            	break;
            }
        }
    };


    private void connectDevice(String blueToohAddress, boolean secure) {
        // Get the device MAC address
        String address = blueToohAddress;
        // Get the BLuetoothDevice object
        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        // Attempt to connect to the device
        mPrintService.connect(device, secure);
    }
}
