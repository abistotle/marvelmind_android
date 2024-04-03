package com.example.marvelmind_track;

import android.support.v7.app.AppCompatActivity;
import android.content.Context;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.app.PendingIntent;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Message;
import android.os.Handler;
import android.os.Bundle;

import java.io.IOException;
import java.util.HashMap;
import java.util.concurrent.Semaphore;

public class MarvelmindUsb {
    private UsbManager mUsbManager;
    private UsbEndpoint mRcvEndpoint;
    private UsbDeviceConnection usbConnection;
    private Handler msgHandler;
    private boolean isConnected;
    private static String ACTION_USB_PERMISSION = "com.example.marvelmind_track.USB_PERMISSION";
    private AppCompatActivity ownerActivity;
    private PendingIntent permissionIntent;
    private Semaphore semUsbPermitted = new Semaphore(1);
    private int msgCount;

    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbDevice device = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if (device != null) {
                            //permission granted; continue program
                            semUsbPermitted.release();
                        }
                    } else {
                        // user didn't grant permission
                        //finish();
                    }
                }
            }
        }
    };

    UsbDevice findDevice() {
        for (UsbDevice usbDevice: mUsbManager.getDeviceList().values()) {
            return usbDevice;
//            if (usbDevice.getDeviceClass() == UsbConstants.USB_CLASS_PRINTER) {
//                return usbDevice;
//            } else {
//                UsbInterface usbInterface = findInterface(usbDevice);
//                if (usbInterface != null) return usbDevice;
//            }
        }
        return null;
    }

    UsbInterface findInterface(UsbDevice usbDevice) {
        UsbInterface usbInterface= null;
        //sendMessage2main(200+usbDevice.getInterfaceCount());
        for (int nIf = 0; nIf < usbDevice.getInterfaceCount(); nIf++) {
            usbInterface = usbDevice.getInterface(nIf);
            //return usbInterface;
//            if (usbInterface.getInterfaceClass() == UsbConstants.USB_CLASS_PRINTER) {
//                return usbInterface;
//            }
        }
        return usbInterface;
    }

    private void sendMessage2main(int id, byte[] data, int dsize) {
        Message msg2MainWin = Message.obtain();
        msg2MainWin.what= id;
        msg2MainWin.arg1= msgCount++;
        msg2MainWin.arg2= dsize;

        if (data != null) {
            Bundle bundle = new Bundle();
            bundle.putByteArray("stream", data);
            msg2MainWin.setData(bundle);
        }

        msgHandler.sendMessage(msg2MainWin);
    }

    private void setConnected() {
        if (!isConnected) {
            sendMessage2main(MainActivity.MM_MSG_USB_CONNECTED, null, 0);
        }
        isConnected= true;
    }

    private void setNotConnected() {
        if (isConnected) {
            sendMessage2main(MainActivity.MM_MSG_USB_DISCONNECTED, null, 0);
        }
        isConnected= false;
    }

    private void readStream() {
        byte[] data= new byte[256];

        while(true) {
            int dsize = usbConnection.bulkTransfer(mRcvEndpoint, data, 64, 200);

            if (dsize<0)
                break;

            if (dsize == 0)
                continue;

            //sendMessage2main(200+dsize);
            // sendMessage2main(200+mRcvEndpoint.getMaxPacketSize());
            sendMessage2main(MainActivity.MM_MSG_STREAM_DATA,data,dsize);
        }
    }

    private void scanPorts() throws IOException {
        while (true) {
            UsbDevice device = findDevice();
            if (device == null) {
                setNotConnected();
                continue;
            }

            UsbInterface mUsbInterface= findInterface(device);
            if (mUsbInterface == null)
                continue;

            //setConnected();

            mRcvEndpoint= null;
            for (int nEp = 0; nEp < mUsbInterface.getEndpointCount(); nEp++) {
                UsbEndpoint tmpEndpoint = mUsbInterface.getEndpoint(nEp);
                if (tmpEndpoint.getType() != UsbConstants.USB_ENDPOINT_XFER_BULK) continue;

                if (tmpEndpoint.getDirection() == UsbConstants.USB_DIR_IN) {
                    mRcvEndpoint = tmpEndpoint;
                    break;
                }
            }
            if (mRcvEndpoint == null)
                continue;

            if (!isConnected) {
                mUsbManager.requestPermission(device, permissionIntent);
                semUsbPermitted.acquireUninterruptibly();
            }

//            if (mUsbManager.hasPermission(device)) {
//                setConnected();
//            }

            if (!isConnected) {
                usbConnection = mUsbManager.openDevice(device);
                if (usbConnection == null)
                    throw new IOException("Can't open USB connection");
                usbConnection.claimInterface(mUsbInterface, true);
            }

            if (usbConnection != null) {
                setConnected();

                readStream();

                //setNotConnected();
            }
        }
    }

    public MarvelmindUsb(Object usbService, Handler msgHandler_, AppCompatActivity ownerActivity_) {
        mUsbManager = (UsbManager) usbService;
        msgHandler= msgHandler_;
        ownerActivity= ownerActivity_;
        isConnected= false;
        msgCount= 0;

        permissionIntent = PendingIntent.getBroadcast(ownerActivity, 0, new Intent(
                    ACTION_USB_PERMISSION), 0);

        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        ownerActivity.registerReceiver(mUsbReceiver, filter);

        Thread handle = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    scanPorts();
                } catch (IOException e) {
                }
            }
        });
        handle.start();
    }
}
