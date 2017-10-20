package android.com.github.michael79bxl.zbtprinter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;

import android.app.Activity;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;


import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.util.DisplayMetrics;
import android.util.Log;

import com.zebra.android.comm.BluetoothPrinterConnection;
import com.zebra.android.comm.ZebraPrinterConnection;
import com.zebra.android.comm.ZebraPrinterConnectionException;
import com.zebra.android.printer.PrinterStatus;
import com.zebra.android.printer.ZebraPrinter;
import com.zebra.android.printer.ZebraPrinterFactory;
import com.zebra.android.printer.ZebraPrinterLanguageUnknownException;


public class ZebraBluetoothPrinter extends CordovaPlugin {

    private static final String LOG_TAG = "ZebraBluetoothPrinter";

    /* Variable declaration for bitmpa print */
    ZebraPrinterConnection zebraPrinterConnection;
    ZebraPrinter zebra;
    ProgressDialog progressPrintDialogue;
    Activity mActivity=null;
    CallbackContext callbackContextTemp=null;
    private boolean checkIfAlreadyPrinting=false;
    private boolean isCheckIfAlreadyCallConnectionOpenMethod=false;
    //String mac = "AC:3F:A4:1D:7A:5C";

    public ZebraBluetoothPrinter() {

    }

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        mActivity=this.cordova.getActivity();
        if (action.equals("print")) {
            try {
                showProgressBar();
                String mac = args.getString(0);
                String msg = args.getString(1);
                sendData(callbackContext, mac, msg);
            } catch (Exception e) {
                Log.e(LOG_TAG, e.getMessage());
                e.printStackTrace();
            }
            return true;
        } else if (action.equals("find")) {
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
        /*isCheckIfAlreadyCallConnectionOpenMethod=false;
        try {

            BluetoothAdapter bluetoothAdapter   = BluetoothAdapter.getDefaultAdapter();
            Set<BluetoothDevice> pairedDevices  = bluetoothAdapter.getBondedDevices();

            if (pairedDevices.size() > 0) {
                for (BluetoothDevice device : pairedDevices) {
                    final String macAddress = device.getAddress();
                    //I found a printer! I can use the properties of a Discovered printer (address) to make a Bluetooth Connection
                    Thread connectPrinterThread= new Thread(new Runnable() {
                        @Override
                        public void run() {
                            openBluethoothConnection(macAddress);
                        }
                    });
                    connectPrinterThread.start();
                    callbackContext.success(macAddress);
                    break;
                }
            } else{
                callbackContext.error("can't found");
            }
        } catch (Exception e) {
            e.printStackTrace();
            callbackContext.error("something went wrong");
        }*/

        String macAddress ="";
        try {

            BluetoothAdapter bluetoothAdapter   = BluetoothAdapter.getDefaultAdapter();
            Set<BluetoothDevice> pairedDevices  = bluetoothAdapter.getBondedDevices();

            if (pairedDevices.size() > 0) {
                for (BluetoothDevice device : pairedDevices) {
                    macAddress = device.getAddress();
                    callbackContext.success(macAddress);
                    break;
                }
            } else{
                callbackContext.error("can't found");
            }
        } catch (Exception e) {
            e.printStackTrace();
            callbackContext.error("something went wrong");
        }
    }


    private boolean openBluethoothConnection(String macAddress){
        boolean status=false;
        zebraPrinterConnection = null;
        zebra=null;
        zebraPrinterConnection = new BluetoothPrinterConnection(macAddress);
        try {
            zebraPrinterConnection.open();
            if (zebraPrinterConnection.isConnected()) {
                try {
                    zebra = ZebraPrinterFactory.getInstance(zebraPrinterConnection);
                    status=true;
                } catch (ZebraPrinterConnectionException e) {
                    zebra = null;
                    zebraPrinterConnection = null;
                    status=true;
                } catch (ZebraPrinterLanguageUnknownException e) {
                    zebra = null;
                    zebraPrinterConnection = null;
                } catch (Exception e) {
                    e.printStackTrace();
                    status=true;
                }
            }

        }catch (ZebraPrinterConnectionException e1){
            e1.printStackTrace();
            status=false;
        }
        isCheckIfAlreadyCallConnectionOpenMethod=true;
        return  status;
    }


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
        int isCpcl=0;
        int NumOfPrint=1;
        String finalJsonStr=jsonCpclStrWithMaxHeight;
        if(finalJsonStr.contains("#@#")){
            String tempArray[]=finalJsonStr.split("#@#");
            if(tempArray.length==2) {
                finalJsonStr=tempArray[1];
                NumOfPrint = Integer.parseInt(tempArray[0]);
                if(finalJsonStr.contains("$#$")) {
                    String tempArray1[]=finalJsonStr.split("$#$");
                    if(tempArray1.length==2) {
                        try{
                            isCpcl=Integer.parseInt(tempArray1[0]);
                        }catch (Exception e){
                            e.printStackTrace();
                        }
                        finalJsonStr = tempArray1[1];
                    }
                }
            }
        }else{
            if(finalJsonStr.contains("#####")) {
                String tempArray1[]=finalJsonStr.split("#####");
                if(tempArray1.length==2) {
                    try{
                        isCpcl=Integer.parseInt(tempArray1[0]);
                    }catch (Exception e){
                        e.printStackTrace();
                    }
                    finalJsonStr = tempArray1[1];
                }
            }
        }


        if(isCpcl==1){
            try{
                sendDataCPCL(macAddress, finalJsonStr);
            }catch (Exception e){
                e.printStackTrace();
            }
        }else {

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
                    printReceiptHeight=printReceiptHeight+100;
                    //creating bitmap to print
                    Bitmap bmp = Bitmap.createBitmap(printReceiptWidth, printReceiptHeight, Bitmap.Config.RGB_565);
                    bmp.setDensity(DisplayMetrics.DENSITY_DEFAULT);

                    // creating canvas to write data
                    if(writeOncanvas(printReceiptWidth,printReceiptHeight,bmp,finalJsonStr)){
                        try {
                            if(!isCheckIfAlreadyCallConnectionOpenMethod) {
                                waitForThreadComplete();
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }

                        if (zebraPrinterConnection!=null && zebraPrinterConnection.isConnected()) {
                            try {
                                numberOfPrintFun(zebraPrinterConnection, bmp, printReceiptWidth, printReceiptHeight, NumOfPrint);
                            }catch (Exception e){
                                e.printStackTrace();
                            }
                        }else{
                            hideProgressBar(6); // printer not connected
                        }
                    }else{
                        //fail to parse json
                        hideProgressBar(4);
                    }

                    if(bmp!=null) {
                        bmp.recycle();
                    }
                }else{
                    // not getting correct height
                    hideProgressBar(5);
                }
            }
        }// this is last else end
        checkIfAlreadyPrinting=false;
    }


    private void numberOfPrintFun(ZebraPrinterConnection zebraPrinterConnection,Bitmap bmp,int printReceiptWidth, int printReceiptHeight,int printCount){
        try {
            for(int i=0;i<printCount;i++) {
                zebraPrinterConnection.write("! UTILITIES\r\nIN-INCHES\r\nSPEED 12\r\nSETFF 0 0\r\nPRINT\r\n".getBytes());
                zebra.getGraphicsUtil().printImage(bmp, 80, 0, 650, printReceiptHeight, false);
            }
            waitForThreadCompleteWhenPrinterReady();
        }catch (Exception e){
            hideProgressBar(4);
            e.printStackTrace();
        }
    }


    private void waitForThreadComplete(){
        try {
            if(!isCheckIfAlreadyCallConnectionOpenMethod) {
                Thread.sleep(500);
                waitForThreadComplete();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void  waitForThreadCompleteWhenPrinterReady(){
        try {
            PrinterStatus printerStatus = zebra.getCurrentStatus();
            if (printerStatus.isReadyToPrint || printerStatus.isRibbonOut) {
                disconnectPrinter();
                hideProgressBar(1);
            }else if(printerStatus.isPaperOut){
                disconnectPrinter();
                hideProgressBar(1);
            }else{
                Thread.sleep(500);
                waitForThreadCompleteWhenPrinterReady();
            }
        } catch (Exception e) {
            disconnectPrinter();
            hideProgressBar(1);
            e.printStackTrace();
        }
    }

    public void disconnectPrinter() {
        try {
            if (zebraPrinterConnection != null) {
                zebraPrinterConnection.close();
                /*BluetoothAdapter mBluetoothAdapter = null;
                mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
                mBluetoothAdapter.disable();
                try {
                    Thread.sleep(2000);
                    mBluetoothAdapter.enable();
                } catch (Exception e) {
                    e.printStackTrace();
                }*/
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
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

        TextPaint textPaintNormal = makeBoldOrNonBoldTextPaint(1);

        try{
            JSONObject prntStrObjForArray= new JSONObject(jsonArrayStr);
            JSONArray printStrJsonArray = prntStrObjForArray.getJSONArray("printStr");
            for(int i=0;i< printStrJsonArray.length();i++) {
                JSONObject jsonObject = printStrJsonArray.getJSONObject(i);
                String val="";
                if(jsonObject.has("strVal")){
                    val=jsonObject.getString("strVal");;
                }
                int xVal=Integer.parseInt(jsonObject.getString("xIndex"));
                canvas.drawText(val, xVal, (Integer.parseInt(jsonObject.getString("yIndex"))+10), textPaintNormal);
            }// for lop end

            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            bmp.compress(Bitmap.CompressFormat.PNG, 0, stream);
            status=true;
            stream.flush();
            stream.close();

        }catch (Exception e){
            e.printStackTrace();
            status=false;
        }

        return status;
    }

    private  TextPaint makeBoldOrNonBoldTextPaint(int type){
        TextPaint textPaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
        textPaint.setAntiAlias(true);
        textPaint.setFilterBitmap(true);
        textPaint.setDither(true);
        textPaint.setStyle(Paint.Style.FILL);
        textPaint.setColor(Color.parseColor("#000000"));
        //textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        textPaint.setTextSize(24);
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
        // dismisstype =1:success,2:ZebraPrinterConnectionException,3:ZebraPrinterLanguageUnknownException
        //4:json parsing error,5:json parsing in height,6:printer connection error
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

    void sendDataCPCL(final String mac, final String msg) throws IOException {
        /*try {
            if(!isCheckIfAlreadyCallConnectionOpenMethod) {
                waitForThreadComplete();
            }
            if (zebraPrinterConnection!=null && zebraPrinterConnection.isConnected()) {
                zebraPrinterConnection.write(msg.getBytes());
                Thread.sleep(500);
                zebraPrinterConnection.close();
                callbackContextTemp.success("Done");
            } else {
                callbackContextTemp.error("Printer is not ready");
            }
        } catch (Exception e) {
            callbackContextTemp.error(e.getMessage());
        }*/
        Thread printNewThread= new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if(zebraPrinterConnection==null || !zebraPrinterConnection.isConnected()) {
                        zebraPrinterConnection = null;
                        zebraPrinterConnection = new BluetoothPrinterConnection(mac);
                        zebraPrinterConnection.open();
                    }

                    if(zebraPrinterConnection.isConnected()){
                        zebra = ZebraPrinterFactory.getInstance(zebraPrinterConnection);
                        zebraPrinterConnection.write(msg.getBytes());
                        Thread.sleep(500);
                        disconnectPrinter();
                        hideProgressBar(1);
                    }else{
                        //disconnectPrinter();
                        hideProgressBar(5);
                    }
                }catch (com.zebra.android.printer.ZebraPrinterLanguageUnknownException e) {
                    e.printStackTrace();
                    disconnectPrinter();
                    hideProgressBar(3);
                }catch (ZebraPrinterConnectionException e) {
                    e.printStackTrace();
                    disconnectPrinter();
                    hideProgressBar(2);
                }catch (Exception e) {
                    e.printStackTrace();
                    disconnectPrinter();
                    hideProgressBar(4);
                }
            }
        });

        printNewThread.start();
    }

    private Boolean isPrinterReady(ZebraPrinterConnection connection) throws ZebraPrinterConnectionException, ZebraPrinterLanguageUnknownException {
        Boolean isOK = false;
        connection.open();
        ZebraPrinter printer = ZebraPrinterFactory.getInstance(connection);
        PrinterStatus printerStatus = printer.getCurrentStatus();
        if (printerStatus.isReadyToPrint) {
            isOK = true;
        } else if (printerStatus.isPaused) {
            throw new ZebraPrinterConnectionException("Cannot print because the printer is paused");
        } else if (printerStatus.isHeadOpen) {
            throw new ZebraPrinterConnectionException("Cannot print because the printer media door is open");
        } else if (printerStatus.isPaperOut) {
            throw new ZebraPrinterConnectionException("Cannot print because the paper is out");
        } else {
            throw new ZebraPrinterConnectionException("Cannot print");
        }
        return isOK;
    }
    /* END */
}

