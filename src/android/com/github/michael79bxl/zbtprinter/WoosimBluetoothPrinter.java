package android.com.github.michael79bxl.zbtprinter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;

import android.app.Activity;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;

import java.io.InputStream;
import java.util.Set;
import java.util.UUID;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.bluetooth.BluetoothSocket;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Message;
import android.text.TextPaint;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.util.Log;
import android.widget.Toast;

import com.woosim.printer.WoosimCmd;
import com.woosim.printer.WoosimImage;
import com.woosim.printer.WoosimService;

import static org.chromium.base.ApplicationStatus.getApplicationContext;

public class WoosimBluetoothPrinter extends CordovaPlugin {

    private static final String LOG_TAG = "WoosimBluetoothPrinter";

    /* Variable declaration for bitmpa print */
    String base64Png = null;
    BluetoothSocket mmSocket;
    BluetoothDevice deviceTemp;

    protected static BluetoothPrintService mPrintService = null;
    protected static MainActivity mPrintServ = null;
    private WoosimService mWoosim = null;
    private String jsonCmdAttribStr = null;
    ProgressDialog progressPrintDialogue;
    Activity mActivity=null;
    CallbackContext callbackContextTemp=null;
    private boolean checkIfAlreadyPrinting=false;
    private String mConnectedDeviceName = null;

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

    public WoosimBluetoothPrinter() {

    }

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        mActivity=this.cordova.getActivity();
        if (action.equals("lpPrint")) {
            try {
                String mac = args.getString(0);
                String msg = args.getString(1);
                String sPrinterURI = mac;
                sendData(callbackContext, sPrinterURI, msg);
            } catch (Exception e) {
                Log.e(LOG_TAG, e.getMessage());
                e.printStackTrace();
            }
            return true;
        }
        else if (action.equals("lpFind")) {
            try {
                findPrinter(callbackContext);
            } catch (Exception e) {
                Log.e(LOG_TAG, e.getMessage());
                e.printStackTrace();
            }
            return true;
        }
        return false;
    }

    public void findPrinter(final CallbackContext callbackContext) {
        String macAddress ="";
        try {

            BluetoothAdapter bluetoothAdapter   = BluetoothAdapter.getDefaultAdapter();
            if(!bluetoothAdapter.isEnabled()) {
                bluetoothAdapter.enable();
            }
            Set<BluetoothDevice> pairedDevices  = bluetoothAdapter.getBondedDevices();

            if (pairedDevices.size() > 0) {
                for (BluetoothDevice device : pairedDevices) {
                    macAddress = device.getAddress();
                    mPrintServ = new MainActivity();
                    if (mPrintService == null)
                        mPrintServ.setupPrint(macAddress);
                    callbackContext.success(macAddress);
                }
            } else{
                callbackContext.error("can't found");
            }
        } catch (Exception e) {
            e.printStackTrace();
            callbackContext.error("something went wrong");
        }
    }

    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_DEVICE_NAME:
                    // save the connected device's name
                    mConnectedDeviceName = msg.getData().getString(DEVICE_NAME);
                    Toast.makeText(getApplicationContext(), "Connected to " + mConnectedDeviceName, Toast.LENGTH_SHORT).show();
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
                                    //mTrack1View.setText(str);
                                }
                                if (track[1] != null) {
                                    String str = new String(track[1]);
                                    //mTrack2View.setText(str);
                                }
                                if (track[2] != null) {
                                    String str = new String(track[2]);
                                    //mTrack3View.setText(str);
                                }
                            }
                            break;
                    }
                    break;
            }
        }
    };

    /*
    * This will send data to be printed by the bluetooth printer
    */
    void sendData(final CallbackContext callbackContext, final String mac, final String msg) throws IOException {
        callbackContextTemp=callbackContext;
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                connectToPrinter(msg, mac);
            }
        });
        t.start();
    }
    /* Bitmap print for chinese characters */
    public void connectToPrinter(String jsonCpclStrWithMaxHeight, String macAddress) {

        String finalJsonStr=jsonCpclStrWithMaxHeight;
        // calling on ui thread wait....for time
        if(!checkIfAlreadyPrinting){ // check if already printing
            mActivity.runOnUiThread(new Runnable() {
                public void run() {
                    checkIfAlreadyPrinting = true;
                    showProgressBar();
                }
            });

            int printReceiptWidth = 800;
            int printReceiptHeight=0;

            try{
                JSONObject prntStrObj= new JSONObject(finalJsonStr);
                printReceiptHeight=Integer.parseInt(prntStrObj.getString("printMaxHeight"));
            }catch (Exception e){
                e.printStackTrace();
            }

            if(printReceiptHeight!=0){
                printReceiptHeight=printReceiptHeight+200;
                //creating bitmap to print

                Bitmap mBitmap = Bitmap.createBitmap(printReceiptWidth, printReceiptHeight, Bitmap.Config.ARGB_8888);
                //Bitmap.setDensity(DisplayMetrics.DENSITY_DEFAULT);

                // creating canvas to write data
                if(writeOncanvas(printReceiptWidth,printReceiptHeight,mBitmap,finalJsonStr)){

                    //Bitmap bmp = Bitmap.createScaledBitmap(mBitmap, 384, 200, false);
                    //byte[] data = WoosimImage.printBitmap(10, 10, 384, 200, bmp);
                    byte[] data = WoosimImage.fastPrintARGBbitmap(10, 10, printReceiptWidth, printReceiptHeight, mBitmap);
                    //bmp.recycle();
                    mPrintServ.mPrintService.write(WoosimCmd.initPrinter());
                    mPrintServ.mPrintService.write(WoosimCmd.setPageMode());
                    mPrintServ.mPrintService.write(data);
                    mPrintServ.mPrintService.write(WoosimCmd.printData());
                    mPrintServ.mPrintService.write(WoosimCmd.PM_setStdMode());
                    hideProgressBar(1);
                }else{
                    hideProgressBar(4);
                }

                if(mBitmap!=null) {
                    mBitmap.recycle();
                }

            }else{
                hideProgressBar(5);
            }
            if (mPrintServ.mPrintService != null)
                mPrintServ.mPrintService.stop();
        }
        checkIfAlreadyPrinting=false;
    }


    private boolean writeOncanvas(int printReceiptWidth,int printReceiptHeight,Bitmap bmp, String jsonArrayStr){
        boolean status=false;
        float ratioX  = printReceiptWidth / (float) bmp.getWidth();
        float ratioY  = printReceiptHeight / (float) bmp.getHeight();
        float middleX = printReceiptWidth / 2.0f;
        float middleY = printReceiptHeight / 2.0f;
        Matrix scaleMatrix = new Matrix();
        scaleMatrix.setScale(ratioX, ratioY, middleX, middleY);
        Canvas canvas = new Canvas(bmp);
        canvas.drawColor(Color.WHITE);
        canvas.setMatrix(scaleMatrix);

        TextPaint textPaintNormal;
        try{
            JSONObject prntStrObjForArray= new JSONObject(jsonArrayStr);
            JSONArray printStrJsonArray = prntStrObjForArray.getJSONArray("printStr");
            for(int i=0;i< printStrJsonArray.length();i++) {
                JSONObject jsonObject = printStrJsonArray.getJSONObject(i);
                String val="";
                String isArabic = "";
                if(jsonObject.has("strVal")){
                    val=jsonObject.getString("strVal");
                }
                int xVal=Integer.parseInt(jsonObject.getString("xIndex"));
                textPaintNormal = makeBoldOrNonBoldTextPaint(1);
                if(jsonObject.has("isArabic")){
                    isArabic = jsonObject.getString("isArabic");
                    if(isArabic.equals("1")){
                        textPaintNormal = arabicPaint(1);
                    }
                }


                canvas.drawText(val, xVal, (Integer.parseInt(jsonObject.getString("yIndex"))+10), textPaintNormal);
            }// for lop end

            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            bmp.compress(Bitmap.CompressFormat.PNG, 0, stream);
            byte[] imgData = stream.toByteArray();
            base64Png = Base64.encodeToString(imgData, Base64.DEFAULT);
            status=true;
            stream.flush();
            stream.close();

        }catch (Exception e){
            e.printStackTrace();
            status=false;
        }

        return status;
    }

    private  TextPaint arabicPaint(int type){
        TextPaint textPaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
        textPaint.setAntiAlias(true);
        textPaint.setFilterBitmap(true);
        textPaint.setDither(true);
        textPaint.setStyle(Paint.Style.FILL);
        textPaint.setColor(Color.parseColor("#000000"));
        textPaint.setTextAlign(Paint.Align.RIGHT);
        Typeface bold = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);
        textPaint.setTypeface(bold);
        textPaint.setTextSize(20);
        return textPaint;
    }

    private  TextPaint makeBoldOrNonBoldTextPaint(int type){
        TextPaint textPaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
        textPaint.setAntiAlias(true);
        textPaint.setFilterBitmap(true);
        textPaint.setDither(true);
        textPaint.setStyle(Paint.Style.FILL);
        textPaint.setColor(Color.parseColor("#000000"));
        Typeface bold = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);
        textPaint.setTypeface(bold);
        textPaint.setTextSize(20);
        return textPaint;
    }

    private void showProgressBar(){
        progressPrintDialogue=null;
        progressPrintDialogue = new ProgressDialog(mActivity);
        progressPrintDialogue.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        progressPrintDialogue.setMessage("Printing. Please wait...");
        progressPrintDialogue.setIndeterminate(true);
        progressPrintDialogue.setCanceledOnTouchOutside(false);
        progressPrintDialogue.show();
    }

    private void hideProgressBar(final int dismisstype){
        mActivity.runOnUiThread(new Runnable() {
            public void run() {
                checkIfAlreadyPrinting=false;
                if(progressPrintDialogue!=null){
                    progressPrintDialogue.dismiss();
                    if(dismisstype==1) {
                        callbackContextTemp.success("printed");
                    }else{
                        callbackContextTemp.error("failed to print");
                    }
                }else {
                    callbackContextTemp.error("failed to print");
                }
            }
        });
    }
}

