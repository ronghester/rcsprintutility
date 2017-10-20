package android.com.github.michael79bxl.zbtprinter;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;

import android.app.Activity;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;


import java.io.InputStream;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Environment;
import android.text.TextPaint;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.util.Log;

import com.zebra.android.comm.BluetoothPrinterConnection;
import com.zebra.android.comm.ZebraPrinterConnection;
import com.zebra.android.comm.ZebraPrinterConnectionException;
import com.zebra.android.printer.PrinterStatus;
import com.zebra.android.printer.ZebraPrinter;
import com.zebra.android.printer.ZebraPrinterFactory;
import com.zebra.android.printer.ZebraPrinterLanguageUnknownException;

import com.honeywell.mobility.print.LinePrinter;
import com.honeywell.mobility.print.LinePrinterException;
import com.honeywell.mobility.print.PrintProgressEvent;
import com.honeywell.mobility.print.PrintProgressListener;

public class LineBluetoothPrinter extends CordovaPlugin {

    private static final String LOG_TAG = "LineBluetoothPrinter";

    /* Variable declaration for bitmpa print */
    String base64Png = null;
    private String jsonCmdAttribStr = null;

    LinePrinter lp = null;

    ProgressDialog progressPrintDialogue;
    Activity mActivity=null;
    CallbackContext callbackContextTemp=null;
    private boolean checkIfAlreadyPrinting=false;
    private boolean isCheckIfAlreadyCallConnectionOpenMethod=false;

    public LineBluetoothPrinter() {

    }

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        mActivity=this.cordova.getActivity();
        if (action.equals("lpPrint")) {
            try {
                String mac = args.getString(0);
                String msg = args.getString(1);
                readAssetFiles(callbackContext);
                String sPrinterURI = "bt://" + mac;
                sendData(callbackContext, sPrinterURI, msg);
            } catch (Exception e) {
                Log.e(LOG_TAG, e.getMessage());
                e.printStackTrace();
            }
            return true;
        } if (action.equals("lpLRUPrint")) {
            try {
                String mac = args.getString(0);
                String msg = args.getString(1);
                readAssetFiles(callbackContext);
                String sPrinterURI = "bt://" + mac;
                sendDataLRU(callbackContext, sPrinterURI, msg);
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

    private void readAssetFiles(final CallbackContext callbackContext)
    {
        InputStream input = null;
        ByteArrayOutputStream output = null;
        AssetManager assetManager = mActivity.getAssets();
        String fileName =  "printer_profiles.JSON";
        int initialBufferSize;

        try
        {
            input = assetManager.open(fileName);
            initialBufferSize = 8000;
            output = new ByteArrayOutputStream(initialBufferSize);

            byte[] buf = new byte[1024];
            int len;
            while ((len = input.read(buf)) > 0)
            {
                output.write(buf, 0, len);
            }
            input.close();
            input = null;

            output.flush();
            output.close();
            jsonCmdAttribStr = output.toString();
            output = null;
        }
        catch (Exception ex)
        {
            ex.printStackTrace();
            callbackContext.error("something went wrong");
        }
        finally
        {
            try
            {
                if (input != null)
                {
                    input.close();
                    input = null;
                }

                if (output != null)
                {
                    output.close();
                    output = null;
                }
            }
            catch (IOException e){
                e.printStackTrace();
                callbackContext.error("something went wrong");
            }
        }
    }

    public void findPrinter(final CallbackContext callbackContext) {
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

    void sendDataLRU(final CallbackContext callbackContext, final String mac, final String msg) throws IOException {
        callbackContextTemp=callbackContext;
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                connectToPrinterLRU(msg, mac);
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
                printReceiptHeight=printReceiptHeight+100;
                //creating bitmap to print
                Bitmap bmp = Bitmap.createBitmap(printReceiptWidth, printReceiptHeight, Bitmap.Config.RGB_565);
                bmp.setDensity(DisplayMetrics.DENSITY_DEFAULT);

                // creating canvas to write data
                if(writeOncanvas(printReceiptWidth,printReceiptHeight,bmp,finalJsonStr)){

                    LinePrinter.ExtraSettings exSettings = new LinePrinter.ExtraSettings();
                    exSettings.setContext(this.mActivity.getApplicationContext());

                    try
                    {
                        lp = new LinePrinter(
                                jsonCmdAttribStr,
                                "PB51",
                                macAddress,
                                exSettings);

                        //A retry sequence in case the bluetooth socket is temporarily not ready
                        int numtries = 0;
                        int maxretry = 2;
                        while(numtries < maxretry)
                        {
                            try{
                                lp.connect();  // Connects to the printer
                                break;
                            }
                            catch(LinePrinterException ex){
                                numtries++;
                                Thread.sleep(1000);
                            }
                        }
                        if (numtries == maxretry) lp.connect();//Final retry

                        lp.writeGraphicBase64(base64Png,
                                LinePrinter.GraphicRotationDegrees.DEGREE_0,
                                10,  // Offset in printhead dots from the left of the page
                                810, // Desired graphic width on paper in printhead dots
                                printReceiptHeight); // Desired graphic height on paper in printhead dots

                    }
                    catch (LinePrinterException ex)
                    {
                        hideProgressBar(4);
                    }
                    catch (Exception ex)
                    {
                        hideProgressBar(4);
                    }
                    finally
                    {
                        if (lp != null)
                        {
                            try
                            {
                                lp.disconnect();  // Disconnects from the printer
                                lp.close();  // Releases resources
                                hideProgressBar(1);
                            }
                            catch (Exception ex) {
                                hideProgressBar(4);
                            }
                        }
                    }
                }else{
                    hideProgressBar(4);
                }

                if(bmp!=null) {
                    bmp.recycle();
                }
            }else{
                hideProgressBar(5);
            }
        }
        checkIfAlreadyPrinting=false;
    }

    public void connectToPrinterLRU(String jsonCpclStrWithMaxHeight, String macAddress) {
        String finalJsonStr=jsonCpclStrWithMaxHeight;

        // calling on ui thread wait....for time
        if(!checkIfAlreadyPrinting){ // check if already printing
            mActivity.runOnUiThread(new Runnable() {
                public void run() {
                    checkIfAlreadyPrinting = true;
                    showProgressBar();
                }
            });


            LinePrinter.ExtraSettings exSettings = new LinePrinter.ExtraSettings();
            exSettings.setContext(this.mActivity.getApplicationContext());

            try
            {
                lp = new LinePrinter(
                        jsonCmdAttribStr,
                        "P6824",
                        macAddress,
                        exSettings);

                //A retry sequence in case the bluetooth socket is temporarily not ready
                int numtries = 0;
                int maxretry = 2;
                while(numtries < maxretry)
                {
                    try{
                        lp.connect();  // Connects to the printer
                        break;
                    }
                    catch(LinePrinterException ex){
                        numtries++;
                        Thread.sleep(1000);
                    }
                }
                if (numtries == maxretry) lp.connect();//Final retry

                int noOfLines = 0;
                int iLength = 60;

               /* byte[] str2 = new byte[] {0x1B, 0x41,(byte) 0x10};
                lp.write(str2);*/

                byte[] str = new byte[] {0x1B, 0x43, 0x0,(byte) 0x72};
                lp.write(str);

                int pageNo = 1;
                int pageFit = 40;
                String footerText = "";
                Boolean multiPageFlag = false;
                int jsonObjLength = 0;
                int pageCtr = 0;
                String pageStatus  = "";
                JSONObject prntStrObjForArray= new JSONObject(finalJsonStr);
                JSONArray printStrJsonArray = prntStrObjForArray.getJSONArray("printStr");
                    if(prntStrObjForArray.has("footerText")){
                        footerText = prntStrObjForArray.getString("footerText");;
                    }
                    if(prntStrObjForArray.has("totalLength")){
                        jsonObjLength = Integer.parseInt(prntStrObjForArray.getString("totalLength"));
                    }

                    for (int i = 0; i < printStrJsonArray.length(); i++) {
                        JSONObject jsonObject = printStrJsonArray.getJSONObject(i);
                        Boolean newLineRequired = (jsonObject.getString("newLine").equals("1") ? true : false);
                        Boolean setBoldProperty = (jsonObject.getString("isBold").equals("1") ? true : false);
                        lp.setBold(setBoldProperty);
                        if (newLineRequired) {
                            noOfLines++;
                            pageCtr++;
                            lp.newLine(1);
                            lp.write(jsonObject.getString("strVal"));
                        } else {
                            lp.write(jsonObject.getString("strVal"));
                        }

                        if ((noOfLines % pageFit) == 0) {
                            if (jsonObjLength != noOfLines) {
                                pageStatus = "   CONTINUE";
                            }
                            multiPageFlag = true;
                            lp.newLine(10);
                            lp.write("   ");
                            lp.write("                              " + footerText);
                            lp.newLine(1);
                            lp.write("                              " + pageStatus);
                            lp.newLine(1);
                            lp.write("                                 Page : " + pageNo);
                            //lp.newLine(1);
                            //lp.write("   ");
                            byte[] str3 = new byte[] {0x1B, 0x4E,(byte) 0x72};
                            lp.write(str3);
                            //lp.write("   ");
                            pageNo++;
                            lp.newLine(25);
                            lp.write("   ");
                            pageCtr = 0;
                        }
                         else {
                            if (jsonObjLength == noOfLines) {
                                pageStatus = "DERNIERE PAGE";
                                int tempCtr = pageFit - pageCtr;
                                lp.newLine(tempCtr);
                                lp.write("   ");
                                if(multiPageFlag){
                                    lp.newLine(5);
                                }
                                else{
                                    lp.newLine(10);
                                }
                                lp.write("                              " + footerText);
                                lp.newLine(1);
                                lp.write("                              " + pageStatus);
                                lp.newLine(1);
                                lp.write("                                 Page : " + pageNo);
                                //lp.newLine(1);
                                //lp.write("   ");
                                byte[] str3 = new byte[] {0x1B, 0x4E,(byte) 0x72};
                                lp.write(str3);
                                //lp.write("   ");
                                lp.newLine(20);
                                lp.write("   ");
                            }
                        }
                    }
            }
            catch (LinePrinterException ex)
            {
                hideProgressBar(4);
            }
            catch (Exception ex)
            {
                hideProgressBar(4);
            }
            finally
            {
                if (lp != null)
                {
                    try
                    {
                        lp.disconnect();  // Disconnects from the printer
                        lp.close();  // Releases resources
                        hideProgressBar(1);
                    }
                    catch (Exception ex) {
                        hideProgressBar(4);
                    }
                }
            }
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
                    else if(isArabic.equals("2")){
                        textPaintNormal = arabicLeftJustified(1);
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
        textPaint.setTextSize(32);
        return textPaint;
    }

    private  TextPaint arabicLeftJustified(int type){
        TextPaint textPaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
        textPaint.setAntiAlias(true);
        textPaint.setFilterBitmap(true);
        textPaint.setDither(true);
        textPaint.setStyle(Paint.Style.FILL);
        textPaint.setColor(Color.parseColor("#000000"));
        textPaint.setTextAlign(Paint.Align.LEFT);
        Typeface bold = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);
        textPaint.setTypeface(bold);
        textPaint.setTextSize(32);
        return textPaint;
    }

    private  TextPaint makeBoldOrNonBoldTextPaint(int type){
        TextPaint textPaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
        textPaint.setAntiAlias(true);
        textPaint.setFilterBitmap(true);
        textPaint.setDither(true);
        textPaint.setStyle(Paint.Style.FILL);
        textPaint.setColor(Color.parseColor("#000000"));
        Typeface bold = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD);
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

