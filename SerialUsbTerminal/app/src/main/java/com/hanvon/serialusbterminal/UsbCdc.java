package com.hanvon.serialusbterminal;

import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbRequest;
import android.util.Log;

public class UsbCdc {
    private static final String CLASS_ID = MainActivity.class.getSimpleName();
    protected static final int USB_TIMEOUT = 0;

    private static final int CDC_REQTYPE_HOST2DEVICE = 0x21;
    private static final int CDC_REQTYPE_DEVICE2HOST = 0xA1;

    private static final int CDC_SET_LINE_CODING = 0x20;
    private static final int CDC_GET_LINE_CODING = 0x21;
    private static final int CDC_SET_CONTROL_LINE_STATE = 0x22;

    private static final int CDC_SET_CONTROL_LINE_STATE_RTS = 0x2;
    private static final int CDC_SET_CONTROL_LINE_STATE_DTR = 0x1;


    /***
     *  Default Serial Configuration
     *  Baud rate: 115200 = 0x01C200
     *  Data bits: 8
     *  Stop bits: 1
     *  Parity: None
     *  Flow Control: Off
     */
    private static final byte[] CDC_DEFAULT_LINE_CODING = new byte[]{
            (byte) 0x00, // Offset 0:4 dwDTERate
            (byte) 0xC2,
            (byte) 0x01,
            (byte) 0x00,
            (byte) 0x00, // Offset 5 bCharFormat (1 Stop bit)
            (byte) 0x00, // bParityType (None)
            (byte) 0x08  // bDataBits (8)
    };

    private static final int CDC_CONTROL_LINE_ON = 0x0003;
    private static final int CDC_CONTROL_LINE_OFF = 0x0000;

    private UsbInterface mInterface;
    private UsbEndpoint inEndpoint;
    private UsbEndpoint outEndpoint;

    private int initialBaudRate = 0;

    private int controlLineState = CDC_CONTROL_LINE_ON;
    private UsbDevice device;
    private UsbDeviceConnection connection;

    public UsbDeviceConnection getConnection() {
        return connection;
    }

    public UsbEndpoint getInEndpoint() {
        return inEndpoint;
    }

    public UsbEndpoint getOutEndpoint() {
        return outEndpoint;
    }

    public int bulkTransfer(byte[] data) {
        if (connection == null || data == null) {
            return 0;
        }

        if (data.length > 0)
            return connection.bulkTransfer(outEndpoint, data, data.length, USB_TIMEOUT);
        return 0;
    }

    public UsbRequest getRequestIN() {
        //UsbRequest requestIN = new SafeUsbRequest();
        UsbRequest requestIN = new UsbRequest();
        requestIN.initialize(connection, inEndpoint);
        return requestIN;
    }

    public void setBaudRate(int baudRate) {
        byte[] data = getLineCoding();

        data[0] = (byte) (baudRate & 0xff);
        data[1] = (byte) (baudRate >> 8 & 0xff);
        data[2] = (byte) (baudRate >> 16 & 0xff);
        data[3] = (byte) (baudRate >> 24 & 0xff);

        setControlCommand(connection, CDC_SET_LINE_CODING, 0, data);
    }

    public boolean openCDC(UsbDevice device, UsbDeviceConnection connection, int iface) {
        if (device == null || connection == null)
            return false;
        Log.i(CLASS_ID, "findFirstCDC()");
        mInterface = device.getInterface(iface >= 0 ? iface : findFirstCDC(device));

        Log.i(CLASS_ID, "claimInterface()");
        if (connection.claimInterface(mInterface, true)) {
            Log.i(CLASS_ID, "Interface succesfully claimed");
        } else {
            Log.i(CLASS_ID, "Interface could not be claimed");
            return false;
        }

        // Assign endpoints
        int numberEndpoints = mInterface.getEndpointCount();
        Log.i(CLASS_ID, "getEndpointCount(): " + numberEndpoints);
        for (int i = 0; i <= numberEndpoints - 1; i++) {
            UsbEndpoint endpoint = mInterface.getEndpoint(i);
            if (endpoint.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK
                    && endpoint.getDirection() == UsbConstants.USB_DIR_IN) {
                inEndpoint = endpoint;
            } else if (endpoint.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK
                    && endpoint.getDirection() == UsbConstants.USB_DIR_OUT) {
                outEndpoint = endpoint;
            }
        }

        if (outEndpoint == null || inEndpoint == null) {
            Log.i(CLASS_ID, "Interface does not have an IN or OUT interface");
            return false;
        }

        // Default Setup
        Log.i(CLASS_ID, "CDC_SET_LINE_CODING");
        setControlCommand(connection, CDC_SET_LINE_CODING, 0, getInitialLineCoding());
        Log.i(CLASS_ID, "CDC_SET_CONTROL_LINE_STATE");
        setControlCommand(connection, CDC_SET_CONTROL_LINE_STATE, CDC_CONTROL_LINE_ON, null);

        this.device = device;
        this.connection = connection;
        return true;
    }

    public void close() {
        setControlCommand(connection, CDC_SET_CONTROL_LINE_STATE, CDC_CONTROL_LINE_OFF, null);
        connection.releaseInterface(mInterface);
        connection.close();
    }

    @SuppressWarnings("UnusedReturnValue")
    private int setControlCommand(UsbDeviceConnection connection, int request, int value, byte[] data) {
        int dataLength = 0;
        if (data != null) {
            dataLength = data.length;
        }
        int response = connection.controlTransfer(CDC_REQTYPE_HOST2DEVICE, request, value, 0, data, dataLength, USB_TIMEOUT);
        Log.i(CLASS_ID, "Control Transfer Response: " + String.valueOf(response));
        return response;
    }

    private byte[] getInitialLineCoding() {
        byte[] lineCoding;

//        int initialBaudRate = -1;
//
//        if(initialBaudRate > 0) {
//            lineCoding = CDC_DEFAULT_LINE_CODING.clone();
//            for (int i = 0; i < 4; i++) {
//                lineCoding[i] = (byte) (initialBaudRate >> i*8 & 0xFF);
//            }
//        } else {
//            lineCoding = CDC_DEFAULT_LINE_CODING;
//        }
//
//        return lineCoding;
        return CDC_DEFAULT_LINE_CODING;
    }

    private static int findFirstCDC(UsbDevice device) {
        int interfaceCount = device.getInterfaceCount();

        for (int iIndex = 0; iIndex < interfaceCount; ++iIndex) {
            if (device.getInterface(iIndex).getInterfaceClass() == UsbConstants.USB_CLASS_CDC_DATA) {
                return iIndex;
            }
        }

        Log.i(CLASS_ID, "There is no CDC class interface");
        return -1;
    }

    private byte[] getLineCoding() {
        byte[] data = new byte[7];
        int response = connection.controlTransfer(CDC_REQTYPE_DEVICE2HOST, CDC_GET_LINE_CODING, 0, 0, data, data.length, USB_TIMEOUT);
        Log.i(CLASS_ID, "Control Transfer Response: " + String.valueOf(response));
        return data;
    }
}
