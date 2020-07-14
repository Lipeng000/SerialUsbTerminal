package com.hanvon.serialusbterminal;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.util.Log;

public class UsbMonitor extends BroadcastReceiver {
    private static final String TAG = "UsbMonitor";

    private static final int PERMISSION_REQUEST_CODE = 352;
    private static final String PERMISSION_REQUEST_ACTION = "com.goodix.ble.gr.toolbox.app.mmi.test.device.UsbDeviceMonitor";

    private UsbManager mUsbManager;
    private Context mCtx;
    private CB callback;

    public interface CB {
        void onDeviceAttached(UsbMonitor monitor, UsbManager usbManager, UsbDevice device);

        void onDeviceDetached(UsbMonitor monitor, UsbManager usbManager, UsbDevice device);

        void onDeviceCanOpen(UsbMonitor monitor, UsbManager usbManager, UsbDevice device);
    }

    public void setCallback(CB callback) {
        this.callback = callback;
    }

    public void register(Context ctx) {
        if (ctx != null) {
            mCtx = ctx;
            final IntentFilter filter = new IntentFilter();
            filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
            filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
            filter.addAction(PERMISSION_REQUEST_ACTION);
            ctx.registerReceiver(this, filter);

            mUsbManager = (UsbManager) mCtx.getSystemService(Context.USB_SERVICE);
        }
    }

    public void unregister() {
        if (mCtx != null) {
            mCtx.unregisterReceiver(this);
            mCtx = null;
            mUsbManager = null;
        }
    }

    public void requestOpenDevice(UsbDevice device) {
        if (mUsbManager != null) {
            if (mUsbManager.hasPermission(device)) {
                if (callback != null) {
                    callback.onDeviceCanOpen(this, mUsbManager, device);
                }
            } else {
                mUsbManager.requestPermission(device, PendingIntent.getBroadcast(mCtx, PERMISSION_REQUEST_CODE, new Intent(PERMISSION_REQUEST_ACTION), 0));
            }
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.e(TAG, "onReceive: " + intent + "-----time: "+ System.currentTimeMillis());
        if (intent.getExtras() != null && !intent.getExtras().isEmpty()) {
            UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
            if (device != null) {
                Log.e(TAG, "EXTRA_DEVICE = " + device.getDeviceName() + ", VID = 0x" + Integer.toHexString(device.getVendorId()) + ", PID = 0x" + Integer.toHexString(device.getProductId()));
                if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(intent.getAction())) {
                    if (callback != null) {
                        callback.onDeviceAttached(this, mUsbManager, device);
                    }
                } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(intent.getAction())) {
                    if (callback != null) {
                        callback.onDeviceDetached(this, mUsbManager, device);
                    }
                } else if (PERMISSION_REQUEST_ACTION.equals(intent.getAction())) {
                    boolean granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false);
                    if (granted) {
                        if (callback != null) {
                            callback.onDeviceCanOpen(this, mUsbManager, device);
                        }
                    }
                }
            }
        }

    }
}
