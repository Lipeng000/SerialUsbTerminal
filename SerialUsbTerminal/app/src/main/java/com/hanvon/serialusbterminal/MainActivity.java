package com.hanvon.serialusbterminal;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.PixelFormat;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbManager;
import android.hardware.usb.UsbRequest;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.security.Permissions;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 不依赖第3方库，阻塞读取15个字节
 */
public class MainActivity extends AppCompatActivity implements View.OnClickListener, UsbMonitor.CB {
    private final String TAG = "UsbMonitor";


    private UsbMonitor usbMonitor = new UsbMonitor();

    private UsbCdc serialDevice;
    private Button sendBtn;
    private TextView logTv;
    private TextView tv_logPath;
    private Button cleanBtn;
    private UsbDeviceConnection connection;
    private UsbEndpoint inEndpoint;
    private UsbEndpoint outEndpoint;
    private File logFile;
    private String[] permission = {Manifest.permission.READ_EXTERNAL_STORAGE,Manifest.permission.WRITE_EXTERNAL_STORAGE};
    private View mInflateView;
    private WindowManager windowManager;
    byte[] writebyte ={07, 00};
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        requestPermission();

        logTv = findViewById(R.id.log_tv);
        sendBtn = findViewById(R.id.send_btn);
        tv_logPath = findViewById(R.id.tv_logpath);
        cleanBtn = findViewById(R.id.clean_btn);
        sendBtn.setEnabled(false);




        usbMonitor.register(this);
        usbMonitor.setCallback(this);
    }
    @SuppressLint("HandlerLeak")
    private Handler mHandler = new Handler(){

        @Override
        public void handleMessage(@NonNull Message msg) {
            switch (msg.what) {
                case 100:
                    windowManager.removeView(mInflateView);
                    break;
            }
            super.handleMessage(msg);
        }
    };

    private void requestPermission(){
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.M){
            if (ContextCompat.checkSelfPermission(this,permission[0]) != PackageManager.PERMISSION_GRANTED){
                ActivityCompat.requestPermissions(this,permission,3);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 3){
            if(grantResults.length > 0){
                for (int i=0;i < grantResults.length;i++){
                    if (grantResults[i] != PackageManager.PERMISSION_GRANTED)
                        Toast.makeText(this,"权限申请失败，不能存储log文件",Toast.LENGTH_SHORT).show();
                }
            }

        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        usbMonitor.unregister();
    }


    //获取asc对应的字符
    public String asciiToString(String value) {
        StringBuffer sbu = new StringBuffer();
        String[] chars = value.split(",");
        for (int i = 0; i < chars.length; i++) {
            sbu.append((char) Integer.parseInt(chars[i]));
        }
        return sbu.toString();
    }

    public static String toAsciiString(byte[] output) {
        char[] chars = new char[output.length];
        for (int i = 0; i < output.length; i++) {
            chars[i] = (char) output[i];
        }
        return new String(chars);
    }

    private String log_msg = "";
    private void subMessage(UsbDeviceConnection connection,
                            UsbEndpoint inEndpoint,UsbEndpoint outEndpoit,
                            byte[] wirtebyte,byte[] readbyte ){
        showLoading();
        createLogFile();
        if (serialDevice != null) {
            int writeTimeout = 0;
            int readTimeout = 3000;
            Log.d(TAG,"startWriteTime:"+ System.currentTimeMillis());
            connection.bulkTransfer(outEndpoit, wirtebyte, wirtebyte.length, writeTimeout);
            new Thread(() -> {
                //read
                boolean read = true;
                while (read) {
                    // 循环阻塞接收数据
                    UsbRequest requestIN = new UsbRequest();
                    requestIN.initialize(connection, inEndpoint);
                    int outMax = outEndpoit.getMaxPacketSize();
                    int inMax = inEndpoint.getMaxPacketSize();
                    ByteBuffer byteBuffer = ByteBuffer.allocate(inMax);
                    UsbRequest usbRequest = new UsbRequest();
                    usbRequest.initialize(connection, inEndpoint);
                    usbRequest.queue(byteBuffer, inMax);
                    if (connection.requestWait() == usbRequest) {
                        byte[] retData = byteBuffer.array();
//                        String s = new String(retData);
                        String log = toAsciiString(retData);
                        if (retData.length == 0) {
                            read = false;
                            mHandler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    if (mHandler != null)
                                        mHandler.sendEmptyMessage(100);
                                }
                            },1000);
                        }else {
                            saveLogMessage(log);
                        }

                    }
                }

//                byte[] tmp = new byte[requestIN.getEndpoint().getMaxPacketSize()];
//                try {
////                    Log.d(TAG,"startReadTime:"+ System.currentTimeMillis());
//
//                    for (int i = 0; i < readbyte.length; ) {
////                        UsbEndpoint endpoint = requestIN.getEndpoint();
//                        int readMax = Math.min(tmp.length, inEndpoint.getMaxPacketSize());
//                        int read = connection.bulkTransfer(inEndpoint, tmp, readMax, readTimeout);
//
//                        System.arraycopy(tmp, 0, readbyte, i, read);
//                        i += read;
//                        if (read == -1) {
//                            break;
//                        }
//                    }
//                    Log.d(TAG,"endReadTime:  "+ System.currentTimeMillis());
//                    runOnUiThread(() -> {
//                        String msg =  toAsciiString(readbyte);
//                        Log.e(TAG, msg);
//                        readTv.setText(msg);
//                        sendBtn.setEnabled(true);
//                    });
//                } catch (Exception e) {
//                    e.printStackTrace();
//                }finally {
//                    runOnUiThread(() -> {
//                        sendBtn.setEnabled(true);
//                    });
//                }
//
            }).start();

        } else {
            Toast.makeText(this, "Please connect USB device firstly.", Toast.LENGTH_LONG).show();
        }
    }

    private void createLogFile() {

        try {
            String logdir = Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator + "pen_log";
            File dir = new File(logdir);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");
            Date date = new Date(System.currentTimeMillis());
            String fileName = dateFormat.format(date);
            String logFilePath = logdir + "/"+"pen-" + fileName + ".txt";
            tv_logPath.setText(logFilePath);
            logFile = new File(logFilePath);
            if (!logFile.exists())
                logFile.createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private void saveLogMessage(String log_msg){
        try {
            Scanner scanner = new Scanner(logFile);
            StringBuilder sb = new StringBuilder();
            while (scanner.hasNext()){
                sb.append(scanner.nextLine());
                sb.append("\r\n");
            }
            scanner.close();

            PrintWriter pw = new PrintWriter(new FileWriter(logFile,true), true);
            pw.println(sb.toString());
            pw.println(log_msg);
            pw.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private void showLoading(){
        windowManager = getWindowManager();
        WindowManager.LayoutParams mParams = new WindowManager.LayoutParams();
        mParams.height = WindowManager.LayoutParams.WRAP_CONTENT;
        mParams.width = WindowManager.LayoutParams.WRAP_CONTENT;
        mParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        mParams.format = PixelFormat.TRANSLUCENT;
        mInflateView = getLayoutInflater().inflate(R.layout.progress_layout, null);
        windowManager.addView(mInflateView,mParams);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()){
            case R.id.clean_btn:
//                createLogFile();
                break;
            case R.id.send_btn:
                if (serialDevice != null){
                    connection = serialDevice.getConnection();
                    inEndpoint = serialDevice.getInEndpoint();
                    outEndpoint = serialDevice.getOutEndpoint();

                    byte[] readbyte = new byte[115200];

                    subMessage(connection, inEndpoint, outEndpoint,writebyte,readbyte);
                }
                break;
        }


    }

    @Override
    public void onDeviceAttached(UsbMonitor monitor, UsbManager usbManager, UsbDevice device) {
        monitor.requestOpenDevice(device);
        Log.e(TAG, "Device attached: " + System.currentTimeMillis());
        Toast.makeText(this, "Device attached.", Toast.LENGTH_LONG).show();
    }

    @Override
    public void onDeviceDetached(UsbMonitor monitor, UsbManager usbManager, UsbDevice device) {
        if (serialDevice != null) {
            serialDevice.close();
            serialDevice = null;
        }
        Toast.makeText(this, "Device detached.", Toast.LENGTH_LONG).show();
        sendBtn.setEnabled(false);
        if (mHandler != null)
            mHandler.sendEmptyMessage(100);
    }

    @Override
    public void onDeviceCanOpen(UsbMonitor monitor, UsbManager usbManager, UsbDevice device) {

        UsbDeviceConnection connection = usbManager.openDevice(device);

        //serialDevice = UsbSerialDevice.createUsbSerialDevice(evtData.device, evtData.connection);
        new Thread(() -> {
            runOnUiThread(() -> logTv.setText("创建设备对象"));
            serialDevice = new UsbCdc();
            runOnUiThread(() -> logTv.setText("准备打开设备"));
            if (serialDevice.openCDC(device, connection, -1)) {
                runOnUiThread(() -> logTv.setText("设置波特率"));
                serialDevice.setBaudRate(115200);
                runOnUiThread(() -> {
                    logTv.setText("设备已打开");
                    Log.e(TAG, "Device Open: " + System.currentTimeMillis());
                    Toast.makeText(this, "Device opened.", Toast.LENGTH_LONG).show();
                    sendBtn.setEnabled(true);
                });
            }
        }).start();
    }

    private String printHex(byte[] dat) {
        if (dat == null) {
            return "";
        }
        return printHex(dat, 0, dat.length);
    }

    private String printHex(byte[] dat, int startPos, int size) {
        if (dat == null) {
            return "";
        }
        int endPos = startPos + size;
        if (endPos > dat.length) {
            endPos = dat.length;
        }
        StringBuilder sb = new StringBuilder((endPos - startPos) * 2);
        for (int i = startPos; i < endPos; i++) {
            sb.append(String.format("%02X", (int) dat[i] & 0xFF));
        }
        return sb.toString();
    }

}
